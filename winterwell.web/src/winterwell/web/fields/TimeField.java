package winterwell.web.fields;

import java.io.Serializable;
import java.util.concurrent.Callable;
import java.util.concurrent.atomic.AtomicBoolean;

import winterwell.utils.Utils;
import winterwell.utils.time.Time;
import winterwell.utils.time.TimeUtils;

import com.winterwell.utils.Constant;

/**
 * TODO Like DateField, adding relative-time answers such as "last week"
 * 
 * Enter dates (and times). TODO a (popup) javascript calendar widget. TODO
 * handle time zones configurably
 * 
 * @author daniel
 * @testedby {@link TimeFieldTest}
 */
public class TimeField extends AField<Callable<Time>> {

	private static final long serialVersionUID = 1L;

	public TimeField(String name) {
		super(name, "text");
		// The html5 "date" type is not really supported yet.
		// What it does do on Firefox is block non-numerical text entry, which
		// we want to support
		cssClass = "DateField";
	}

	/**
	 * First tries the "canonical" "HH:mm dd/MM/yyyy", then the other formats,
	 * finally {@link TimeUtils#parseExperimental(String)}.
	 */
	@Override
	public Callable<Time> fromString(String v) {
		AtomicBoolean isRel = new AtomicBoolean();
		// HACK fixing bugs elsewhere really. Handle "5+days+ago" from a query
		v = v.replace('+', ' ');		
		
		Time t = DateField.parse(v, isRel);
		if ( ! isRel.get()) {
			return new Constant(t);
		}
		// ??Relative but future (eg. "tomorrow")... make it absolute? No -- let the caller make that decision.		
		// e.g. "1 day ago" or "tomorrow"
		return new RelTime(v); 
	}

	@Override
	public String toString(Callable<Time> _time) {
		// relative?
		if (_time instanceof RelTime) {
			return ((RelTime) _time).v;
		}
		try {
			Time time = _time.call();
			return toString(time);
		} catch (Exception e) {
			throw Utils.runtime(e);
		}
	}

	public String toString(Time time) {
		return DateField.toString2(time);
	}

	@Override
	public Class<Callable<Time>> getValueClass() {
		return (Class) Callable.class;
	}
}


final class RelTime implements Callable<Time>, Serializable {
	private static final long serialVersionUID = 1L;
	final String v;
	
	public RelTime(String v) {
		this.v = v;
	}
	
	@Override
	public String toString() {
		return "RelTime["+v+"]";
	}

	@Override
	public Time call() throws Exception {
		return TimeUtils.parseExperimental(v);
	}
}
