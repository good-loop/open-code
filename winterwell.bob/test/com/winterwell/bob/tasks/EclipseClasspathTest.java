package com.winterwell.bob.tasks;

import static org.junit.Assert.*;

import java.io.File;
import java.util.Set;

import org.junit.Test;

import com.winterwell.utils.io.FileUtils;

public class EclipseClasspathTest {

	@Test
	public void testGetCollectedLibs_smokeTestBugJun2021() {
		File juiceDir = new File(FileUtils.getWinterwellDir(), "juice");
				// WinterwellProjectFinder().apply("juice");
		assert juiceDir.isDirectory() : juiceDir;
		EclipseClasspath ec = new EclipseClasspath(juiceDir);
		Set<File> libs = ec.getCollectedLibs();
		assert ! libs.isEmpty();
	}

}
