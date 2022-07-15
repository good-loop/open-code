package com.winterwell.datalog.server;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;

import org.eclipse.jetty.util.ajax.JSON;

import com.goodloop.data.KCurrency;
import com.winterwell.datalog.DataLog;
import com.winterwell.datalog.DataLogConfig;
import com.winterwell.datalog.DataLogEvent;
import com.winterwell.datalog.DataLogRemoteStorage;
import com.winterwell.datalog.Dataspace;
import com.winterwell.json.JSONObject;
import com.winterwell.utils.Dep;
import com.winterwell.utils.IProperties;
import com.winterwell.utils.MathUtils;
import com.winterwell.utils.Printer;
import com.winterwell.utils.StrUtils;
import com.winterwell.utils.Utils;
import com.winterwell.utils.containers.ArrayMap;
import com.winterwell.utils.containers.Containers;
import com.winterwell.utils.log.Log;
import com.winterwell.utils.threads.ICallable;
import com.winterwell.utils.time.TUnit;
import com.winterwell.utils.time.Time;
import com.winterwell.utils.web.WebUtils;
import com.winterwell.utils.web.WebUtils2;
import com.winterwell.web.FakeBrowser;
import com.winterwell.web.ajax.JsonResponse;
import com.winterwell.web.app.AppUtils;
import com.winterwell.web.app.BrowserType;
import com.winterwell.web.app.FileServlet;
import com.winterwell.web.app.KServerType;
import com.winterwell.web.app.WebRequest;
import com.winterwell.web.app.WebRequest.KResponseType;
import com.winterwell.web.fields.AField;
import com.winterwell.web.fields.BoolField;
import com.winterwell.web.fields.DoubleField;
import com.winterwell.web.fields.JsonField;
import com.winterwell.web.fields.SField;
import com.winterwell.youagain.client.AuthToken;
import com.winterwell.youagain.client.YouAgainClient;

import ua_parser.Client;
import ua_parser.Parser;


/**
 * Fast Ajax logging of stats.
 * 
 * Endpoint: /lg <br>
 * Parameters: <br>
 *  - tag Optional. Will have log prepended, so we can distinguish ajax-logged events (which could be bogus!) 
 * from internal ones. E.g. "foo" gets written as "#log.foo" <br>
 *  - msg
 * 
 * @see AServlet
 * <p>
 * TODO filter by time
 * @author daniel
 * @testedby  LgServletTest
 */
public class LgServlet {

	public static final SField TAG = DataLogFields.t;
	static final AField<Dataspace> DATASPACE = DataLogFields.d;

	public LgServlet() {		
	}
		
	/**
	 * Either set this to send json. Or add p.key=value to the url.
	 */
	static JsonField PARAMS = new JsonField("p");
	
	static final List<String> NOTP = Arrays.asList(TAG.getName(), DATASPACE.getName(), "via", "track");
	/**
	 * group-by ID for merging several events into one.
	 */
	public static final SField GBY = new SField("gby");
	
	static final BoolField track = new BoolField("track");
	
	/**
	 * Log msg to fast.log file and send appropriate response
	 * @param req
	 * @param resp
	 * @throws IOException 
	 */
	public static void fastLog(WebRequest state) throws IOException {
//		String u = state.getRequestUrl();
//		Map<String, Object> ps = state.getParameterMap();
		Dataspace ds = state.getRequired(DATASPACE);
		// TODO security check the dataspace?
		final String tag = state.getRequired(TAG).toLowerCase();
		double count = state.get(new DoubleField("count"), 1.0);
		// NB: dont IP/user track simple events, which are server-side
		boolean stdTrackerParams = ! DataLogEvent.simple.equals(tag) && state.get(track, true);
		// Read the "extra" event parameters
		Map<String,Object> params = (Map) state.get(PARAMS);		
		if (params==null) {
			// params from the url?
			final Map<String, String> smap = state.getMap();
			// e.g. 
			// https://lg.good-loop.com/lg?d=gl&t=install&idfa={idfa}&adid={adid}&android_id={android_id}&gps_adid={gps_adid}
			// &fire_adid={fire_adid}&win_udid={win_udid}&ua={user_agent}&ip={ip_address}&country={country}
			// &time={created_at}&app_id={app_id}&app_name={app_name}&store={store}&tracker_name={tracker_name}&tracker={tracker}
			// &bid={dcp_bid}
			// or use p.param for unambiguity					
			params = new HashMap();
			for(Map.Entry<String, String> kv : smap.entrySet()) {
				String v = kv.getValue();
				if (v==null || v.isEmpty()) continue;
				String k = kv.getKey();
				if (NOTP.contains(k)) continue;
				if (k.startsWith("p.")) k = k.substring(2);				
				params.put(k, v);
			}
		}
		assert params != null;
		
		// Google Analytics UTM parameters?
		// - No, we can confuse foreign utm codes with our own (e.g. campaign)
		// NB: these are also removed from the url later -- look for WebUtils2.cleanUp()
//		String ref = state.getReferer();
//		if (ref != null) {
//			readGoogleAnalyticsTokens(ref, params);
//		}

		// group by
		String gby = state.get(GBY);
		if (gby==null) {
			// bleurgh - it should be a top-level parameter, but lets catch it here too
			gby = (String) params.get(GBY.name);
		}
		ICallable<Time> ctime = state.get(DataLogFields.time);
		Time time = ctime==null? null : ctime.call();
		// log it!
		DataLogEvent logged = doLog(state, ds, gby, tag, count, time, params, stdTrackerParams, true);
				
		// also fire a callback?
		String cb = state.get(JsonResponse.CALLBACK);
		if (cb!=null) {
			try {
				FakeBrowser fb = new FakeBrowser();
				fb.setTimeOut(1000); // don't wait around - just call and go
				fb.getPage(cb);
			} catch(Exception ex) {
				// oh well
				Log.d("log.callback", cb+" from "+state+"-> "+ex);
			}
		}
		
		// Has the request already received a response (eg from TrackingPixelServlet)? Stop here.
		if (!state.isOpen()) return;

		// Reply
		// Always set Access-Control-Allow-Origin on response
		if (DataLogServer.settings.CORS) {
			WebUtils2.CORS(state, false);
		}
		// Send a .gif for a pixel?
		if (state.getResponseType()==KResponseType.image) {
			FileServlet.serveFile(TrackingPixelServlet.PIXEL, state);
			return;
		}
		// redirect?
		if (state.getRedirect() != null) {
			state.sendRedirect();
			return;
		}
		// send the event back as json
		Object jobj = logged==null? null : logged.toJsonPublic();
		JsonResponse jr = new JsonResponse(state, jobj);
		WebUtils2.sendJson(jr, state);				
	}

	/**
	 * add utm_X=v to params as X=v -- but only if X=v is not already present
	 * @param ref
	 * @param params
	 */
	static void readGoogleAnalyticsTokens(String ref, Map<String, Object> params) {
		if (ref==null) return;
//		Campaign Source (utm_source) – Required parameter to identify the source of your traffic such as: search engine, newsletter, or other referral.
//		Campaign Medium (utm_medium) – Required parameter to identify the medium the link was used upon such as: email, CPC, or other method of sharing.
//		Campaign Term (utm_source) – Optional parameter suggested for paid search to identify keywords for your ad. You can skip this for Google AdWords if you have connected your AdWords and Analytics accounts and use the auto-tagging feature instead.
//		Campaign Content (utm_content) – Optional parameter for additional details for A/B testing and content-targeted ads.
//		Campaign Name (utm_campaign) – Required parameter to identify a specific product promotion or strategic campaign such as a spring sale or othe
		Matcher m = WebUtils2.UTM_PARAMETERS.matcher(ref);
		int s = 0;
		while(m.find(s)) {
			s = m.end()-1; // the pattern captures the boundaries, so go back one
//			String g1 = m.group(1);
			String g2 = m.group(2);
			String g3 = m.group(3);
			if (g3.isEmpty()) {
				continue;
			}
			String val = WebUtils.urlDecode(g3);
			params.put(g2, val);			
		}
	}

	/**
	 * TODO refactor as Map key:ip value=user-type eg "bot"
	 */
	static List<Map> userTypeForIPorXId;
	static volatile Time userTypeForIPorXIdFetched;

	/**
	 * 
	 * @param state
	 * @param dataspace
	 * @param tag
	 * @param count
	 * @param time Optional set the event time 
	 * @param params can be null
	 * @param stdTrackerParams
	 * @param saveIt If true, use DataLog.count() to pass it into the save queue. If false, just return the event.
	 * @return event, or null if this was screened out (eg our own IPs)
	 */
	public static DataLogEvent doLog(WebRequest state, Dataspace dataspace, String gby, String tag, double count, 
			Time time, Map params, boolean stdTrackerParams, boolean saveIt) 
	{
		assert dataspace != null;		
		assert tag != null : state;
		String trckId = TrackingPixelServlet.getCreateCookieTrackerId(state);
		// special vars
		if (stdTrackerParams) {			
			params = doLog2_addStdTrackerParams(state, params, trckId);
		}
		
		// HACK remove Hetzner from the ip param 
		// TODO make this a config setting?? Or even better, the servers report their IP
		Object ip = params.get("ip"); // NB ip can be null
		if (ip instanceof String) ip = ((String) ip).split(",\\s*");
		List ips = Containers.list(ip); // NB: ips is now never null
		if (ips.contains("5.9.23.51")) {
			ips = Containers.filter(ips, a -> ! "5.9.23.51".equals(a));
			if (ips.size() == 1) {
				params.put("ip", ips.get(0));
			} else {
				params.put("ip", ips);
			}
		}
		
		// Try and retrieve country code for originating IP
		// TODO Is there an order to these? Which is most likely to be the actual client?
		for (Object ip2 : ips) {
			if (!(ip2 instanceof String)) continue;
			try {
				GeoLiteLocator.GeoIPBlock location = Dep.get(GeoLiteLocator.class).getLocation((String) ip2);
				if (location != null) {
					if (!Utils.isBlank(location.country)) {
						params.put("country", location.country);
					}
					if (!Utils.isBlank(location.subdiv1)) {
						params.put("locn_sub1", location.subdiv1);
					}
					if (!Utils.isBlank(location.subdiv2)) {
						params.put("locn_sub2", location.subdiv2);
					}
					if (!Utils.isBlank(location.city)) {
						params.put("city", location.city);
					}
					break;
				}
			} catch (NumberFormatException e) {
				Log.w("LgServlet", "Couldn't resolve country for bad IP \"" + ip2 + "\"");
			}
		}
		
		// screen out our IPs?
		if ( ! accept(dataspace, tag, params)) {
			Log.d("lg", "not accepted "+tag+" "+params);
			return null;
		}
		
		// Add ip/user type
		String userType = getInvalidType(ips);
		if (userType!=null) {
			params.put("invalid", userType);
		}
		
		// Convert into USD
		// Minor TODO refactor into a method
		if (params.get("curr") != null) {
			try {
				KCurrency nativeCurrency = KCurrency.valueOf(params.get("curr").toString());
				CurrencyConvertor cc = new CurrencyConvertor(nativeCurrency, KCurrency.USD, new Time());
				for(String prop : new String[] {"dntn","price"}) {
					double dntn = MathUtils.toNum(params.get(prop));
					if (dntn!=0) {
						Double dntnusd = cc.convertES(dntn);
						params.put(prop+"usd", dntnusd); // e.g. dntnusd
					}
				}
			} catch(Throwable ex) {
				// paranoia
				Log.e("lg", ex);
			}
		}
		
		// write to log file -- off by default (Dec 2021 datalog crisis) 
		if (state.debug) {
			doLogToFile(dataspace, tag, count, params, trckId, state);
		}
		
		// write to Stat / ES
		// ...which dataspaces?
		// Multiple dataspaces: Dan A reports a significant cost to per-user dataspaces
		// -- he estimated one server per 4k ES indexes. c.f. #5403
		DataLogEvent event = new DataLogEvent(dataspace, gby, count, new String[] { tag}, params);
		if (time != null) event.setTime(time);
		if (state.debug) {
			event.debug = state.debug;
		}
		if (saveIt) {
			DataLog.count(event);
		}
		
		return event;
	}
	
	
	/**
	 * Is it a bot? works with Portal which holds the data
	 * @param ips
	 * @return
	 */
	private static String getInvalidType(List ips) {
		assert ips != null;
		if (userTypeForIPorXId==null || userTypeForIPorXIdFetched==null || userTypeForIPorXIdFetched.isBefore(new Time().minus(10, TUnit.MINUTE))) {
			//Needs to be set first -- will get caught in a loop otherwise as userTypeForIPorXId is still null
			userTypeForIPorXIdFetched = new Time();			
			FakeBrowser fb = new FakeBrowser();
			fb.setRequestMethod("GET");

			try {
				//Right now, just set to point at local. TODO read in correct endpoint from state
				String json= fb.getPage("https://portal.good-loop.com/botip/_list.json");
				Map response = (Map) WebUtils2.parseJSON(json);
				Map esres = (Map) response.get("cargo");
				List<Map> hits = Containers.asList(esres.get("hits"));
				
				userTypeForIPorXId = hits;
			}
			catch(Exception ex) {
				Log.e("lg.getInvalidType", ex);
				userTypeForIPorXId = new ArrayList(); // paranoia: keep logging fast. This will get checked again in 10 minutes
			}
		}
		//At this point, can safely assume that we have a valid list of IPs
		for (Object userIP : ips) {
			for(Map botIP : userTypeForIPorXId) {
				String badIP = (String) botIP.get("ip");
				if (userIP.equals(badIP)) return (String) botIP.get("type");
			}
		}
		return null;
	}


	static ua_parser.Parser parser;
	

	/**
	 * Add ua (user agent), user, ip.
	 * Adds nothing if this is a call from one of our servers.
	 * 
	 * @param state
	 * @param params Can be null
	 * @param trckId
	 * @return params, never null
	 */
	private static Map doLog2_addStdTrackerParams(WebRequest state, Map params, String trckId) {
		// TODO allow the caller to explicitly set some of these if they want to
		if (params==null) params = new ArrayMap();
		// Browser info
		String ua = state.getUserAgent();
		if (FakeBrowser.HONEST_USER_AGENT.equals(ua)) {
			return params; // dont add tracking params for our own server calls
		}
		params.putIfAbsent("ua", ua);
		// Replace $user with tracking-id, and $		
		params.putIfAbsent("user", trckId);			
		// ip: $ip
		params.putIfAbsent("ip", state.getRemoteAddr());
			
		BrowserType bt = getBrowserInfo(ua);
		boolean mobile = bt.isMobile();		
		params.putIfAbsent("mbl", mobile);		
		// browser
		String browser = bt.getBrowserMake(); //+"_"+bt.getVersion(); actually no version: its more useful to group by chrome vs firefox; the version is more noise than signal.
		// And we do also store the user-agent
		// TODO s/_\d+//g in the old data to remove the version numbers
		params.putIfAbsent("browser", browser);
		// OS
		String os = bt.getOS();
		params.putIfAbsent("os", os);
		
		// what page?
		String ref = state.getReferer();
		if (ref==null) ref = state.get("site"); // DfP hack
		// remove some gumpf (UTM codes)
		String cref = WebUtils2.cleanUp(ref);
		if (cref != null) {
			params.putIfAbsent("url", cref);
			// domain (e.g. sodash.com) & host (e.g. www.sodash.com)				
			params.putIfAbsent("domain", WebUtils2.getDomain(cref)); 
			// host is the one to use!
			params.putIfAbsent("host", WebUtils2.getHost(cref)); // matches publisher in adverts
		}
		return params;
	}

	
	/**
	 * Uses ua_parser if it can
	 * @param ua
	 * @return
	 */
	public static BrowserType getBrowserInfo(String ua) {
		BrowserType bt = new BrowserType(ua);
		// browser
		try {
			ua_parser.Parser _parser = uaParser();
			Client uac = _parser.parse(ua);			
			bt.setBrowserMake(uac.userAgent.family);
			bt.setVersion(uac.userAgent.major);
			bt.setOS(uac.os.family);
		} catch(Throwable ex) {
			Log.w("lg", ex);
		}
		return bt;
	}


	static Parser uaParser() throws IOException {
		// The constructor loads files, so lets stash one copy and reuse it.
		// Thread safety: not sure!
		if (parser==null) {
			parser = new Parser();
		}
		return parser;
	}


	/**
	 * HACK screen off our IPs and test sites
	 * 
	 * TODO instead do this by User, and have a no-log parameter in the advert
	 * 
	 * @param dataspace2
	 * @param tag2
	 * @param params2
	 * @return
	 */
	private static boolean accept(Dataspace dataspace, String tag, Map params) {
		KServerType stype = AppUtils.getServerType(null);
		// only screen our IPs out of production
		if (stype != KServerType.PRODUCTION) 
		{
			return true;
		}
		// allow all non gl through??
		if ( ! "gl".equals(dataspace.toString())) return true;
		Object ip = params.get("ip");
		List<String> ips = Containers.list(ip);		
		if (OUR_IPS != null && ! Collections.disjoint(OUR_IPS, ips)) {
			Log.d("lg", "skip ip "+ip+" event: "+tag+params);
			return false;
		}
		if ("good-loop.com".equals(params.get("host"))) {
			String url = (String) params.get("url");
			// Do track the marketing site, esp live demo and landing-page ad-player
			// but otherwise no GL sites 
			if (url!=null) {
				if (url.contains("live-demo")) return true;
				if (url.contains("//www.good-loop.com")) return true;
				if (url.contains("//good-loop.com")) return true;
				if (url.contains("//as.good-loop.com")) return true;
			}
			Log.d("lg", "skip url "+url+" event: "+tag+params);
			return false;
		}
		return true;
	}

	static DataLogConfig DataLogConfig = Dep.get(DataLogConfig.class);
	/**
	 * Rarely null.
	 */
	static final List<String> OUR_IPS = Dep.get(DataLogConfig.class).ourSkippedIPs;
	
	private static void doLogToFile(Dataspace dataspace, String tag, double count, Map params, String trckId, WebRequest state) {
		String msg = params == null? "" : Printer.toString(params, ", ", ": ");
		if (count != 1) msg += "\tcount:"+count;
		msg += "\ttracker:"+trckId+"\tref:"+state.getReferer()+"\tip:"+state.getRemoteAddr();
		// Guard against giant objects getting put into log, which is almost
		// certainly a careless error
		if (msg.length() > Log.MAX_LENGTH) {
			msg = StrUtils.ellipsize(msg, Log.MAX_LENGTH);
//			error = StrUtils.ellipsize(msg, 140)+" is too long for Log!";
		}
		// chop #tag down to tag (including embedded #, as in tr_#myapp)
		tag = tag.replace("#", "");
		tag = dataspace+"."+tag;
		// Note: LogFile will force the report onto one line by converting [\r\n] to " "
		// Add in referer and IP
		// Tab-separating elements on this line is useless, as Report.toString() will immediately convert \t to space.
		String msgPlus = msg+" ENDMSG "+state.getReferer()+" "+state.getRemoteAddr();
		
		// error or warning?
		if (tag.contains("error")) {
			// Reduced to "warning" so we don't spam LogStash alert emails.
			Log.w(tag, msgPlus); 
		} else if (tag.contains("warning")) {
			Log.w(tag, msgPlus);
		} else {
			// normal case
			Log.i(tag, msgPlus);
		}
	}

}
