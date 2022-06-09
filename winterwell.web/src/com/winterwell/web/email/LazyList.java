package com.winterwell.web.email;

import java.util.AbstractList;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;

public class LazyList<X> extends AbstractList<X> implements List<X> {
	
	private final List<X> base = new ArrayList<>();
	private final Iterator<X> src;
	
	public LazyList(Iterator<X> src) {
		this.src = src;
	}
	
	@Deprecated // the spec says you should have this
	public LazyList() {
		this(Collections.EMPTY_LIST.iterator());
	}
	
	// HACK returns null if index is too big, to handle lazy filtering with a bit of grace
	@Override
	public X get(int index) {
		while (base.size() <= index) {
			if ( ! src.hasNext()) {
				return null; // HACK!
			}
			base.add(src.next());
		}		
		return base.get(index);		
	}

	private int size;
	
	public LazyList<X> setSize(int size) {
		this.size = size;
		return this;
	}

	@Override
	public X set(int index, X element) {
		get(index); // make it
		return base.set(index, element);
	}
	
	@Override
	public int size() {
		return size==-1? base.size() : size;
	}

}
