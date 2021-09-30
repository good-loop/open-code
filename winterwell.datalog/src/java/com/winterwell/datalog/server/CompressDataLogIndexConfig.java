package com.winterwell.datalog.server;

import java.util.List;

import com.winterwell.datalog.DataLogConfig;
import com.winterwell.utils.io.Option;
import com.winterwell.utils.time.Time;
import com.winterwell.utils.time.TimeParser;

public class CompressDataLogIndexConfig extends DataLogConfig {

	@Option(description="If set, the source index defaults to e.g. datalog.gl_sep21.")
	public String month;
	
	@Option(description="The new index to output into. Normally unset, defaults to {source index}_compressed")
	String destIndex;
	
	@Option(description="Normally unset. Set to switch off the alias swap, which would normally remove the old data from datalog.{dataspace}.all and swap in the compressed data.")
	public boolean noAliasSwap;

	@Option(description="Normally unset. A filter to only process some data. HACK setting `-user:/.+@trk/`")
	public String filter;
	
	@Option(description="Normally unset. Remove some properties from what is normally kept in the transformed data. i.e. this modifies longterms")
	public List<String> removeProperty;

	public Time getMonth() {
		if (month==null) return null;
		Time t = new TimeParser().parseExperimental(month);
		return t;
	}
}
