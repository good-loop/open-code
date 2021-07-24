package com.winterwell.utils.log;

import static org.junit.Assert.*;

import java.util.logging.Level;

import org.junit.Test;

import com.winterwell.utils.containers.Containers;

public class SystemOutLogListenerTest {

	public static void main(String[] args) {
		new SystemOutLogListenerTest().testListen();
	}
	
	@Test
	public void testListen() {
		SystemOutLogListener ll = Containers.firstClass(Log.getListeners(), SystemOutLogListener.class);
		assert ll != null;
		ll.setUseColor(true);
		Log.addListener(ll);
		Log.e("test", "Oh dear");
		Log.i("test", "meh");
		Log.report("Hurrah!", Log.SUCCESS);
	}

}
