package com.github.abrarsyed.gmcp.mcversion

import org.junit.Assert
import org.junit.Test

class McLibraryDependencyTest {

    @Test
    public void testAllowedNoRules() throws Exception {

        // Dependency without rules should always be taken into consideration
        testWithRules(true)

    }

    @Test
    public void testAllowedSingleRule() throws Exception {

        // A single rule that is allowed, the dependency should be allowed as well
        testWithRules(true, true)

    }

    @Test
    public void testDisallowedSingleRule() throws Exception {

        // A single rule that is disallowed, the dependency should be skipped
        testWithRules(false, false)

    }

    @Test
    public void testDisallowAndAllow() throws Exception {

        // Disallow should win over allow if multiple rules are specified
        testWithRules(false, true, false)

    }

    private static void testWithRules(boolean expectedResult, boolean... allowOrDisallow) {

        def dep = new McLibraryDependency()
        for (mode in allowOrDisallow)
            dep.rules.add(new MockedRule(mode))

        Assert.assertEquals(expectedResult, dep.allowed)

    }

    private static class MockedRule extends McLibraryRule {

        private final boolean allowed

        MockedRule(boolean allowed) {
            this.allowed = allowed
        }

        @Override
        boolean isAllowed() {
            return allowed
        }

    }

}
