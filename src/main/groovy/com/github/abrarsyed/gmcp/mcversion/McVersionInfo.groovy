package com.github.abrarsyed.gmcp.mcversion

import com.google.common.base.Charsets
import com.google.common.io.Resources
import groovy.json.JsonSlurper

import javax.xml.bind.DatatypeConverter

/**
 * Parses the Minecraft JSON information file used to describe a Minecraft version.
 *
 * Example: https://s3.amazonaws.com/Minecraft.Download/versions/1.6.2/1.6.2.json
 */
class McVersionInfo {

    String id

    Calendar time

    Calendar releaseTime

    String type

    String processArguments

    String minecraftArguments

    int minimumLauncherVersion

    List<McLibraryDependency> libraries

    String mainClass

    static def parse(URL url) {
        try {
            return parse(Resources.toString(url, Charsets.UTF_8));
        } catch (IOException e) {
            throw new IOException("Unable to access the Minecraft Version information @ " + url, e);
        }
    }

    static def parse(String jsonText) {

        def slurper = new JsonSlurper()
        def jsonInfo = slurper.parseText(jsonText)

        def result = new McVersionInfo()

        result.id = (String) jsonInfo["id"]
        result.time = parseDate((String) jsonInfo["time"])
        result.releaseTime = parseDate((String) jsonInfo["releaseTime"])
        result.type = (String) jsonInfo["type"]
        result.processArguments = (String) jsonInfo["processArguments"]
        result.minecraftArguments = (String) jsonInfo["minecraftArguments"]
        result.minimumLauncherVersion = (int) jsonInfo["minimumLauncherVersion"]
        result.libraries = parseLibraries(jsonInfo["libraries"])
        result.mainClass = (String) jsonInfo["mainClass"]

        result
    }

    static def parseDate(String dateString) {
        // Java date handling isn't great. Especially since SimpleDateFormat can't handle ISO compliant time stamps
        return DatatypeConverter.parseDateTime(dateString);
    }

    static def parseLibraries(def jsonLibraries) {

        def result = new ArrayList<McLibraryDependency>();

        for (jsonLibrary in jsonLibraries) {
            def library = new McLibraryDependency()
            library.name = jsonLibrary["name"] // This is a Maven-style artifact reference

            // There are "rules" that pull in other versions of the libraries for different OS versions
            // Currently it seems to be used to pull in a nightly-build of lwjgl for certain MacOS versions
            def jsonRules = jsonLibrary["rules"]
            if (jsonRules != null) {
                for (jsonRule in jsonRules) {
                    def rule = new McLibraryRule()
                    rule.action = jsonRule["action"]
                    if (jsonRule["os"]) {
                        def ruleOs = new McLibraryRuleOs(
                                (String) jsonRule["os"]["name"],
                                (String) jsonRule["os"]["version"]
                        )
                        rule.os = ruleOs
                    }
                    library.rules.add(rule)
                }
            }

            result.add(library)
        }

        return result

    }

    @Override
    public String toString() {
        return id
    }

}
