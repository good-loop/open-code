package com.winterwell.datalog;

import java.util.List;

import com.winterwell.depot.IInit;
import com.winterwell.gson.Gson;
import com.winterwell.utils.containers.Containers;
import com.winterwell.utils.containers.ListMap;
import com.winterwell.utils.log.Log;
import com.winterwell.utils.threads.Actor;
import com.winterwell.utils.web.WebUtils2;
import com.winterwell.web.FakeBrowser;

/**
 * Using an Actor model here for high-throughput low-latency
 * @author daniel
 *
 */
public class CallbackManager extends Actor<DataLogEvent> implements IInit {

	private static final String LOGTAG = "CallbackManager";
	
	ListMap<String,Callback> callbacksForDataspace = new ListMap();

	private DataLogConfig config;
	
	public CallbackManager() {		
	}	
	
	@Override
	public void init() {
		config = DataLog.getImplementation().getConfig();
		if (config.noCallbacks) {
			return;
		}
		// NB: This is where lgwebhook gets wired up for adserver -- depending on the config!
		// a call to adserver
		if (config.callbacks != null) {
			for(String callback : config.callbacks) {
				try {
					String[] bits = callback.trim().split("\\s+");
					assert bits.length==3 : callback;
					addCallback(new Dataspace(bits[0]), bits[1], bits[2]);					
				} catch(Throwable ex) {
					Log.e(LOGTAG, ex); // swallow and carry on
				}
			}
//			KServerType mtype = AppUtils.getServerType(null);
//			StringBuilder url = AppUtils.getServerUrl(mtype, "as.good-loop.com");
//			url.append("/lgwebhook");
//			// minview is where money gets spent. donation is when a user picks a charity.
//			Dataspace gl = new Dataspace("gl");
//			for(String evt : new String[] {"minview","click","donation"}) {
//				addCallback(gl, evt, url.toString());
//			}
		}
	}

	private void addCallback(Dataspace dataspace, String evt, String url) {
		if ( ! WebUtils2.URL_REGEX.matcher(url).matches()) {
			throw new IllegalArgumentException(url+" is not a url. evt:"+evt);
		}
		Callback cb = new Callback(dataspace, evt, url.toString());
		callbacksForDataspace.add(dataspace.toString(), cb);
	}
	

	@Override
	protected void consume(DataLogEvent msg, Actor sender) throws Exception {
		assert msg != null;
		if (config.noCallbacks) {
			Log.d(LOGTAG, "config: no callbacks");
			return;
		}
		List<Callback> cbs = callbacksForDataspace.get(msg.dataspace);
		Log.d(LOGTAG, "callbacks: "+cbs+" for "+msg);
		if (cbs==null) return;
		for (Callback callback : cbs) {
			// does the event match the callback?
			if ( ! matches(msg, callback)) {
				continue;
			}
			try {
				consume2_doCallback(msg, callback);
			} catch(Exception ex) {
				// retry once
				if (sender != this) {
					send(msg, this);
					return;
				}
				throw ex;
			}
		}
	}

	protected void consume2_doCallback(DataLogEvent msg, Callback callback) {
		String json = Gson.toJSON(msg);
		Log.d(LOGTAG, callback.url+" for "+msg+" Posting "+json);
		FakeBrowser fb = new FakeBrowser();
		fb.setUserAgent(FakeBrowser.HONEST_USER_AGENT);
		fb.setDebug(true);		
		String ok = fb.post(callback.url, json);
	}

	private boolean matches(DataLogEvent msg, Callback callback) {
		if (callback.evt==null) return true; // match all
		// NPE paranoia
		if (msg.getEventType()==null) return false;
		if (Containers.contains(callback.evt, msg.getEventType())) {
			return true;
		}
		return false;
	}
	
}
