package com.badlogicgames.gdx.tests;

import com.badlogic.gdx.tests.utils.GdxTests;
import org.graalvm.nativeimage.hosted.Feature;
import org.graalvm.nativeimage.hosted.RuntimeReflection;

public class TestConfigRegistration implements Feature {
	@Override
	public void beforeAnalysis (BeforeAnalysisAccess access) {
		GdxTests.tests.forEach(aClass -> {
			try {
				RuntimeReflection.register(aClass.getConstructor());
			} catch (NoSuchMethodException e) {
				e.printStackTrace();
			}
		});
	}
}
