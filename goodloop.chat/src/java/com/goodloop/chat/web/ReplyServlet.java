package com.goodloop.chat.web;

import java.util.List;
import java.util.Map;

import com.winterwell.maths.stats.distributions.discrete.IFiniteDistribution;
import com.winterwell.utils.Printer;
import com.winterwell.utils.Utils;
import com.winterwell.utils.containers.ArrayMap;
import com.winterwell.utils.containers.ArraySet;
import com.winterwell.utils.web.WebUtils2;
import com.winterwell.web.ajax.JSend;
import com.winterwell.web.ajax.KAjaxStatus;
import com.winterwell.web.app.IServlet;
import com.winterwell.web.app.WebRequest;
import com.winterwell.web.fields.ListField;

public class ReplyServlet implements IServlet {

	static ChatLineMatcher clm = new ChatLineMatcher();
	
	@Override
	public void process(WebRequest state) throws Exception {
		String chatId = state.get("chatId");
		String text = state.get("text");
		String reply = state.get("reply");
		String type = state.get("type"); // text (or in future ux)
		String visitorId = state.get("oxid");
//		String pb = state.getPostBody();
		String agentId = "596772";
		
		if (state.actionIs("match")) {
			doMatch(state);
			return;
		}
		
		SmartSuppClient ssc = new SmartSuppClient();
		ssc.setChatId(chatId);
		ssc.setAgentId(agentId);
		// TODO!
		Map resp;
		if (reply != null) {
			resp = ssc.sendReply(reply);
		} else {
			resp = ssc.sendReply("Hm... What about "+
					Utils.getRandomMember(new ArraySet<>("anacondas beetles crabs dragons elephants frogs goats hippos iguanas jaguars".split(" ")))
				+"?");
		}
		
		JSend jsend = new JSend();
		jsend.setStatus(KAjaxStatus.success);
		jsend.send(state);
	}

	private void doMatch(WebRequest state) {
		String text = state.get("text");
		List<String> options = state.get(new ListField<String>("options"));
		
		// match
		IFiniteDistribution<String> pMatched = clm.match(text, options);
		String matched = pMatched.getMostLikely();
		
		// reply
		ArrayMap data = new ArrayMap(
			"text", text,
			"matched", matched,
			"distribution", pMatched.asMap()
		);
		
		JSend jsend = new JSend();
		jsend.setStatus(KAjaxStatus.success);
		jsend.setData(data);
		jsend.send(state);
	}

}
