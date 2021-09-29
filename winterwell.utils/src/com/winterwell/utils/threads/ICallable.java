package com.winterwell.utils.threads;

import java.util.concurrent.Callable;

/**
 * {@link Callable} but with unchecked exceptions, so you dont need try-catches.
 * @author daniel
 *
 * @param <V>
 */
public interface ICallable<V> extends Callable<V> {

	@Override
	V call() throws RuntimeException;
	
	/**
	 * Convenience for "call if not null"
	 * @param <V>
	 * @param fn Can be null
	 * @return fn() or null
	 */
	public static <V> V callOrNull(ICallable<V> fn) {
		if (fn==null) return null;
		return fn.call();
	}
}
