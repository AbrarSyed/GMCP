package com.github.abrarsyed.gmcp.mcversion

import org.junit.Assert
import org.junit.Test

import static org.junit.Assert.assertFalse
import static org.junit.Assert.assertTrue
import static org.mockito.Mockito.doReturn
import static org.mockito.Mockito.mock
import static org.mockito.Mockito.when

class McLibraryRuleTest {

    @Test
    void testAllowed() {

        // Blanket allow
        def rule = new McLibraryRule()
        rule.action = "allow"
        assertTrue(rule.allowed)

    }

    @Test
    public void testDisallowed() throws Exception {

        // Blanket disallow (which is not very useful)
        def rule = new McLibraryRule()
        rule.action = "disallow"
        assertFalse(rule.allowed)

    }

    @Test
    public void testDisallowWitOsRuleMatching() throws Exception {
        testRuleWithOs("disallow", true, false)
    }

    @Test
    public void testDisallowWitOsRuleNotMatching() throws Exception {
        testRuleWithOs("disallow", false, true)
    }

    @Test
    public void testAllowWitOsRuleMatching() throws Exception {
        testRuleWithOs("allow", true, true)
    }

    @Test
    public void testAllowWitOsRuleNotMatching() throws Exception {
        testRuleWithOs("allow", false, false)
    }

    private void testRuleWithOs(String action, boolean osMatching, boolean expectedResult) {

        def mockedOsRule = new MockedOsRule()
        mockedOsRule.fixedMatch = osMatching

        def rule = new McLibraryRule()
        rule.action = action
        rule.os = mockedOsRule
        Assert.assertEquals(expectedResult, rule.allowed)

    }

    private static class MockedOsRule extends McLibraryRuleOs {
        boolean fixedMatch

        MockedOsRule() {
            super("dontCare", "dontCare")
        }

        @Override
        boolean isMatch() {
            return fixedMatch
        }
    }

}
