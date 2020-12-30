package com.winterwell.web.app;

import java.io.IOException;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.eclipse.jetty.util.ajax.JSON;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.winterwell.data.AThing;
import com.winterwell.data.KStatus;
import com.winterwell.depot.IInit;
import com.winterwell.es.ESPath;
import com.winterwell.es.IESRouter;
import com.winterwell.es.client.DeleteRequestBuilder;
import com.winterwell.es.client.ESHit;
import com.winterwell.es.client.ESHttpClient;
import com.winterwell.es.client.IESResponse;
import com.winterwell.es.client.KRefresh;
import com.winterwell.es.client.SearchRequestBuilder;
import com.winterwell.es.client.SearchResponse;
import com.winterwell.es.client.query.BoolQueryBuilder;
import com.winterwell.es.client.query.ESQueryBuilder;
import com.winterwell.es.client.query.ESQueryBuilders;
import com.winterwell.es.client.sort.KSortOrder;
import com.winterwell.es.client.sort.Sort;
import com.winterwell.gson.FlexiGson;
import com.winterwell.gson.Gson;
import com.winterwell.nlp.query.SearchQuery;
import com.winterwell.utils.Dep;
import com.winterwell.utils.ReflectionUtils;
import com.winterwell.utils.StrUtils;
import com.winterwell.utils.Utils;
import com.winterwell.utils.WrappedException;
import com.winterwell.utils.containers.ArrayMap;
import com.winterwell.utils.containers.Containers;
import com.winterwell.utils.io.CSVSpec;
import com.winterwell.utils.io.CSVWriter;
import com.winterwell.utils.log.Log;
import com.winterwell.utils.time.Period;
import com.winterwell.utils.time.Time;
import com.winterwell.utils.web.SimpleJson;
import com.winterwell.utils.web.WebUtils;
import com.winterwell.utils.web.WebUtils2;
import com.winterwell.web.WebEx;
import com.winterwell.web.ajax.JThing;
import com.winterwell.web.ajax.JsonResponse;
import com.winterwell.web.app.WebRequest.KResponseType;
import com.winterwell.web.data.IHasXId;
import com.winterwell.web.data.XId;
import com.winterwell.web.fields.Checkbox;
import com.winterwell.web.fields.IntField;
import com.winterwell.web.fields.SField;
import com.winterwell.youagain.client.AuthToken;
import com.winterwell.youagain.client.NoAuthException;
import com.winterwell.youagain.client.YouAgainClient;
/**
 * TODO security checks
 *  
 * @author daniel
 *
 * @param <T>
 */
public abstract class CrudServlet<T> implements IServlet {

	protected String[] prefixFields = new String[] {"name"};
	
	protected boolean dataspaceFromPath;
	public static final String ACTION_PUBLISH = "publish";
	public static final String ACTION_NEW = "new";
	/**
	 * get, or create if absent
	 */
	public static final String ACTION_GETORNEW = "getornew";
	public static final String ACTION_SAVE = "save";

	public CrudServlet(Class<T> type) {
		this(type, Dep.get(IESRouter.class));
	}
	
	
	public CrudServlet(Class<T> type, IESRouter esRouter) {
		this.type = type;
		this.esRouter = esRouter;
		Utils.check4null(type, esRouter);
	}

	protected JThing<T> doDiscardEdits(WebRequest state) {
		ESPath path = esRouter.getPath(dataspace, type, getId(state), KStatus.DRAFT);
		DeleteRequestBuilder del = es.prepareDelete(path.index(), path.type, path.id);
		IESResponse ok = del.get().check();		
		getThing(state);
		return jthing;
	}

	public void process(WebRequest state) throws Exception {
		// CORS??
		WebUtils2.CORS(state, false);
		
		// dataspace?
		if (dataspaceFromPath) {
			String ds = state.getSlugBits(1);
			setDataspace(ds);
		}
		
		doSecurityCheck(state);
		
		// list?
		String slug = state.getSlug();
		if (slug.endsWith("/_list") || LIST_SLUG.equals(slug)) {
			doList(state);
			return;
		}
		if (slug.endsWith("/_stats") || "_stats".equals(slug)) {
			doStats(state);
			return;
		}
		
		// crud?
		if (state.getAction() != null) {
			// do it
			doAction(state);
		}						

		getThingStateOrDB(state);

		// return json?		
		if (jthing != null) {						
			// privacy: potentially filter some stuff from the json!
			cleanse(jthing, state);
			// augment?
			if (augmentFlag) {
				augment(jthing, state);				
			}
			String json = jthing.string();
			JsonResponse output = new JsonResponse(state).setCargoJson(json);			
			WebUtils2.sendJson(output, state);
			return;
		}
		// no object...
		// return blank / messages
		if (state.getAction()==null) {
			// no thing? return a 404
			ESPath path = getPath(state);
			throw new WebEx.E404(state.getRequestUrl(), "Not found: "+path);
		}
		JsonResponse output = new JsonResponse(state);
		WebUtils2.sendJson(output, state);
	}
	
	protected void doSecurityCheck(WebRequest state) throws SecurityException {
		YouAgainClient ya = Dep.get(YouAgainClient.class);
		ReflectionUtils.setPrivateField(state, "debug", true); // FIXME
		List<AuthToken> tokens = ya.getAuthTokens(state);
		if (state.getAction() == null) {
			return;
		}
		// logged in?					
		if (Utils.isEmpty(tokens)) {
			Log.w("crud", "No auth tokens for "+this+" "+state+" All JWT: "+ya.getAllJWTTokens(state));
			throw new NoAuthException(state);
		}
	}

	/**
	 * ES takes 1 second to update by default, so save actions within a second could
	 * cause an issue. Allow an extra second to be safe.
	 */
	static final Cache<String,Boolean> ANTI_OVERLAPPING_EDITS_CACHE = CacheBuilder.newBuilder()
			.expireAfterWrite(2, TimeUnit.SECONDS)
			.build();
	private static final Checkbox ALLOW_OVERLAPPING_EDITS = new Checkbox("allowOverlappingEdits");
	
	protected void doAction(WebRequest state) throws Exception {
		// Defend against repeat calls from the front end
		doAction2_blockRepeats(state);		
		// make a new thing?
		// ...only if absent?
		if (state.actionIs(ACTION_GETORNEW)) {
			JThing<T> thing = getThingFromDB(state);
			if (thing != null) {
				jthing = thing;
				return;
			}
			// absent => new
			state.setAction(ACTION_NEW);
		}
		// ...new
		if (state.actionIs(ACTION_NEW)) {
			// add is "special" as the only request that doesn't need an id
			String id = getId(state);
			jthing = doNew(state, id);
			jthing.setType(type);
		}
		
		// save?
		if (state.actionIs(ACTION_SAVE) || state.actionIs(ACTION_NEW)) {
			doSave(state);
			return;
		}
		// copy / save-as?
		if (state.actionIs("copy")) {
			doCopy(state);
			return;
		}
		if (state.actionIs("discard-edits") || state.actionIs("discardEdits")) {
			jthing = doDiscardEdits(state);
			return;
		}
		if (state.actionIs("delete")) {
			jthing = doDelete(state);
			return;
		}
		// publish?
		if (state.actionIs(ACTION_PUBLISH)) {
			jthing = doPublish(state);
			assert jthing.string().contains(KStatus.PUBLISHED.toString()) : jthing;
			return;
		}
		if (state.actionIs("unpublish")) {
			jthing = doUnPublish(state);
			return;
		}
		if (state.actionIs("archive")) {
			jthing = doArchive(state);
			return;
		}
	}


	protected void doAction2_blockRepeats(WebRequest state) {
		if (state.get(ALLOW_OVERLAPPING_EDITS)) {
			return;
		}
		String ckey = doAction2_blockRepeats2_actionId(state);
		Log.d(LOGTAG(), "Anti overlap key: "+ckey);
		if (ANTI_OVERLAPPING_EDITS_CACHE.getIfPresent(ckey)!=null) {
			throw new WebEx.E409Conflict("Duplicate request within 2 seconds. Blocked for edit safety. "+state
					+" Note: this behaviour could be switched off via "+ALLOW_OVERLAPPING_EDITS);
		}		
		ANTI_OVERLAPPING_EDITS_CACHE.put(ckey, true);
	}

	/**
	 * @param state
	 * @return the id for this action -- this determines what counts as identical (and hence will be blocked)
	 */
	protected String doAction2_blockRepeats2_actionId(WebRequest state) {
		Map<String, Object> pmap = state.getParameterMap();
		String ckey = state.getAction()+FlexiGson.toJSON(pmap);
		return ckey;
	}


	/**
	 * Copy / save-as -- this is almost the same as save. 
	 * But it can clear some values which should not be copied -- e.g. external linking ids.
	 * @param state
	 */
	protected void doCopy(WebRequest state) {
		// clear linking ids
		T thing = getThing(state);
		if (thing instanceof IHasXId) {
			try {
				((IHasXId) thing).setAka(new ArrayList());
			} catch(UnsupportedOperationException ex) {
				// oh well
			}
		}
		// save 
		doSave(state);
	}



	/**
	 * Delete from draft and published!!
	 * @param state
	 * @return
	 */
	protected JThing<T> doDelete(WebRequest state) {
		String id = getId(state);
		// try to copy to trash
		try {
			JThing<T> thing = getThingFromDB(state);
			if (thing != null) {
				ESPath path = esRouter.getPath(dataspace, type, id, KStatus.TRASH);
				AppUtils.doSaveEdit2(path, thing, state, false);
			}
		} catch(Throwable ex) {
			Log.e(LOGTAG(), "copy to trash failed: "+state+" -> "+ex);
		}
		for(KStatus s : KStatus.main()) {
			if (s==KStatus.TRASH) continue;
			ESPath path = esRouter.getPath(dataspace,type, id, s);
			DeleteRequestBuilder del = es.prepareDelete(path.index(), path.type, path.id);
			del.setRefresh("wait_for");
			IESResponse ok = del.get().check();			
		}
		return null;
	}

	/**
	 * 
	 * @param state
	 * @return thing or null
	 * @throws TODO WebEx.E403
	 */
	protected JThing<T> getThingFromDB(WebRequest state) throws WebEx.E403 {
		ESPath path = getPath(state);
		KStatus status = state.get(AppUtils.STATUS);
		// fetch from DB
		T obj = AppUtils.get(path, type);		
		if (obj!=null) {
			JThing thing = new JThing().setType(type).setJava(obj);
			return thing;
		}
		
		// Not found :(
		// was version=draft?
		if (status == KStatus.DRAFT) {			
			// Try for the published version
			// NB: all published should be in draft, so this should be redundant
			// ?? maybe refactor to use a getThinfFromDB2(state, status) method? But beware CharityServlet has overriden this
			WebRequest state2 = new WebRequest(state.request, state.response);
			state2.put(AppUtils.STATUS, KStatus.PUBLISHED);
			JThing<T> pubThing = getThingFromDB(state2);
			return pubThing;
		}
		// Was status unset? Maybe the published version got archived?
		if (status == null) {			
			// Try for an archived version
			WebRequest state2 = new WebRequest(state.request, state.response);
			state2.put(AppUtils.STATUS, KStatus.ARCHIVED);
			JThing<T> pubThing = getThingFromDB(state2);
			return pubThing;
		}		
		return null;
	}

	/**
	 * Use getId() to make an ESPath.
	 * NB: the path depends on status - defaulting to published 
	 * @param state
	 * @return
	 */
	protected ESPath getPath(WebRequest state) {
		assert state != null;
		String id = getId(state);
		if ("list".equals(id)) {
			throw new WebEx.E400(
					state.getRequestUrl(),
					"Bad input: 'list' was interpreted as an ID -- use /_list.json to retrieve a list.");
		}
		KStatus status = state.get(AppUtils.STATUS, KStatus.PUBLISHED);
		ESPath path = esRouter.getPath(dataspace,type, id, status);
		return path;
	}

	/**
	 * Make a new thing. The state will often contain json info for this.
	 * @param state
	 * @param id Can be null ??It may be best for the front-end to normally provide IDs. 
	 * @return
	 */
	protected JThing<T> doNew(WebRequest state, String id) {
		String json = getJson(state);
		T item;
		if (json == null) {
			try {
				item = type.newInstance();
			} catch (InstantiationException | IllegalAccessException e) {
				throw Utils.runtime(e);
			}
		} else {
			// from front end json
			item = Dep.get(Gson.class).fromJson(json, type);
			// TODO safety check ID! Otherwise someone could hack your object with a new object
//			idFromJson = AppUtils.getItemId(item);
		}
		// ID
		if (id != null) {
			if (item instanceof AThing) {
				((AThing) item).setId(id);
			}
			// else ??
		}
		if (item instanceof IInit) {
			((IInit) item).init();
		}
		return new JThing().setJava(item);
	}

	protected ESHttpClient es = Dep.get(ESHttpClient.class);
	protected final Class<T> type;
	protected JThing<T> jthing;

	protected final IESRouter esRouter;
	
	/**
	 * The focal thing's ID.
	 * This might be newly minted for a new thing
	 */
	private String _id;
	
	/**
	 * Optional support for dataspace based data access.
	 * Call {@link #setDataspace(CharSequence)} to use this
	 */
	// NB: the Dataspace class is not in the scope of this project, hence the super-class CharSequence
	protected CharSequence dataspace = null;
	
	public CrudServlet setDataspace(CharSequence dataspace) {
		this.dataspace = dataspace;
		return this;
	}
	
	/**
	 * suggested: date-desc
	 */
	protected String defaultSort;
	/**
	 * If true, run all items through {@link #augment(JThing, WebRequest)}
	 */
	protected boolean augmentFlag;
	
	public static final SField SORT = new SField("sort");
	public static final String LIST_SLUG =  "_list";
	private static final IntField SIZE = new IntField("size");
	public static final String ALL = "all";

	protected final JThing<T> doPublish(WebRequest state) throws Exception {
		// For publish, let's force the update.
		return doPublish(state, KRefresh.TRUE, false);
	}
	
	protected JThing<T> doPublish(WebRequest state, KRefresh forceRefresh, boolean deleteDraft) throws Exception {		
		String id = getId(state);
		Log.d("crud", "doPublish "+id+" by "+state.getUserId()+" "+state+" deleteDraft: "+deleteDraft);
		Utils.check4null(id); 
		getThingStateOrDB(state);
		return doPublish2(dataspace, jthing, forceRefresh, deleteDraft, id, state);
	}


	/**
	 * Idempotent -- sets the jthing field and reuses that. Get from state or DB
	 * @param state
	 * @return
	 */
	protected JThing<T> getThingStateOrDB(WebRequest state) {
		if (jthing!=null) {
			return jthing;
		}		
		// from request?
		getThing(state);
		// from DB?
		if (jthing==null) {
			jthing = getThingFromDB(state);
		}
		return jthing;
	}



	/**
	 * @param _jthing 
	 * @param forceRefresh
	 * @param deleteDraft
	 * @param id
	 * @return
	 */
	protected JThing<T> doPublish2(CharSequence dataspace, JThing<T> _jthing, 
			KRefresh forceRefresh, boolean deleteDraft, String id, WebRequest stateIgnored) 
	{		
		doBeforeSaveOrPublish(_jthing, stateIgnored);
		
		// id must match
		if (_jthing.java() instanceof AThing) {
			String thingId = ((AThing) _jthing.java()).getId();
			if (thingId==null || ACTION_NEW.equals(thingId)) {
				_jthing.put("id", id);
			} else if ( ! thingId.equals(id)) {
				throw new IllegalStateException("ID mismatch remote: "+thingId+" vs local: "+id);
			}
		}		
		// ES paths
		ESPath draftPath = esRouter.getPath(dataspace, type, id, KStatus.DRAFT);
		ESPath publishPath = esRouter.getPath(dataspace, type, id, KStatus.PUBLISHED);
		ESPath archivedPath = esRouter.getPath(dataspace,type, id, KStatus.ARCHIVED);
		// do it
		JThing obj = AppUtils.doPublish(_jthing, draftPath, publishPath, archivedPath, forceRefresh, deleteDraft);
		return obj.setType(type);
	}
	
	/**
	 * Sets lastModified by default - override to add custom logic.
	 * This is called by both {@link #doSave(WebRequest)} and {@link #doPublish(WebRequest)}
	 * @param _jthing
	 * @param stateIgnored
	 */
	protected void doBeforeSaveOrPublish(JThing<T> _jthing, WebRequest stateIgnored) {
		// set last modified
		if (_jthing.java() instanceof AThing) {
			AThing ting = (AThing) _jthing.java();
			ting.setLastModified(new Time());
		}
	}


	protected JThing<T> doUnPublish(WebRequest state) {
		KStatus status = KStatus.DRAFT;
		return doUnPublish2(state, status);
	}
	
	private JThing<T> doUnPublish2(WebRequest state, KStatus status) {
		assert status!=null;
		String id = getId(state);
		Log.d("crud."+status, "doUnPublish "+id+" by "+state.getUserId()+" "+state);
		Utils.check4null(id); 
		getThingStateOrDB(state);

		ESPath draftPath = esRouter.getPath(dataspace,type, id, status);
		ESPath publishPath = esRouter.getPath(dataspace,type, id, KStatus.PUBLISHED);
		
		AppUtils.doUnPublish(jthing, draftPath, publishPath, status);
		
		state.addMessage(id+" has been moved to "+status.toString().toLowerCase());
		return jthing;
	}
	
	protected JThing<T> doArchive(WebRequest state) {
		return doUnPublish2(state, KStatus.ARCHIVED);
	}

	/**
	 * `new` gets turned into userid + nonce
	 * @param state
	 * @return 
	 */
	protected String getId(WebRequest state) {
		if (_id!=null) return _id;
		// Beware if ID can have a / in it!
		String slug = state.getSlug();
		String[] slugBits = state.getSlugBits();
		
		String sid = slugBits[slugBits.length - 1]; 
		// NB: slug-bit-0 is the servlet, slug-bit-1 might be the ID - or the dataspace for e.g. SegmentServlet
		_id = getId2(state, sid);
		return _id;
	}

	protected String getId2(WebRequest state, String sid) {
		if (ACTION_NEW.equals(sid)) {
			String nicestart = StrUtils.toCanonical(
					Utils.or(state.getUserId(), state.get("name"), type.getSimpleName()).toString()
					).replace(' ', '_');
			sid = nicestart+"_"+Utils.getRandomString(8);
			// avoid ad, 'cos adblockers dont like it!
			if (sid.startsWith("ad")) {
				sid = sid.substring(2, sid.length());
			}
		}
		return sid;
	}


	protected void doStats(WebRequest state) {
		throw new WebEx.E404(state.getRequestUrl(), "_stats not available for "+type);
	}

	/**
	 * 
	 * @param state
	 * @return for debug purposes! The results are sent back in state
	 * @throws IOException
	 */
	public final List doList(WebRequest state) throws IOException {
		Time now = new Time();
		KStatus status = state.get(AppUtils.STATUS, KStatus.DRAFT);
		String q = state.get(CommonFields.Q);
		String prefix = state.get("prefix");
		String sort = state.get(SORT, defaultSort);		
		int size = state.get(SIZE, 1000);
		Period period = CommonFields.getPeriod(state);
		
		SearchResponse sr = doList2(q, prefix, status, sort, size, period, state);
		
//		Map<String, Object> jobj = sr.getParsedJson();
		// Let's deal with ESHit and JThings
		List<ESHit<T>> _hits = sr.getHits(type);

		// init java objects (this acts as a safety check on bad data)
		_hits = init(_hits);
		
		// TODO dedupe can cause the total reported to be off
		List<ESHit<T>> hits2 = doList3_source_dedupe(status, _hits);
		
		// HACK: avoid created = during load just now
		for(ESHit<T> hit : hits2) {
			if ( ! hit.getJThing().isa(AThing.class)) continue;
			AThing at = (AThing) hit.getJThing().java();
			if (at.getCreated()!=null && at.getCreated().isAfter(now)) {
				at.setCreated(null);
			}
		}
		
		// sanitise for privacy
		for (ESHit<T> esHit : hits2) {
			cleanse(esHit.getJThing(), state);
		}		
		
		// HACK: send back csv?
		if (state.getResponseType() == KResponseType.csv) {
			doSendCsv(state, hits2);
			return hits2;
		}
		
		// augment?
		if (augmentFlag) {
			for (ESHit<T> h : hits2) {
				augment(h.getJThing(), state);
			}
		}
		// put together the json response	
		long total = sr.getTotal();
		List<Map> items = Containers.apply(hits2, h -> h.getJThing().map());
		String json = gson().toJson(
				new ArrayMap(
					"hits", items, 
					"total", total
				));
		JsonResponse output = new JsonResponse(state).setCargoJson(json);
		// ...send
		WebUtils2.sendJson(output, state);
		return hits2;
	}
	
	/**
	 * Run results through deserialisation to catch any bugs.
	 * Bugs are logged, but they do _not_ disrupt returning the rest of the list.
	 * This is so one bad data item can't block an API service.
	 * 
	 * @param _hits
	 * @return hits (filtered for no-exceptions)
	 */
	private List<ESHit<T>> init(List<ESHit<T>> _hits) {
		List<ESHit<T>> hits = new ArrayList(_hits.size());
		for (ESHit<T> h : _hits) {
			try {
				T pojo = h.getJThing().java();
				if (pojo instanceof IInit) {
					((IInit) pojo).init();
				}
				hits.add(h);
			} catch(Throwable ex) {
				// log, swallow, and carry on
				Log.e("crud", new WrappedException("cause: "+h, ex));
			}
		}
		return hits;		
	}


	/**
	 * Override to do anything. 
	 * @param jThing Modify this if you want
	 * @param state
	 * @see #augmentFlag
	 */
	protected void augment(JThing<T> jThing, WebRequest state) {
		// no-op by default
	}


	/**
	 * Do the search! 
	 * 
	 * Does NOT dedupe (eg multiple copies with diff status) or security cleanse.
	 * @param prefix 
	 * @param num 
	 */
	public final SearchResponse doList2(String q, String prefix, KStatus status, String sort, int size, Period period, WebRequest stateOrNull) {
		// copied from SoGive SearchServlet
		// TODO refactor to use makeESFilterFromSearchQuery
		SearchRequestBuilder s = new SearchRequestBuilder(es);
		/// which index? draft (which should include copies of published) by default
		doList3_setIndex(status, s);
		
		// query
		ESQueryBuilder qb = doList3_ESquery(q, prefix, period, stateOrNull);

		if (qb!=null) s.setQuery(qb);
				
		// Sort e.g. sort=date-desc for most recent first
		if (sort!=null) {
			// split on comma to support hierarchical sorting, e.g. priority then date
			String[] sorts = sort.split(",");
			for (String sortBit : sorts) {
				// split into field and up/down order
				KSortOrder order = KSortOrder.asc;
				if (sortBit.endsWith("-desc")) {
					sortBit = sortBit.substring(0, sortBit.length()-5);
					order = KSortOrder.desc;
				} else if (sortBit.endsWith("-asc")) {
					sortBit = sortBit.substring(0, sortBit.length()-4);
				}
				Sort _sort = new Sort().setField(sortBit).setOrder(order);			
				s.addSort(_sort);				
			}
		}
		
		// TODO paging!
		s.setSize(size);
		s.setDebug(true);

		// Call the DB
		SearchResponse sr = s.get();		
		return sr;
	}


	protected void doList3_setIndex(KStatus status, SearchRequestBuilder s) {
		switch(status) {
		case ALL_BAR_TRASH:
			s.setIndices(
					esRouter.getPath(dataspace, type, null, KStatus.PUBLISHED).index(),
					esRouter.getPath(dataspace, type, null, KStatus.DRAFT).index(),
					esRouter.getPath(dataspace, type, null, KStatus.ARCHIVED).index()
				);
			break;
		case PUB_OR_ARC:
			s.setIndices(
					esRouter.getPath(dataspace, type, null, KStatus.PUBLISHED).index(),
					esRouter.getPath(dataspace, type, null, KStatus.ARCHIVED).index()
				);
			break;
		case PUB_OR_DRAFT:
			s.setIndices(
					esRouter.getPath(dataspace, type, null, KStatus.PUBLISHED).index(),
					esRouter.getPath(dataspace, type, null, KStatus.DRAFT).index()
				);
			break;
		default:
			// normal
			s.setIndex(esRouter.getPath(dataspace, type, null, status).index());
		}
	}


	/**
	 * 
	 * @param q
	 * @param prefix
	 * @param period
	 * @param stateOrNull
	 * @return can this be null?? best to guard against nulls 
	 */
	protected ESQueryBuilder doList3_ESquery(String q, String prefix, Period period, WebRequest stateOrNull) {
		ESQueryBuilder qb = null;
		// HACK no key:value in a prefix query
		if (prefix!=null && prefix.indexOf(':') != -1) {
			if (q==null) q = prefix;
			else q = "("+q+") AND "+prefix;
			prefix = null;
		}
		if (prefix != null) {
			// NB: not factored into its own method as it edits a few variables			
			// Hack: convert punctuation into spaces, as ES would otherwise say query:"P&G" !~ name:"P&G"
			String cprefix = StrUtils.toCanonical(prefix);
			// Hack: Prefix should be one word. If 2 are sent -- turn it into a query + prefix
			int spi = cprefix.lastIndexOf(' ');
			if (spi != -1) {
				assert cprefix.equals(cprefix.trim()) : "untrimmed?! "+cprefix;
				String qbit = cprefix.substring(0, spi);
				if (q==null) q = qbit;
				else q = "("+q+") AND "+qbit;
				cprefix = cprefix.substring(spi+1);
			}
			// prefix is on a field(s) -- we use name by default
			BoolQueryBuilder prefixESQ = ESQueryBuilders.boolQuery();
			for(String field : prefixFields) {
				prefixESQ.should(ESQueryBuilders.prefixQuery(field, cprefix));
			}
			// also allow general search on the prefix word -- so that prefix is not more restrictive than q
			ESQueryBuilder searchForPrefix = ESQueryBuilders.simpleQueryStringQuery(cprefix);
			prefixESQ.should(searchForPrefix);
			
			prefixESQ.minimumNumberShouldMatch(1);
			assert qb == null;
			qb = prefixESQ;
		} //./prefix
		
		if ( q != null) {
			// convert "me" to specific IDs
			if (Pattern.compile("\\bme\\b").matcher(q).find()) {
				if (stateOrNull==null) {
					throw new NullPointerException("`me` requires webstate to resolve who: "+q);
				}
				YouAgainClient ya = Dep.get(YouAgainClient.class);
				List<AuthToken> tokens = ya.getAuthTokens(stateOrNull);
				StringBuilder mes = new StringBuilder();
				for (AuthToken authToken : tokens) {
					mes.append(authToken.xid+" OR ");
				}
				if (mes.length()==0) {
					Log.w("crud", "No mes "+q+" "+stateOrNull);
					mes.append("ANON OR " ); // fail - WTF? How come no logins?!
				}
				StrUtils.pop(mes, 4);
				q = q.replaceAll("\\bme\\b", mes.toString());
			}
			// TODO match on all?
			// HACK strip out unset
			if (q.contains(":unset")) {
				Matcher m = Pattern.compile("(\\w+):unset").matcher(q);
				m.find();
				String prop = m.group(1);
				String q2 = m.replaceAll("").trim();
				q = q2;
				ESQueryBuilder setFilter = ESQueryBuilders.existsQuery(prop);
				qb = ESQueryBuilders.boolQuery().mustNot(setFilter);
			}	
			// Add the Query!
			if ( ! Utils.isBlank(q) && ! ALL.equalsIgnoreCase(q)) { // ??make all case-sensitive??
				SearchQuery sq = new SearchQuery(q);
				BoolQueryBuilder esq = AppUtils.makeESFilterFromSearchQuery(sq, null, null);			
				qb = ESQueryBuilders.must(qb, esq);
			}
		} // ./q		
		
		if (period != null) {
			// option for modified or another date field?
			String timeField = stateOrNull==null? null : stateOrNull.get("period");
			if (timeField==null) timeField = "created";
			ESQueryBuilder qperiod = ESQueryBuilders.dateRangeQuery(timeField, period.first, period.second);
			qb = ESQueryBuilders.must(qb, qperiod);
		}
		
		// NB: exq can be null for ALL
		ESQueryBuilder exq = doList4_ESquery_custom(stateOrNull);
		qb = ESQueryBuilders.must(qb, exq);
		return qb;
	}


/**
 * 		// If user requests ALL_BAR_TRASH, they want to see draft versions of items which have been edited
		// So when de-duping, give priority to entries from .draft indices where the object is status: DRAFT

 * @param status
 * @param hits
 * @return unique hits, source
 */
	private List<ESHit<T>> doList3_source_dedupe(KStatus status, List<ESHit<T>> hits) {
		if ( ! KStatus.isMultiIndex(status)) {
			// One index = no deduping necessary.
//			ArrayList<Object> hits2 = Containers.apply(hits, h -> h.get("_source"));
			return hits;
		}
		List<ESHit<T>> hits2 = new ArrayList<>();
		// de-dupe
//		KStatus preferredStatus = status==KStatus.ALL_BAR_TRASH? KStatus.DRAFT : KStatus.PUB_OR_ARC;
		List<Object> idOrder = new ArrayList<Object>(); // original ordering
		Map<Object, ESHit<T>> things = new HashMap<>(); // to hold "expected" version of each hit
		
		for (ESHit<T> h : hits) {
			// pull out the actual object from the hit (NB: may be Map or AThing)
//			Object hit = h.getSource();
			Object id = getIdFromHit(h);			
			// First time we've seen this object? Save it.
			if ( ! things.containsKey(id)) {
				idOrder.add(id);
				things.put(id, h);
				continue;
			}
			// Which copy to keep?
			// Is this an object from .draft with non-published status? Overwrite the previous entry.
			Object index = h.getIndex();
			KStatus hitStatus = KStatus.valueOf(getStatus(h.getJThing()));
			if (status == KStatus.ALL_BAR_TRASH) {
				// prefer draft
				if (index != null && index.toString().contains(".draft")) {
					things.put(id, h);	
				}
			} else {
				// prefer published over archived
				if (KStatus.PUBLISHED == hitStatus) {
					things.put(id, h);	
				}										
			}
		}
		// Put the deduped hits in the list in their original order.
		for (Object id : idOrder) {
			if (things.containsKey(id)) {
				hits2.add(things.get(id));
			}
		}
		return hits2;
	}


	/**
	 * convenience
	 * @return
	 */
	protected Gson gson() {
		return Dep.get(Gson.class);
	}



	/**
	 * Remove sensitive details for privacy - override to do anything!
	 * 
	 * This is (currently) only used with the _list endpoint!
	 * TODO expand to get-by-id requests too -- but carefully, as there's more risk of breaking stuff.
	 * 
	 * @param hits
	 * @param state
	 * @return hits
	 */
	protected void cleanse(JThing<T> thing, WebRequest state) {
	}


	private String getStatus(JThing h) {
		Object s;
		if (h.java() instanceof AThing) {
			s = ((AThing) h.java()).getStatus();
		} else {
			s = h.map().get("status");
		}
		return String.valueOf(s);
	}



	/**
	 * 
	 * @param hit Map from ES, or AThing
	 * @return
	 */
	private Object getIdFromHit(ESHit<T> hit) {
		Object id = hit.getJThing().map().get("id");
		return id;
	}



	protected void doSendCsv(WebRequest state, List<ESHit<T>> hits2) {
		StringWriter sout = new StringWriter();
		CSVWriter w = new CSVWriter(sout, new CSVSpec());
		
		Json2Csv j2c = new Json2Csv(w);		
		// TODO!
		
		// send
		String csv = sout.toString();
		state.getResponse().setContentType(WebUtils.MIME_TYPE_CSV); // + utf8??
		WebUtils2.sendText(csv, state.getResponse());
	}
	




	/**
	 * Override to add custom filtering.
	 * @param state
	 * @return null or a query. This is ANDed to the normal query.
	 */
	protected ESQueryBuilder doList4_ESquery_custom(WebRequest state) {
		return null;
	}


	/**
	 * 
	 * NB: doPublish does NOT save first!
	 * 
	 * NB: Uses AppUtils#doSaveEdit2(ESPath, JThing, WebRequest, boolean) to do a *merge* into ES.
	 * So this will not remove parts of a document (unless you provide an over-write value).
	 * 
	 * Why use merge?
	 * This allows for partial editors (e.g. edit the budget of an advert), and reduces the collision
	 * issues with multiple online editors.
	 * 
	 * 
	 * @param state
	 */
	protected void doSave(WebRequest state) {		
		XId user = state.getUserId(); // TODO save who did the edit + audit trail
		
		String diff = state.get("diff");
		if (diff!=null) {
			// TODO Instead of applying the diff here, why not save the diff directly using an ES update? That would allow for multiple editors
			Object jdiff = JSON.parse(diff);
			List<Map> diffs = Containers.asList(jdiff);
			JThing<T> oldThing = getThingFromDB(state);
			applyDiff(oldThing, diffs);
			jthing = oldThing; // NB: getThing(state) below will now return the diff-modified oldThing
		}
		
		T thing = getThing(state);
		assert thing != null : "null thing?! "+state;
		
		// This has probably been done already in getThing(), but harmless to repeat
		// run the object through Java, to trigger IInit
		T pojo = jthing.java();
		
		doBeforeSaveOrPublish(jthing, state);
		
		// add security?
		doSave2_setSecurity(state, pojo);
		
		{	// update
			String id = getId(state);
			assert id != null : "No id? cant save! "+state; 
			ESPath path = esRouter.getPath(dataspace,type, id, KStatus.DRAFT);
			AppUtils.doSaveEdit(path, jthing, state);
			Log.d("crud", "doSave "+path+" by "+state.getUserId()+" "+state+" "+jthing.string());
		}
	}
	

	/**
	 * 
	 * @param room
	 * @param diffs Each diff is {op:replace, path:/foo/bar, value:v}
	 * TODO other ops 
	 * @return
	 */
	void applyDiff(JThing<T> room, List<Map> diffs) {			
		if (diffs.isEmpty()) {
			return;
		}
		Map<String, Object> thingMap = new HashMap(room.map());
		for (Map diff : diffs) {
			String op = (String) diff.get("op"); // replace
			String path = (String) diff.get("path");
			Object value = diff.get("value");
			// NB: drop the leading / on path
			String[] bits = path.substring(1).split("/");
			SimpleJson.set(thingMap, value, bits);
		}
		room.setMap(thingMap);
	}

	/**
	 * Override to implement!
	 * @param state
	 * @param pojo
	 */
	protected void doSave2_setSecurity(WebRequest state, T pojo) {
		// TODO Auto-generated method stub		
	}



	/**
	 * Get from field or state. Does NOT call the database.
	 * @param state
	 * @return
	 * @see #getThingFromDB(WebRequest)
	 */
	protected T getThing(WebRequest state) {
		if (jthing!=null) {
			return jthing.java();
		}
		String json = getJson(state);
		if (json==null) {
			return null;
		}
		jthing = new JThing(json).setType(type);
		return jthing.java();
	}

	protected String getJson(WebRequest state) {
		return state.get(new SField(AppUtils.ITEM.getName()));
	}
	
}
