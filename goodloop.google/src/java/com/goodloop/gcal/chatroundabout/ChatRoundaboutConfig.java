package com.goodloop.gcal.chatroundabout;

import com.winterwell.utils.io.Option;
import com.winterwell.utils.time.Dt;
import com.winterwell.utils.time.TUnit;
import com.winterwell.utils.time.TimeOfDay;
import com.winterwell.web.app.ISiteConfig;

public class ChatRoundaboutConfig implements ISiteConfig {

	@Override
	public int getPort() {
		return 8713;
	}

	@Option
	TimeOfDay crossTeamTime = new TimeOfDay("11:30am");

	@Option
	TimeOfDay inTeamTime = new TimeOfDay("11:00am");

	@Option
	Dt duration = new Dt(10, TUnit.MINUTE);
	
}
