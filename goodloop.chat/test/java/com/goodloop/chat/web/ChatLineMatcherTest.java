package com.goodloop.chat.web;

import static org.junit.Assert.*;

import java.util.Arrays;

import org.junit.Test;

import com.winterwell.maths.stats.distributions.discrete.IFiniteDistribution;
import com.winterwell.utils.Printer;

public class ChatLineMatcherTest {

	@Test
	public void testMatch() {
		ChatLineMatcher clm = new ChatLineMatcher();
		IFiniteDistribution<String> m = clm.match("I like cats", Arrays.asList("I do not like cats", "cats are great", "I like dogs", "What did you say?"));
		Printer.out(m);
	}

}
