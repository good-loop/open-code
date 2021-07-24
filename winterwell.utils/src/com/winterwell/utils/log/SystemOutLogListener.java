package com.winterwell.utils.log;

import java.awt.Color;

import com.winterwell.utils.Printer;
import com.winterwell.utils.ReflectionUtils;
import com.winterwell.utils.Utils;
import com.winterwell.utils.gui.GuiUtils;

/**
 * TODO colours https://stackoverflow.com/questions/1448858/how-to-color-system-out-println-output
 * @author daniel
 * @testedby {@link SystemOutLogListenerTest}
 */
public final class SystemOutLogListener implements ILogListener {

	boolean useColor;
//	off by default for now = Utils.OSisUnix() && GuiUtils.isInteractive();
	
	public SystemOutLogListener setUseColor(boolean useColor) {
		this.useColor = useColor;
		return this;
	}
	
	@Override
	public void listen(Report report) {
		StringBuilder sb = new StringBuilder();
		sb.append('#'); sb.append(report.tag); sb.append('\t');
		sb.append(report.getMessage());
		if (report.ex!=null) {
			sb.append('\t'); sb.append(report.getDetails());
		}
		if (useColor) {
			if (report.level == Log.ERROR) {
				Printer.outInColor(Color.YELLOW, sb.toString());
				return;
			}
			if (report.level == Log.SUCCESS) {
				Printer.outInColor(Color.GREEN, sb.toString());
				return;
			}
		}
		Printer.out(sb.toString());
	}
	
}