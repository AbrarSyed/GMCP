package com.github.abrarsyed.gmcp.mcversion

class McLibraryRule {

    String action

    McLibraryRuleOs os

    boolean isAllowed() {
        def matches = os == null || os.match

        if (action == "allow")
            return matches
        else
            return !matches
    }

}
