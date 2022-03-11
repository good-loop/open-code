package com.winterwell.datalog.server;

import java.io.File;
import java.util.HashMap;
import java.util.Map;

import com.winterwell.utils.io.CSVReader;

/**
 * Consumes MaxMind's free GeoLite2 IPv4 CIDR-to-country CSVs and gives basic IP-to-country geolocation 
 * @author roscoe
 *
 */
public class GeoLiteLocator {
	// Binary tree - convert an IP to binary and traverse until you hit a leaf, that's the (probable) country it's in.
	static Node prefixes;
	
	public static final String GEOIP_FILES_PATH = "./geoip/";
	public static final String LOCATIONS_CSV_NAME = "GeoLite2-Country-Locations-en.csv";
	public static final String BLOCKS_CSV_NAME = "GeoLite2-Country-Blocks-IPv4.csv";
	
	public GeoLiteLocator() {
		if (prefixes != null) return;
		
		prefixes = new Node();
		File locnsFile = new File(GEOIP_FILES_PATH, LOCATIONS_CSV_NAME);
		File blocksFile = new File(GEOIP_FILES_PATH, BLOCKS_CSV_NAME);
		this.constructPrefixTree(locnsFile, blocksFile);
	}
	
	/**
	 * Construct the binary IP prefix to country code search tree
	 * @param locnsFile The GeoLite2 location descriptions file
	 * @param blocksFile The GeoLite2 CIDR blocks file
	 */
	void constructPrefixTree(File locnsFile, File blocksFile) {
		// Location desc file header and sample row:
		// gogeoname_id,locale_code,continent_code,continent_name,country_iso_code,country_name,is_in_european_union
		// 49518,en,AF,Africa,RW,Rwanda,0
		// CIDR blocks file Header and sample row:
		// network,geoname_id,registered_country_geoname_id,represented_country_geoname_id,is_anonymous_proxy,is_satellite_provider
		// 1.0.0.0/24,2077456,2077456,,0,0
		
		if (!locnsFile.exists() || !blocksFile.exists()) {
			// TODO FileNotFoundException?
		}
		
		// First assemble a mapping of numeric country IDs to two-letter ISO country codes, as that's what we want to store
		CSVReader locnsReader = new CSVReader(locnsFile);
		Map<String,String> locnToISOCode = new HashMap<String, String>();
		for (Map<String, String> row : locnsReader.asListOfMaps()) {
			locnToISOCode.put(row.get("geoname_id"), row.get("country_iso_code"));
		}
		locnsReader.close();
		
		// New root node for search tree - will swap in once complete
		Node newPrefixes = new Node();
				
		// Parse the blocks file and construct searchable tree
		CSVReader blocksReader = new CSVReader(blocksFile);	
		for (Map<String, String> row : blocksReader.asListOfMaps()) {
			// Split "128.64.32.16/28" into network and prefix-length parts
			String[] cidr = row.get("network").split("/");
			String network = cidr[0];
			
			String networkBinary = ipToBinary(network);
			
			// Truncate binary string to prefix length
			String prefixLengthString = cidr[1];
			Integer prefixLength = Integer.parseInt(prefixLengthString);
			if (prefixLength > networkBinary.length()) {
				System.out.println("PROBLEM: network " + row.get("network") + " as binary " + networkBinary + " prefix length " + prefixLengthString);
			}
			networkBinary = networkBinary.substring(0, prefixLength);
			
			// Find corresponding ISO country code
			String countryCode;
			
			// Some networks correspond to known proxies or satellite ISPs, so country code isn't useful
			String isProxy = row.get("is_anonymous_proxy");
			String isSatellite = row.get("is_satellite_provider");
			if (!"0".equals(isProxy)) {
				countryCode = "PROXY";
			} else if (!"0".equals(isSatellite)) {
				countryCode = "SAT";
			} else {
				// Looks like a real country!
				String geonameId = row.get("geoname_id");
				countryCode = locnToISOCode.get(geonameId);				
			}

			// Put this country code in the tree under the specified network prefix
			newPrefixes.put(networkBinary, countryCode);
		}
		blocksReader.close();
	}
	
	private String zeroes = "00000000"; // for padding
	
	/**
	 * Convert dot-decimal representation of an IPV4 address to binary string
	 * @param ip eg "192.168.1.1"
	 * @return eg "11000000101010000000000100000001"
	 */
	private String ipToBinary(String ip) {
		String bin = "";
		for (String byteStr : ip.split("\\.")) {
			Integer byteInt = Integer.parseInt(byteStr);
			String byteBin = Integer.toBinaryString(byteInt);
			bin += (zeroes.substring(0, 8 - byteBin.length()) + byteBin);
		}
		return bin;
	}
	
	/**
	 * If this IP falls within a known network prefix, return its probable ISO country code.
	 * @param ip eg "62.30.12.102"
	 * @return eg "GB". Never null, empty string for failure.
	 */
	public String getCountryCode(String ip) {
		Object maybeCC = prefixes.get(ipToBinary(ip));
		if (maybeCC != null && maybeCC instanceof String) return (String) maybeCC;
		return "";
	}

	public void getLatestFiles() {
		// TODO Auto-generated method stub
		
	}
}

/**
 * Binary tree for matching network prefixes (as strings of '0'/'1') to country codes.
 * I mean, it can probably be used for a lot of other things. But that's what it does here.
 * @author roscoe
 *
 */
class Node {
	Object zero;
	Object one;
	
	public Object get(String address) {
		return get(address, 0);
	}
	
	public Object get(String address, int index) {
		Object next = (address.charAt(index) == '0') ? zero : one;
		if (!(next instanceof Node)) return next;
		return ((Node)next).get(address, index + 1);
	}
	
	public void put(String address, Object obj) {
		put(address, obj, 0);
	}
	
	public void put(String address, Object obj, int index) {
		// last index? store obj at this.zero or this.one as appropriate
		if (index == address.length() - 1) {
			if (address.charAt(index) == '0') {
				zero = obj;
			} else {
				one = obj;
			}
			return;
		}
		Node nextNode;
		if (address.charAt(index) == '0') {
			nextNode = (Node) zero;
			if (nextNode == null) {
				nextNode = new Node();
				zero = nextNode;
			}
		} else {
			nextNode = (Node) one;
			if (nextNode == null) {
				nextNode = new Node();
				one = nextNode;
			}
		}
		nextNode.put(address, obj, index + 1);
	}
}
