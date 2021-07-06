package com.goodloop.chat.web;

import com.goodloop.chat.data.Chat;
import com.winterwell.utils.TodoException;
import com.winterwell.web.app.CrudServlet;
import com.winterwell.web.app.WebRequest;

/**
 * Status: not used!
 * @author daniel
 *
 */
public class ChatServlet extends CrudServlet<Chat> {

	public ChatServlet() {
		super(Chat.class);
	}

}
