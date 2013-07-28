package com.github.abrarsyed.gmcp.mcversion

import com.google.common.io.Resources
import org.junit.Test

import javax.xml.bind.DatatypeConverter

import static org.junit.Assert.*

class McVersionInfoTest {

    public static final String MC_1_6_2_INFO_URL = "https://s3.amazonaws.com/Minecraft.Download/versions/1.6.2/1.6.2.json"

    @Test
    public void testDownload() {
        def info = McVersionInfo.parse(new URL(MC_1_6_2_INFO_URL));
        assertEquals("1.6.2", info.id);
    }

    @Test
    public void testParse() {

        def jsonUrl = Resources.getResource(McVersionInfoTest.class, "/1.6.2.json")
        def info = McVersionInfo.parse(jsonUrl);

        assertEquals("1.6.2", info.id);
        Calendar expected = DatatypeConverter.parseDate("2013-07-09T20:59:42+02:00");
        assertEquals(expected, info.time);
        expected = DatatypeConverter.parseDate("2013-07-05T15:09:02+02:00");
        assertEquals(expected, info.releaseTime);
        assertEquals("release", info.type);
        assertEquals("username_session_version", info.processArguments);
        assertEquals('--username ${auth_player_name} --session ${auth_session} --version ${version_name} --gameDir ${game_directory} --assetsDir ${game_assets}',
                info.minecraftArguments);
        assertEquals(4, info.minimumLauncherVersion);
        assertEquals("net.minecraft.client.main.Main", info.mainClass);
        assertEquals(3, info.libraries.size());

        def library = info.libraries[0];
        assertEquals("net.sf.jopt-simple:jopt-simple:4.5", library.name)
        assertEquals(0, library.rules.size())

        /*
            This is the library that should be disallowed on anything but a certain version of OSX
         */
        library = info.libraries[1];
        assertEquals("org.lwjgl.lwjgl:lwjgl-platform:2.9.0", library.name)
        assertEquals(2, library.rules.size())

        def rule = library.rules[0]
        assertEquals("allow", rule.action)
        assertNull(rule.os)

        rule = library.rules[1]
        assertEquals("disallow", rule.action)
        assertEquals("osx", rule.os.name)
        assertEquals(/^10\.5\.\d$/, rule.os.versionPattern.pattern())

        /*
            This is the library that should only be allowed on a certain version of OSX
         */
        library = info.libraries[2];
        assertEquals("org.lwjgl.lwjgl:lwjgl-platform:2.9.1-nightly-20130708-debug3", library.name)
        assertEquals(1, library.rules.size())

        rule = library.rules[0]
        assertEquals("allow", rule.action)
        assertEquals("osx", rule.os.name)
        assertEquals(/^10\.5\.\d$/, rule.os.versionPattern.pattern())
    }

}
