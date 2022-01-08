package com.winterwell.utils.log;

import java.io.Serializable;
import java.util.Arrays;
import java.util.logging.Level;

import com.winterwell.utils.Printer;
import com.winterwell.utils.StrUtils;
import com.winterwell.utils.time.Time;

/**
 * A log report (just a simple time + message + level).
 * 
 * @author daniel
 * 
 */
public final class Report implements Serializable {
	
	private static final long serialVersionUID = 1L;
	/**
	 * NB: Only set by exceptions
	 */
	private final String details;
	public final Level level;
	private final String msg;

	public String getDetails() {
		return details;
	}
	
	/**
	 * NB: does not start with a # -- that's added by toString().
	 */
	public final String tag;
	private final Time time = new Time();
	final Throwable ex;
//	final long threadId;

	public Report(String tag, Exception ex) {
		this(tag, ex.getMessage(), Level.SEVERE, Printer.toString(ex, true), ex); 
	}

	public Report(String tag, String msg, Level level, String details, Throwable ex) {
		this.tag = tag;
		this.msg = msg;
		this.level = level;
		this.ex = ex;
		this.details = details;
//		this.threadId = Thread.currentThread().getId();		
	}
	
	final String context = Log.getContextMessage();

	public String getMessage() {
		return msg;
	}

	public Time getTime() {
		return time;
	}

	final String thread = Thread.currentThread().toString();
	
	/**
	 * [time] level #tag message details context thread
	 */
	@Override
	public String toString() {
//		// Convert tabs, so we lines are nicely tab-aligned
//		// Assumes: level & tag don't have tabs, and after message we don't care
//		String _msg = msg.replace('\t', ' ');
		return "["+time+"]"+StrUtils.join(Arrays.asList(
				time, level, tag, getMarker(), msg, details, Log.getContextMessage(), thread
				), "\t"); 
	}
	
	/** 
	 * A shorter String, for conserving log file space at the cost of details.
	 * @return time tag message
	 */
	public String toStringShort() {
		// Convert tabs, so we lines are nicely tab-aligned
		// Assumes: level & tag don't have tabs, and after message we don't care
		String _msg = msg.replace('\t', ' ');
		return "["+time+"]\t\t#"+tag+"\t"+_msg+"\n";
	}

	/**
	 * A crude hash for tag+msg -- this is useful for filtering
	 * @return
	 */
	public String getMarker() {
		return StrUtils.md5(tag+msg).substring(0,8);
	}

}
