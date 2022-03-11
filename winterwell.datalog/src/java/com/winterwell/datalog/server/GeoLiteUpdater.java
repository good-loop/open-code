package com.winterwell.datalog.server;

import static org.junit.Assert.assertArrayEquals;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.attribute.BasicFileAttributes;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.lang3.time.DateUtils;

import com.winterwell.utils.Proc;
import com.winterwell.utils.io.ConfigFactory;
import com.winterwell.utils.io.FileUtils;
import com.winterwell.web.FakeBrowser;
import com.winterwell.web.WebEx;


/**
 * Updater for our GeoLite2 IP-geolocation CSVs.
 * Contacts MaxMind servers, checks timestamp on latest available bundle file,
 * and if it's newer than the current one downloads it, extracts relevant CSVs,
 * and instructs GeoLiteLocator to refresh.
 * TODO Restructure this to call from inside DataLogServer on a timer
 * @author roscoe
 *
 */
public class GeoLiteUpdater {
	
	static String licenseKey = "xxxxxxxx";
	
	static final String CSV_URL = "https://download.maxmind.com/app/geoip_download?edition_id=GeoLite2-Country-CSV&license_key=$LICENSE_KEY&suffix=zip";
	static final String CHECKSUM_URL = "https://download.maxmind.com/app/geoip_download?edition_id=GeoLite2-Country-CSV&license_key=$LICENSE_KEY&suffix=zip.sha256";
	
	static String getCsvCmd = "wget \"" + CSV_URL + "\" -O ./geoip/$FILENAME";
	static String getChecksumCmd = "curl \"" + CHECKSUM_URL + "\"";
	
	static final String VERSION_FILE_NAME = "current_version_date.txt";
	static final String ZIP_NAME = "geolite2_csv.zip";
	
	// For extracting the path to the block and location files from the downloaded zip's directory listing
	static final Pattern BLOCKS_FILENAME_PATTERN = Pattern.compile("^.+" + GeoLiteLocator.BLOCKS_CSV_NAME + "$", Pattern.MULTILINE);
	static final Pattern LOCNS_FILENAME_PATTERN = Pattern.compile("^.+" + GeoLiteLocator.LOCATIONS_CSV_NAME + "$", Pattern.MULTILINE);
	
	static final Pattern HASH_PATTERN = Pattern.compile("[\\da-f]+");
	 
	
	public static void main(String[] args) throws ParseException, IOException {
		String csvUrl = CSV_URL.replace("$LICENSE_KEY", licenseKey);
		
		// Check the new CSV bundle's Last-Modified header...
		HttpURLConnection conn = (HttpURLConnection) (new URL(csvUrl).openConnection());
		conn.setRequestMethod("HEAD");
		conn.connect();
		Long newVersionTimestamp = conn.getHeaderFieldDate("Last-Modified", 0);
		conn.disconnect();

		// ...and compare it to the timestamp of the version we currently have, if any.
		File zipFile = new File(GeoLiteLocator.GEOIP_FILES_PATH, ZIP_NAME);
		if (zipFile.exists()) {
			if (zipFile.lastModified() >= newVersionTimestamp) return;
		}
		
		// Time to update the CSVs! Let's download the new zip file.
		FakeBrowser fb = new FakeBrowser();
		File tmpFile = fb.getFile(csvUrl);
		
		// Compare reference SHA256 to that of downloaded file
		String referenceHash = fb.getPage(CHECKSUM_URL.replace("$LICENSE_KEY", licenseKey));
		String downloadedHash = Proc.run("sha256sum " + tmpFile.getCanonicalPath());
		Matcher refHashMatcher = HASH_PATTERN.matcher(referenceHash);
		refHashMatcher.find();
		if (!downloadedHash.contains(refHashMatcher.group())) {
			// TODO Hash mismatch! Try again?
		}
		
		// Overwrite the previously saved CSV bundle zip
		FileUtils.move(tmpFile, zipFile);
		
		String zipPath = zipFile.getCanonicalPath();
		
		// Print the file paths contained in the new zip and find the paths to the block and location files
		String zipContents = Proc.run("unzip -Z -1 " + zipPath);
		Matcher findBlocksPath = BLOCKS_FILENAME_PATTERN.matcher(zipContents);
		Matcher findLocnsPath = LOCNS_FILENAME_PATTERN.matcher(zipContents);
		findBlocksPath.find();
		findLocnsPath.find();
		// TODO Unexpected file structure can cause failure here 
		String blocksPath = findBlocksPath.group();
		String locnsPath = findLocnsPath.group();
		
		// Unzip the needed files only to a temp directory
		File newCsvDir = new File(GeoLiteLocator.GEOIP_FILES_PATH, "newcsv");
		newCsvDir.mkdir();
		Proc.run("unzip -j \"" + zipPath  + "\" \"" + blocksPath + "\" -d \"" + newCsvDir.getCanonicalPath() + "\"");
		Proc.run("unzip -j \"" + zipPath  + "\" \"" + locnsPath + "\" -d \"" + newCsvDir.getCanonicalPath() + "\"");
		
		// Prepare to overwrite the old files with the new ones...
		File blocksSrc = new File(newCsvDir, GeoLiteLocator.BLOCKS_CSV_NAME);
		File locnsSrc = new File(newCsvDir, GeoLiteLocator.LOCATIONS_CSV_NAME);
		
		// Make sure the files were unzipped successfully!
		if (!blocksSrc.exists() || !locnsSrc.exists()) {
			// Throw something, probably email someone
		}
		File blocksDest = new File(GeoLiteLocator.GEOIP_FILES_PATH, GeoLiteLocator.BLOCKS_CSV_NAME);
		File locnsDest = new File(GeoLiteLocator.GEOIP_FILES_PATH, GeoLiteLocator.LOCATIONS_CSV_NAME);
		
		// FileUtils.move() will overwrite so no need to check dest
		FileUtils.move(blocksSrc, blocksDest);
		FileUtils.move(locnsSrc, locnsDest);
		
		// TODO Restructure this method to take a GeoLiteLocator which should be updated here
		(new GeoLiteLocator()).constructPrefixTree(locnsDest, blocksDest);
	}
}
