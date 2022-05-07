package com.winterwell.nlp.dict;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import com.winterwell.utils.Printer;
import com.winterwell.utils.StrUtils;
import com.winterwell.utils.TodoException;
import com.winterwell.utils.containers.ArraySet;
import com.winterwell.utils.containers.Containers;
import com.winterwell.utils.log.Log;

public class NameMapper {

	private static final String LOGTAG = "NameMapper";
	private Dictionary ourNames;
	private List<String> theirAmbiguous = new ArrayList<>();
	/**
	 * TODO currently always null?!
	 */
	private Map<String,String> mappingImportRow2ourRow;
	// NB: make it thread safe - concurrent mod exception seen Jan 2022
	private Map<String, String> ourRowNames4csvRowName = Collections.synchronizedMap(new HashMap());

	public NameMapper(Dictionary ourNames) {
		this.ourNames = ourNames;
	}

	/**
	 * See Business.getRow2() - refactor to share
	 * @param rowName
	 * @param rowNames
	 * @return
	 */
	public String run2_ourRowName(String rowName) {
		if (rowName==null) throw new NullPointerException();
		// mapping?
		if (mappingImportRow2ourRow != null) {
			String mappedName = Containers.getLenient(mappingImportRow2ourRow, rowName);
			if (mappedName!=null) {
				// try correcting for slight mismatches
				String mn2 = run2_ourRowName2_noLookup(rowName);
				if (mn2==null) {
					return mappedName;
				}
				if ( ! mappedName.equals(mn2)) {
					Log.w(LOGTAG, "Mapped name changed from "+mappedName+" to "+mn2);
				}
				return mn2;
			}
		}
		// no set mapping -- work it out if we can
		String mappedName = run2_ourRowName2_noLookup(rowName);
		if (mappedName==null) {
			Log.d(LOGTAG, "Unmapped row: "+rowName);
		}
		return mappedName;
	}
	
	String run2_ourRowName2_noLookup(String rowName) {
		Dictionary rowNames = ourNames;
		// exact match
		if (rowNames.contains(rowName)) {
			String m = rowNames.getMeaning(rowName);
			if (m==null) {
				// bleurgh
				String[] matches = rowNames.getMeanings(rowName);
				Log.d(LOGTAG, "(skip match) Ambiguous name matches for "+rowName+" to "+Printer.str(matches));
			}
			return m;
		}
		// match ignoring case+
		String rowNameCanon = StrUtils.toCanonical(rowName);
		if (rowNames.contains(rowNameCanon)) {
			return rowNames.getMeaning(rowNameCanon);
		}
		// match on ascii, no spaces
		String rowNameAscii = rowNameCanon.replaceAll("[^a-zA-Z0-9 ]", "");
		if ( ! rowNameAscii.isEmpty() && rowNames.contains(rowNameAscii)) {
			return rowNames.getMeaning(rowNameAscii);
		}
		// try removing "total" since MS group rows are totals
		if (rowNameCanon.contains("total")) {			
			String rn2 = rowNameCanon.replace("total", "").trim();
			// Xero exports hack for e.g. "Total 01 Property"
			rn2 = rn2.replaceFirst("^\\d+", "").trim();
			
			assert rn2.length() < rowNameCanon.length();
			if ( ! rn2.isBlank()) {
				String found = run2_ourRowName(rn2);
				if (found!=null) {
					return found;
				}
			}
			Log.d(LOGTAG, "Unmatched total: "+rn2);
		}
		// Allow a first-word or starts-with match if it is unambiguous e.g. Alice = Alice Smith
		ArraySet<String> matches = new ArraySet();
		String firstWord = rowNameCanon.split(" ")[0];
		for(String existingName : rowNames) {
			String existingNameFW = existingName.split(" ")[0];
			if (firstWord.equals(existingNameFW)) {
				matches.add(rowNames.getMeaning(existingName));
			}
		}
		if (matches.size() == 1) {
			return matches.first();
		}
		if (matches.size() > 1) {
			Log.d(LOGTAG, "(skip match) Ambiguous 1st word matches for "+rowName+" to "+matches);
		}
		// starts-with?
		matches.clear();
		for(String existingName : rowNames) {
			if (rowName.startsWith(existingName)) {
				matches.add(rowNames.getMeaning(existingName));
			} else if (existingName.startsWith(rowName)) {
				matches.add(rowNames.getMeaning(existingName));
			}
		}
		if (matches.size() == 1) {
			return matches.first();
		}
		if (matches.size() > 1) {
			Log.d(LOGTAG, "(skip match) Ambiguous startsWith matches for "+rowName+" to "+matches);
		}
		// Nothing left but "Nope"
		return null;
	}
	
	public List<String> getAmbiguous(String rowName) {
		Dictionary rowNames = ourNames;
		// exact match
		if (rowNames.contains(rowName)) {
			String m = rowNames.getMeaning(rowName);
			if (m==null) {
				// bleurgh
				String[] matches = rowNames.getMeanings(rowName);
				return Containers.asList(matches);
			}
			return Collections.singletonList(m);
		}
		// match ignoring case+
		String rowNameCanon = StrUtils.toCanonical(rowName);
		if (rowNames.contains(rowNameCanon)) {
			String[] matches = rowNames.getMeanings(rowNameCanon);
			return Containers.asList(matches);
		}
		// match on ascii
		String rowNameAscii = rowNameCanon.replaceAll("[^a-zA-Z0-9]", "");
		if ( ! rowNameAscii.isEmpty() && rowNames.contains(rowNameAscii)) {
			String[] matches = rowNames.getMeanings(rowNameAscii);
			return Containers.asList(matches);
		}
		// try removing "total" since MS group rows are totals
		if (rowNameCanon.contains("total")) {			
			String rn2 = rowNameCanon.replace("total", "").trim();
			// Xero exports hack for e.g. "Total 01 Property"
			rn2 = rn2.replaceFirst("^\\d+", "").trim();
			
			assert rn2.length() < rowNameCanon.length();
			if ( ! rn2.isBlank()) {
				String found = run2_ourRowName(rn2);
				if (found!=null) {
					return Collections.singletonList(found);
				}
			}
			Log.d(LOGTAG, "Unmatched total: "+rn2);
		}
		// Allow a first-word or starts-with match if it is unambiguous e.g. Alice = Alice Smith
		ArraySet<String> matches = new ArraySet();
		String firstWord = rowNameCanon.split(" ")[0];
		for(String existingName : rowNames) {
			String existingNameFW = StrUtils.toCanonical(existingName).split(" ")[0];
			if (firstWord.equals(existingNameFW)) {
				matches.add(rowNames.getMeaning(existingName));
			}
		}
		if ( ! matches.isEmpty()) {
			return matches.asList();
		}
		// starts-with?
		for(String existingName : rowNames) {
			if (rowName.startsWith(existingName)) {
				matches.add(rowNames.getMeaning(existingName));
			} else if (existingName.startsWith(rowName)) {
				matches.add(rowNames.getMeaning(existingName));
			}
		}
		if ( ! matches.isEmpty()) {
			return matches.asList();
		}
		// Nothing left but "Nope"
		return null;
	}
	
	

	public void putTheirsOurs(String rowName, String ourRowName) {
		// TODO Auto-generated method stub
		
		// we want to prevent more than 1 rowName which corresponds to the same ourRowName
		// if ourRowName is already added previously, do an ambiguity check			
		if (containsOurName(ourRowName)) {
			Iterator it = ourRowNames4csvRowName.entrySet().iterator();
			while (it.hasNext()) {
				Map.Entry pair = (Map.Entry) it.next();
				// if there is already an exact match previously, ignore this entry
				if (pair.getValue().equals(ourRowName) && ourNames.contains((String) pair.getKey())) {	
					theirAmbiguous.add(rowName); // this entry should not be added to mapping
					rowName = (String) pair.getKey();
					break;
				}
			}
		}
		
		ourRowNames4csvRowName.put(rowName, ourRowName);
	}

	private boolean containsOurName(String ourRowName) {
		return ourRowNames4csvRowName.containsValue(ourRowName);
	}

	public Map<String, String> getOurNames4TheirNames() {
		return ourRowNames4csvRowName;
	}

	public List<String> getTheirAmbiguous() {
		return theirAmbiguous;
	}

	public Dictionary getDict() {
		return ourNames;
	}

}
