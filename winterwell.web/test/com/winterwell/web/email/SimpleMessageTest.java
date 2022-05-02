package com.winterwell.web.email;

import static org.junit.Assert.*;

import java.io.File;
import java.io.FileFilter;

import org.apache.commons.io.FileUtils;
import org.junit.Test;

import com.winterwell.utils.Printer;
import com.winterwell.utils.gui.GuiUtils;

public class SimpleMessageTest {

	@Test
	public void testLoadFromFile() {
		File a = new File(FileUtils.getUserDirectory(), "Downloads");
		if ( ! a.isFile()) a = null;
		File emlFile = GuiUtils.selectFile("Pick an .eml file", a, f -> f.isDirectory() || f.getName().endsWith(".eml"));
		
		SimpleMessage loaded = SimpleMessage.loadFromFile(emlFile);
		String body = loaded.getBodyHtml();
		Printer.out(body);
	}

}
