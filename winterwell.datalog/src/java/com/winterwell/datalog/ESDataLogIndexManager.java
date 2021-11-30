package com.winterwell.datalog;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;
import java.util.Map.Entry;

import com.winterwell.es.ESType;
import com.winterwell.es.client.ESHttpClient;
import com.winterwell.es.client.IESResponse;
import com.winterwell.es.client.admin.CreateIndexRequest;
import com.winterwell.es.client.admin.GetAliasesRequest;
import com.winterwell.es.client.admin.IndicesAdminClient;
import com.winterwell.es.client.admin.IndicesAliasesRequest;
import com.winterwell.es.client.admin.PutMappingRequest;
import com.winterwell.es.client.admin.CreateIndexRequest.Analyzer;
import com.winterwell.es.fail.ESIndexAlreadyExistsException;
import com.winterwell.utils.Null;
import com.winterwell.utils.log.Log;
import com.winterwell.utils.time.TUnit;
import com.winterwell.utils.time.Time;

public class ESDataLogIndexManager {

	public boolean registerDataspace(Dataspace dataspace) {
		boolean regd = registerDataspace2(dataspace, new Time());
		// Also pre-register the next month, to avoid issues at the switch-over
		// (Bug seen Nov 2021: at the monthly switch-over, an auto-generated mapping was made with text fields instead of keyword)
		registerDataspace2(dataspace, new Time().plus(TUnit.MONTH));
		return regd;
	}

	

	/**
	 * NB: split out for testing reasons, so we can poke at older dataspaces
	 * @param dataspace
	 * @param now
	 * @return
	 */
	boolean registerDataspace2(Dataspace dataspace, Time now) {
		String baseIndex = baseIndexFromDataspace(dataspace, now);
		read vs write
		handling special indexes??
		// fast check of cache
		if (knownBaseIndexes.contains(baseIndex)) {
			return false;
		}
		ESHttpClient _client = client(dataspace);
		if (_client.admin().indices().indexExists(baseIndex)) {
			knownBaseIndexes.add(baseIndex);
			assert knownBaseIndexes.size() < 100000;
			
			// HACK: patch old setups which might not have aliases
			registerDataspace3_patchAliases(_client, dataspace, baseIndex, now);			
			
			return false;
		}
		// make it, with a base and an alias
		return registerDataspace3_doIt(dataspace, baseIndex, _client, now);
	}

	private void registerDataspace3_patchAliases(ESHttpClient _client, Dataspace dataspace, String baseIndex, Time now) {
		hm
		String readIndex = readIndexFromDataspace(dataspace);
		String writeIndex = writeIndexFromDataspace(dataspace);
		
		IndicesAdminClient indices = _client.admin().indices();
		IESResponse ra = indices.getAliases(readIndex).get();
		Set<String> readIndices = GetAliasesRequest.getBaseIndices(ra);
		IESResponse wa = indices.getAliases(writeIndex).get();
		Set<String> writeIndices = GetAliasesRequest.getBaseIndices(wa);
		
		IndicesAliasesRequest aliasEdit = indices.prepareAliases();
		if ( ! readIndices.contains(baseIndex)) {
			aliasEdit.addAlias(baseIndex, readIndex);
		}
		if ( ! writeIndices.contains(baseIndex)) {
			aliasEdit.addAlias(baseIndex, writeIndex);
		}
		ArrayList<String> otherWriters = new ArrayList(writeIndices);
		otherWriters.remove(baseIndex);
		for (String ow : otherWriters) {
			aliasEdit.removeAlias(ow, writeIndex);
		}
		if ( ! aliasEdit.isEmpty()) {		
			aliasEdit.setDebug(true);
			IESResponse ok = aliasEdit.get().check();
			Log.d(LOGTAG, "registerDataspace - patchAliases for "+dataspace+" = "+baseIndex+": "+aliasEdit.getBodyJson());
		} else {
			Log.d(LOGTAG, "registerDataspace - patchAliases - no action for "+dataspace+" = "+baseIndex);
		}
		return;
	}
	
	private void registerDataspace4_swapWriteIndexAlias(Dataspace dataspace, String baseIndex, ESHttpClient _client, Time now) 
	{
		hm
		// swap the write index over			
		IndicesAliasesRequest aliasSwap = _client.admin().indices().prepareAliases();
		aliasSwap.setDebug(true);
		aliasSwap.setRetries(10); // really try again!
		String writeIndex = writeIndexFromDataspace(dataspace);
		aliasSwap.addAlias(baseIndex, writeIndex);			
		// remove the write alias for the previous month
		Time prevMonth = now.minus(TUnit.MONTH);
		assert prevMonth.getMonth() % 12 == (now.getMonth() - 1) % 12 : prevMonth;
		String prevBaseIndex = baseIndexFromDataspace(dataspace, prevMonth);
		// does it exist?
		boolean prevExists = _client.admin().indices().indexExists(prevBaseIndex);
		if (prevExists) {
			aliasSwap.removeAlias(prevBaseIndex, writeIndex);
		}
		aliasSwap.get().check(); // What happens if we fail here??		
	}

	/**
	 * per-month index blocks
	 * @param time
	 * @return the base index name
	 */
	public String baseIndexFromDataspace(Dataspace dataspace, Time time) {
		// replaces _client.getConfig().getIndexAliasVersion()
		String v = time.format("MMMyy").toLowerCase();
		String index = writeIndexFromDataspace(dataspace);
		return index+"_"+v;
	}
	

	
	final Set<String> knownBaseIndexes = new HashSet();
	
	

	/**
	 * 
	 * @param dataspace
	 * @param baseIndex
	 * @param _client
	 * @param now NB: This is to allow testing to simulate time passing
	 * @return
	 */
	private synchronized boolean registerDataspace3_doIt(Dataspace dataspace, String baseIndex, ESHttpClient _client, Time now) {
		// race condition - check it hasn't been made
		if (_client.admin().indices().indexExists(baseIndex)) {
			knownBaseIndexes.add(baseIndex);
			return false;
		}
		try {
			Log.i(LOGTAG, "register dataspace: "+dataspace+" baseIndex: "+baseIndex);
			// HACK
			CreateIndexRequest pc = _client.admin().indices().prepareCreate(baseIndex);			
//			actually, you can have multiple for all pc.setFailIfAliasExists(true); // this is synchronized, but what about other servers?
			pc.setDefaultAnalyzer(Analyzer.keyword);
			// aliases: index and index.all both point to baseIndex  
			// Set the query index here. The write one is set later as an atomic swap.			
			pc.setAlias(ESStorage.readIndexFromDataspace(dataspace));
			pc.setDebug(true);			
			IESResponse cres = pc.get();
			cres.check();

			// register some standard event types??
			registerDataspace4_mapping(_client, dataspace, now);
			
			// swap the right index -- but not ahead of time!
			registerDataspace4_swapWriteIndexAlias(dataspace, baseIndex, _client, now);
			return true;
			
		} catch (ESIndexAlreadyExistsException ex) {
			Log.i(LOGTAG, ex); // race condition - harmless
			return false;
		} catch(Throwable ex) {			
			Log.e(LOGTAG, ex);
			// swallow and carry on -- an out of date schema may not be a serious issue
			return false;
		}
	}





	private void registerDataspace4_mapping(ESHttpClient _client, Dataspace dataspace, Time now) 
	{
		String esType = ESTYPE;
//		String v = _client.getConfig().getIndexAliasVersion();
		String index = baseIndexFromDataspace(dataspace, now);
		PutMappingRequest pm = _client.admin().indices().preparePutMapping(index, esType);
		// See DataLogEvent.COMMON_PROPS and toJson()
		ESType keywordy = new ESType().keyword().norms(false).lock();
		// Huh? Why were we using type text with keyword analyzer??
//				.text().analyzer("keyword")					
//				.fielddata(true);
		ESType props = new ESType()
				.property("k", keywordy)
				.property("v", new ESType().text().norms(false))
				.property("n", new ESType().DOUBLE());
		ESType simpleEvent = new ESType()
				.property(DataLogEvent.EVT, keywordy.copy()) // ?? should we set fielddata=true??
				.property("time", new ESType().date())
				.property(count, new ESType().DOUBLE())
				.property("props", props);		
		// common probs...
		for(Entry<String, Class> cp : DataLogEvent.COMMON_PROPS.entrySet()) {
			// HACK to turn Class into ESType
			ESType est = keywordy.copy();
			if (cp.getValue()==StringBuilder.class) {
				est = new ESType().text().norms(false);
			} else if (cp.getValue()==Time.class) {
				est = new ESType().date();
			} else if (cp.getValue()==Double.class) {
				est = new ESType().DOUBLE();
			} else if (cp.getValue()==Integer.class) {
				est = new ESType().INTEGER();
			} else if (cp.getValue()==Long.class) {
				est = new ESType().LONG();					 
			} else if (cp.getValue()==Object.class) {
				if ("geo".equals(cp.getKey())) {
					est = new ESType().geo_point();
				}
			} else if (cp.getValue()==Null.class) {
				// HACK primitive or object?
				if ("nonce".equals(cp.getKey())) {
					est = new ESType().keyword().noIndex();
				} else {
					est = new ESType().object().noIndex();
				}
			}
			simpleEvent.property(cp.getKey(), est);
		}		
				
		pm.setMapping(simpleEvent);
		pm.setDebug(true);
		IESResponse res = pm.get();
		res.check();
	}
}
