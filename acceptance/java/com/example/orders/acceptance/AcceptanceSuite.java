package com.example.orders.acceptance;

import org.junit.platform.suite.api.ConfigurationParameter;
import org.junit.platform.suite.api.IncludeEngines;
import org.junit.platform.suite.api.SelectDirectories;
import org.junit.platform.suite.api.Suite;

import static io.cucumber.junit.platform.engine.Constants.GLUE_PROPERTY_NAME;

/**
 * Entry point of the executable specification.
 *
 * Feature files live OUTSIDE the code tree (specs/features) because they are the
 * product's source of truth, owned by humans, not test code owned by an agent.
 *
 * Tags, target environment and report paths come from system properties
 * (see pom.xml failsafe config and scripts/acceptance.sh).
 */
@Suite
@IncludeEngines("cucumber")
@SelectDirectories("specs/features")
@ConfigurationParameter(key = GLUE_PROPERTY_NAME, value = "com.example.orders.acceptance")
public class AcceptanceSuite {
}
