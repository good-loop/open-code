package com.winterwell.utils.web;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.winterwell.utils.StrUtils;
import com.winterwell.utils.TodoException;
import com.winterwell.utils.Utils;
import com.winterwell.utils.WrappedException;
import com.winterwell.utils.containers.ArrayMap;
import com.winterwell.utils.containers.Containers;
import com.winterwell.utils.web.SimpleJson;
import com.winterwell.web.ajax.JThing;
/**
 * diff using json-patch https://tools.ietf.org/html/rfc6902
 * @author daniel
 * @testedby {@link JsonPatchTest}
 */
public class JsonPatch implements IHasJson {

	List<JsonPatchOp> diffs;
	private List<JsonPatchOp> extraDiffs = new ArrayList();

	public List<JsonPatchOp> getExtraDiffs() {
		return extraDiffs;
	}
	
	public List<JsonPatchOp> getDiffs() {
		return diffs;
	}
	
	public JsonPatch(List<JsonPatchOp> diffs) {
		Utils.check4null(diffs);
		this.diffs = diffs;		
	}
	
	public JsonPatch(Map before, Map after) {
		this.diffs = new ArrayList();
		diffMap("", before, after);		
	}
	
	public JsonPatch(List before, List after) {
		this.diffs = new ArrayList();
		diffList("", before, after);		
	}
	
	public static JsonPatch fromJson(List<Map> jsonDiffs) {
		List<JsonPatchOp> jdiffs = Containers.apply(jsonDiffs, JsonPatchOp::new);
		return new JsonPatch(jdiffs);
	}

	private void diffMap(String path, Map before, Map after) {
		if (after==null) {
			diffs.add(JsonPatchOp.remove(path));
			return;
		}
		if (before==null) {
			diffs.add(JsonPatchOp.add(path, after));
			return;
		}
		Set<String> akeys = after.keySet();
		for(String k : akeys) {
			// changes?
			Object av = after.get(k);
			if (av==null) continue;
			Object bv = before.get(k);
			String pathk = path+"/"+k;
			if (bv==null) {
				diffs.add(JsonPatchOp.add(pathk, av));
				continue;
			}			
			if (av.equals(bv)) continue;
			// recurse?
			if (bv instanceof Map && av instanceof Map) {
				diffMap(pathk, (Map)bv, (Map)av);
				continue;
			}
			if (bv.getClass().isArray()) bv = Containers.asList(bv);
			if (av.getClass().isArray()) av = Containers.asList(av);
			if (bv instanceof List && av instanceof List) {
				diffList(pathk, (List)bv, (List)av);
				continue;
			}
			diffs.add(JsonPatchOp.replace(pathk, av));			
		}
		// what got removed?
		Set<String> bkeys = before.keySet();
		for(String k : bkeys) {
			Object av = after.get(k);
			if (av==null) {
				diffs.add(JsonPatchOp.remove(path+"/"+k));
			}
		}
	}

	private void diffList(String path, List before, List after) {
		if (after==null) {
			diffs.add(JsonPatchOp.remove(path));
			return;
		}
		if (before==null) {
			diffs.add(JsonPatchOp.add(path, after));
			return;
		}		
		for(int k=0; k<after.size(); k++) {
			// changes?
			Object av = after.get(k);
			if (av==null) continue;
			Object bv = before.size() <= k? null : before.get(k);
			String pathk = path+"/"+k;
			if (bv==null) {
				diffs.add(JsonPatchOp.add(pathk, av));
				continue;
			}			
			if (av.equals(bv)) continue;
			// recurse?
			if (bv instanceof Map && av instanceof Map) {
				diffMap(pathk, (Map)bv, (Map)av);
				continue;
			}
			if (bv.getClass().isArray()) bv = Containers.asList(bv);
			if (av.getClass().isArray()) av = Containers.asList(av);
			if (bv instanceof List && av instanceof List) {
				diffList(pathk, (List)bv, (List)av);
				continue;
			}
			diffs.add(JsonPatchOp.replace(pathk, av));			
		}
		// what got removed?
		for(int k=after.size(); k<before.size(); k++) {
			diffs.add(JsonPatchOp.remove(path+"/"+k));			
		}			
	}

	public void apply(Map jobj) {			
		if (diffs==null || diffs.isEmpty()) {
			return;
		}		
		for (JsonPatchOp diff : diffs) {
			try {
				// NB: drop the leading / on path
				final String[] bits = diff.path.substring(1).split("/");
				Object value = diff.value;
				String[] ppath;
				String lastBit;
				switch(diff.op) {
				case add: case replace:
					set(jobj, value, bits);
					break;
				case remove:
					// remove from array or object?
					ppath = Arrays.copyOf(bits, bits.length-1);
					lastBit = bits[bits.length-1];
					Object parent = SimpleJson.get(jobj, ppath);
					if (parent instanceof List || parent.getClass().isArray()) {
						// NB: copy 'cos some lists can't be edited
						List<Object> list = new ArrayList(Containers.asList(parent));
						int i = Integer.valueOf(lastBit);
						list.remove(i);
						set(jobj, list, ppath);
					} else {
						// object -- null out
						((Map)parent).remove(lastBit);
					}
					break;			
				case move:
				case copy:
				case test:
					throw new TodoException(diff);
				}
			} catch (Exception ex) {
				// add a bit more info
				throw new WrappedException(diff.toString()+" -> "+ex.getMessage(), ex);
			}
		}
	}
	
	public void apply(List jarr) {
		if (diffs==null || diffs.isEmpty()) {
			return;
		}		
		// Hack wrap so we can use Map methods
		ArrayMap jobj = new ArrayMap("foo", jarr);
		for (JsonPatchOp diff : diffs) {
			String[] bits = ("foo"+diff.path).split("/");			
			Object value = diff.value;
			switch(diff.op) {
			case add: case replace:
				set(jobj, value, bits);
				break;
			case remove:
				set(jobj, null, bits);
				break;			
			case move:
			case copy:
			case test:
				throw new TodoException(diff);
			}
		}
	}

	@Override
	public List toJson2() throws UnsupportedOperationException {
		return Containers.apply(diffs, JsonPatchOp::toJson2);
	}
	
	@Override
	public String toString() {
		return "JsonPatch"+toJSONString();
	}

	
	/**
	 * @param jobj
	 * @param key
	 * @return jobj.key
	 */
	Map<String, Object> getAsMap(Map jobj, String key) {
		Object m = jobj.get(key);
		if (m==null) return null;
		// already exists :)
		if (m instanceof Map) {
			return (Map<String, Object>) m;
		}
		// probably an array!
		List<Object> list = Containers.asList(m);
		// make up a new map??
		Map map = new ListAsMap(list);
		return map;
	}

	
	/**
	 * HACK copy from SimpleJson so we can add catching of extra ops
	 * 
	 * Convenience for drilling down through (and making) the map-of-maps data structures
	 * that JSON tends to involve. 
	 * Uses getCreate to access/build intermediate objects.
	 * @param jobj
	 * @param value
	 * @param key 
	 */
	void set(Map<String, ?> jobj, Object value, String... key) {
		// drill down
		Map obj = jobj;
		for(int i=0,n=key.length-1; i<n; i++) {
			String k = key[i];
			Map<String, Object> nextObj = getAsMap(obj, k);
			if (nextObj!=null) {
				obj = nextObj;
				continue;
			}
			// create			
			nextObj = new ArrayMap(); // what if an array is wanted??
			if (obj instanceof ListAsMap && key.length > 1) {
				// array? copy it
				ArrayList obj2 = new ArrayList(((ListAsMap) obj).list);				
				ListAsMap obj2AsMap = new ListAsMap(obj2);
				obj2AsMap.put(k, nextObj);
				String[] pathToArray = Arrays.copyOf(key, i);
				set(jobj, obj2, pathToArray);				
			} else {
				nextObj = new ArrayMap();
				obj.put(k, nextObj);				
			}			
			obj = nextObj;
			String[] pathToK = Arrays.copyOf(key, i+1);
			String path = "/"+StrUtils.join(pathToK, "/");
			extraDiffs.add(JsonPatchOp.add(path, new ArrayMap()));
		}
		// set it
		String k = key[key.length-1];
		try {		
			if (value==null) {
				obj.remove(k);
			} else {
				obj.put(k, value);
			}
		} catch (UnsupportedOperationException ex) {
			// add to an array failed? replace the array
			if (obj instanceof ListAsMap && key.length > 1) {
				ArrayList obj2 = new ArrayList(((ListAsMap) obj).list);
				new ListAsMap(obj2).put(k, value);
				String[] pathToArray = Arrays.copyOf(key, key.length-1);
				set(jobj, obj2, pathToArray);
				return;
			}
			// can't fix
			throw ex;
		}
	}

	
}
