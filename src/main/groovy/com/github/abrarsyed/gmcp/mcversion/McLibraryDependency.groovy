package com.github.abrarsyed.gmcp.mcversion

class McLibraryDependency {

    String name

    List<McLibraryRule> rules = new ArrayList<McLibraryRule>()

    McLibraryNatives natives = null

    McLibraryExtract extract = null

    /**
     * Tests whether this dependency is excluded because it has matching disallow rules
     * @return
     */
    boolean isAllowed() {

        if (rules.isEmpty())
            return true

        def allowed = true

        for (rule in rules) {
            allowed &= rule.allowed
        }

        return allowed

    }

    @Override
    String toString() {
        name
    }

}
