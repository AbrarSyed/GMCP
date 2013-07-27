package com.github.abrarsyed.gmcp.mcversion

import com.github.abrarsyed.gmcp.Constants
import com.github.abrarsyed.gmcp.Util
import org.junit.Test

import java.util.regex.Pattern

import static org.junit.Assert.assertFalse
import static org.junit.Assert.assertTrue

class McLibraryRuleOsTest {

    @Test
    public void testMatchCurrentOs() throws Exception {
        String expectedName = getCurrentOsName()

        // Test for any version
        def rule = new McLibraryRuleOs(expectedName, /^.*$/)
        assertTrue(rule.match)

        // Test with the exact version we have
        rule = new McLibraryRuleOs(expectedName, Pattern.quote(System.getProperty("os.version")))
        assertTrue(rule.match)
    }

    @Test
    public void testNotMatchingCurrentOsName() {

        def rule = new McLibraryRuleOs("Some Other OS Name", /^.*$/)
        assertFalse(rule.match)

    }

    @Test
    public void testNotMatchingCurrentOsVersion() {

        def expectedName = getCurrentOsName()

        // Test with the exact version we have
        def rule = new McLibraryRuleOs(expectedName, "some-unrecognized-version")
        assertFalse(rule.match)

    }

    private static String getCurrentOsName() {
        switch (Util.OS) {
            case Constants.OperatingSystem.WINDOWS:
                return "windows"
            case Constants.OperatingSystem.MAC:
                return "osx"
            case Constants.OperatingSystem.LINUX:
                return "linux"
        }
        throw new IllegalStateException("Unknown OS: " + Util.OS)
    }

}
