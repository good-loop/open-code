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
import java.util.TimerTask;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.lang3.time.DateUtils;

import com.winterwell.utils.Proc;
import com.winterwell.utils.Utils;
import com.winterwell.utils.io.ConfigFactory;
import com.winterwell.utils.io.FileUtils;
import com.winterwell.utils.log.Log;
import com.winterwell.web.ConfigException;
import com.winterwell.web.FakeBrowser;
import com.winterwell.web.LoginDetails;
import com.winterwell.web.WebEx;
import com.winterwell.web.app.Logins;


/**
 * Updater for our GeoLite2 IP-geolocation CSVs.
 * Contacts MaxMind servers, checks timestamp on latest available bundle file,
 * and if it's newer than the current one downloads it, extracts relevant CSVs,
 * and instructs GeoLiteLocator to refresh.
 * TODO Restructure this to call from inside DataLogServer on a timer
 * @author roscoe
 *
 */
public class GeoLiteUpdateTask extends TimerTask {
	private static final String LOGTAG = "GeoLiteUpdateTask";
	
	GeoLiteLocator geoLiteLocator;
	
	/** Replace $DATABASE and $LICENSE_KEY to use */
	static final String BASE_URL = "https://download.maxmind.com/app/geoip_download?edition_id=GeoLite2-City-CSV&license_key=$LICENSE_KEY";
	
	static final String CSV_URL = BASE_URL + "&suffix=zip"; 
	static final String CHECKSUM_URL = BASE_URL + "&suffix=zip.sha256";
	
	// We retain the downloaded CSV zip here
	static final String ZIP_NAME = "geolite2_csv.zip";
	
	
	// For extracting the path to the block and location files from the downloaded zip's directory listing
	static final Pattern BLOCKS_FILENAME_PATTERN = Pattern.compile("^.+" + GeoLiteLocator.BLOCKS_CSV_NAME + "$", Pattern.MULTILINE);
	static final Pattern LOCNS_FILENAME_PATTERN = Pattern.compile("^.+" + GeoLiteLocator.LOCATIONS_CSV_NAME + "$", Pattern.MULTILINE);
	
	static final Pattern HASH_PATTERN = Pattern.compile("[\\da-f]+");
	 
	public GeoLiteUpdateTask(GeoLiteLocator gll) {
		this.geoLiteLocator = gll;
	}
	
	@Override
	public void run() {
		Log.d(LOGTAG, "Checking for fresh GeoLite2 CSV...");
		
		// Get our MaxMind license key from the logins file...
		LoginDetails ld = Logins.get("maxmind.com");
		if (ld == null || Utils.isBlank(ld.apiSecret)) {
			// Probably just means this server's logins repo is behind, but make sure someone knows
			Log.e(LOGTAG, "Can't run: no apiSecret entry for maxmind.com in misc logins file?");
			return;
		}
		String csvUrl = CSV_URL.replace("$LICENSE_KEY", ld.apiSecret);
		
		File zipFile = new File(GeoLiteLocator.GEOIP_FILES_PATH, ZIP_NAME);
		
		try {
			// Check the new CSV bundle's Last-Modified header...
			HttpURLConnection conn = (HttpURLConnection) (new URL(csvUrl).openConnection());
			conn.setRequestMethod("HEAD");
			conn.connect();
			Long newVersionTimestamp = conn.getHeaderFieldDate("Last-Modified", 0);
			conn.disconnect();
			// ...and compare it to the timestamp of the current version.
			if (zipFile.exists() && zipFile.lastModified() >= newVersionTimestamp) {
				Log.d(LOGTAG, "Current GeoLite2 is up to date.");
				return;
			}
		} catch (Exception e) {
			// TODO ProtocolException shouldn't happen here but handle IOException 
		}
		
		Log.d(LOGTAG, "Newer GeoLite2 data available, downloading...");

		// Time to update the CSVs!
		// Make sure ./geoip exists
		File geoipDir = new File(GeoLiteLocator.GEOIP_FILES_PATH);
		if (!geoipDir.exists()) geoipDir.mkdirs();
		
		// Now let's download the new zip file.
		FakeBrowser fb = new FakeBrowser();
		fb.setMaxDownload(-1); // File is ~45MB, default 10MB limit throws exception
		File tmpFile = fb.getFile(csvUrl);
		
		// Compare reference SHA256 to that of downloaded file
		String checksumUrl = CHECKSUM_URL.replace("$LICENSE_KEY", ld.apiSecret);
		String referenceHash = fb.getPage(checksumUrl);
		String downloadedHash = Proc.run("sha256sum " + tmpFile.getPath());
		Matcher refHashMatcher = HASH_PATTERN.matcher(referenceHash);
		refHashMatcher.find();
		if (!downloadedHash.contains(refHashMatcher.group())) {
			// TODO Retry download at least once on hash mismatch
			Log.e(LOGTAG, "Downloaded GeoLite2 zip didn't match reference SHA256 hash, aborting");
			tmpFile.delete();
			return;
		}
		
		Log.d(LOGTAG, "Downloaded GeoLite2 zip successfully. Extracting files...");
		
		// What do we want to extract from the zip?
		String blocksPath = "";
		String locnsPath = "";
		try {
			// Print the file paths contained in the new zip and find the paths to the block and location files
			String zipContents = Proc.run("unzip -Z -1 " + tmpFile.getPath()); // -Z = list contents, -1 = 1 line per file
			Matcher findBlocksPath = BLOCKS_FILENAME_PATTERN.matcher(zipContents);
			Matcher findLocnsPath = LOCNS_FILENAME_PATTERN.matcher(zipContents);
			findBlocksPath.find();
			findLocnsPath.find();
			blocksPath = findBlocksPath.group();
			locnsPath = findLocnsPath.group();	
		} catch (Exception e) {
			// This probably means the ZIP file content layout has changed & we need to rewrite.
			Log.e(LOGTAG, "Couldn't find expected files in new GeoLite2 zip");
			tmpFile.delete();
			return;
		}
		

		// Unzip the needed files only to a temp directory
		File newCsvDir = new File(GeoLiteLocator.GEOIP_FILES_PATH, "newcsv");
		if (newCsvDir.exists()) {
			FileUtils.deleteDir(newCsvDir); // Ensure no previous dir lying around from a failed run
		}
		newCsvDir.mkdir();
		// -j flag = ignore zip directory structure and extract to specified dir
		Proc.run("unzip -j \"" + tmpFile.getPath()  + "\" \"" + blocksPath + "\" -d \"" + newCsvDir.getPath() + "\"");
		Proc.run("unzip -j \"" + tmpFile.getPath() + "\" \"" + locnsPath + "\" -d \"" + newCsvDir.getPath() + "\"");
		// Grab unzipped files...
		File blocksTemp = new File(newCsvDir, GeoLiteLocator.BLOCKS_CSV_NAME);
		File locnsTemp = new File(newCsvDir, GeoLiteLocator.LOCATIONS_CSV_NAME);
		
		Log.d(LOGTAG, "Extracted CSV files. Constructing prefix tree...");
		
		try {
			// OK, try to parse those CSVs
			geoLiteLocator.constructPrefixTree(locnsTemp, blocksTemp);
			
			// No errors! Overwrite the previous version zip and CSV files with the new ones.
			// FileUtils.move() will overwrite so no need to check dest
			File blocksDest = new File(GeoLiteLocator.GEOIP_FILES_PATH, GeoLiteLocator.BLOCKS_CSV_NAME);
			File locnsDest = new File(GeoLiteLocator.GEOIP_FILES_PATH, GeoLiteLocator.LOCATIONS_CSV_NAME);
			FileUtils.move(blocksTemp, blocksDest);
			FileUtils.move(locnsTemp, locnsDest);
			FileUtils.move(tmpFile, zipFile);
			Log.d("Success! GeoLiteLocator has been updated.");
		} catch (FileNotFoundException e) {
			Log.e(LOGTAG, "Downloaded new GeoLite2 ZIP but missing expected file: " + e);
		} catch (ParseException e) {
			Log.e(LOGTAG, "Downloaded new GeoLite2 ZIP but found unexpected CSV headers:" + e);
		} finally {
			// Clean up any files still left
			FileUtils.deleteDir(newCsvDir);
		}
	}
}
