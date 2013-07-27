package com.github.abrarsyed.gmcp.mcversion

import com.github.abrarsyed.gmcp.Constants
import com.github.abrarsyed.gmcp.Util

import java.util.regex.Pattern

/**
 * A rule specification that applies to the operating system of this computer
 */
class McLibraryRuleOs {

    String name

    Pattern versionPattern

    McLibraryRuleOs(String name, String version) {
        this.name = name
        this.versionPattern = Pattern.compile(version);
    }

    boolean isMatch() {
        switch (Util.OS) {
            case Constants.OperatingSystem.WINDOWS:
                return name == "windows" && isVersionMatch()
            case Constants.OperatingSystem.MAC:
                return name == "osx" && isVersionMatch()
            case Constants.OperatingSystem.LINUX:
                return name == "linux" && isVersionMatch()
            default:
                return false
        }
    }

    boolean isVersionMatch() {
        def version = System.getProperty("os.version")
        return versionPattern.matcher(version).matches();
    }

}
