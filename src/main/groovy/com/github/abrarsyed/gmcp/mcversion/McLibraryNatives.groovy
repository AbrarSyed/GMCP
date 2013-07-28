package com.github.abrarsyed.gmcp.mcversion

import com.github.abrarsyed.gmcp.Constants

class McLibraryNatives {

    String windows

    String linux

    String osx

    def getNative(Constants.OperatingSystem operatingSystem) {
        switch (operatingSystem) {
            case Constants.OperatingSystem.WINDOWS:
                return windows
            case Constants.OperatingSystem.MAC:
                return osx
            case Constants.OperatingSystem.LINUX:
                return linux
        }
    }
}
