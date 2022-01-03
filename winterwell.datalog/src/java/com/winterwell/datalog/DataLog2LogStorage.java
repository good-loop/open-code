package com.winterwell.datalog;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.FileLock;
import java.util.Collection;
import java.util.Iterator;
import java.util.Map;
import java.util.regex.Pattern;

import com.winterwell.datalog.DataLog.KInterpolate;
import com.winterwell.depot.Depot;
import com.winterwell.depot.Desc;
import com.winterwell.depot.MetaData;
import com.winterwell.maths.stats.distributions.d1.IDistribution1D;
import com.winterwell.maths.timeseries.IDataStream;
import com.winterwell.utils.TodoException;
import com.winterwell.utils.Utils;
import com.winterwell.utils.containers.Pair2;
import com.winterwell.utils.io.CSVReader;
import com.winterwell.utils.io.CSVWriter;
import com.winterwell.utils.io.FileUtils;
import com.winterwell.utils.log.Log;
import com.winterwell.utils.threads.IFuture;
import com.winterwell.utils.time.Dt;
import com.winterwell.utils.time.Period;
import com.winterwell.utils.time.Time;

/**
 * 
 * @author Daniel
 *
 */
public class DataLog2LogStorage implements IDataLogStorage {

	@Override
	@Deprecated
	public void setHistory(Map<Pair2<String, Time>, Double> tagTime2set) {
		// unsupported
	}

	public IFuture<MeanRate> getMean(Time start, Time end, String tag) {
		throw new TodoException(); // TODO
	}

	DataLogConfig config;

	public DataLog2LogStorage() {
		this(new DataLogConfig());
	}

	public DataLog2LogStorage(DataLogConfig config) {
		init(config);
	}

	@Override
	public IDataLogStorage init(DataLogConfig _config) {
		this.config = _config;
		return this;
	}

	/**
	 * Use IDataStream csv format: timestamp, tag, value.
	 * 
	 * @param period
	 * @param tag2count
	 * @param tag2mean
	 */
	@Override
	public void save(Period period, Map<String, Double> tag2count, Map<String, IDistribution1D> tag2mean) {
//			// Save as the middle of the period?!
//			Time mid = DataLogImpl.doSave3_time(period);
		for (Map.Entry<String, Double> e : tag2count.entrySet()) {
			Log.i("datalog", e.getKey() + ": " + e.getValue());
		}

		for (Map.Entry<String, IDistribution1D> e : tag2mean.entrySet()) {
//				Log.v(Stat.LOGTAG, "saving "+e.getKey()+"="+e.getValue()+" to "+csv);	
			IDistribution1D mv = e.getValue();
			// String lbl = tag2event.get(e.getKey()); ??
			// mean first, so if you're just grabbing the 1st value you get the "right" one
			double min = mv.getSupport().low;
			double max = mv.getSupport().high;
			Log.i("datalog",
					e.getKey() + ": " + mv.getMean() + " var:" + mv.getVariance() + " min:" + min + " max:" + max);
		}
	}

	// Unsupported for CSV.
	@Override
	@Deprecated
	public void saveHistory(Map<Pair2<String, Time>, Double> tag2time2count) {
	}

	@Override
	public IFuture<IDataStream> getData(Pattern id, Time _start, Time end) {
		throw new UnsupportedOperationException();
	}

	/**
	 * TODO How can we return spread information? i.e. mean/var etc.??
	 * 
	 * @WARNING bucket filtering is done by mid-point value, and you only get
	 *          buckets whose mid-points fall within start & end.
	 * 
	 * @param id
	 * @param start Can be null (includes all)
	 * @param end   Can be null (includes all)
	 * @return never null! May be empty.
	 */
	@Override
	public StatReq<IDataStream> getData(String id, Time start, Time end, KInterpolate fn, Dt bucketSize) {
		throw new UnsupportedOperationException();
	}

	@Override
	public StatReq<Double> getTotal(String tag, Time start, Time end) {
		throw new UnsupportedOperationException();
	}

	@Override
	public StatReq<IDataStream> getMeanData(String tag, Time start, Time end, KInterpolate fn, Dt bucketSize) {
		throw new UnsupportedOperationException();
	}

	@Override
	public Object saveEvent(Dataspace dataspace, DataLogEvent event, Period period) {
		throw new TodoException();
	}

	@Override
	public void saveEvents(Collection<DataLogEvent> values, Period period) {
		throw new UnsupportedOperationException();
	}

	@Override
	public Iterator getReader(String server, Time start, Time end, Pattern tagMatcher, String tag) {
		throw new UnsupportedOperationException();
	}

}
