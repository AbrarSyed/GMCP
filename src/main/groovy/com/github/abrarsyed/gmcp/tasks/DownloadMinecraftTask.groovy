package com.github.abrarsyed.gmcp.tasks

import org.gradle.api.DefaultTask
import org.gradle.api.tasks.TaskAction

import com.github.abrarsyed.gmcp.ConfigParser
import com.github.abrarsyed.gmcp.Constants
import com.github.abrarsyed.gmcp.GMCP
import com.github.abrarsyed.gmcp.Json16Reader
import com.github.abrarsyed.gmcp.Util

class DownloadMinecraftTask extends DefaultTask
{
    
    Json16Reader json
    
    @TaskAction
    def task()
    {
        new File(project.minecraft.jarDir).mkdirs()
        
        if (project.minecraft.is152OrLess())
            do152()
        else
            do16Plus()
    }
    
    def private set16PlusIncrementals()
    {
        inputs.file { baseFile(Constants.DIR_FML, "mc_versions.cfg") }
        outputs.with {
            file { Util.jarVersionFile(Constants.JAR_JAR16_CLIENT_BAK) }
            file { Util.jarFile(Constants.JAR_JAR_SERVER) }
        }
    }
    
    def private do16Plus()
    {
        Util.download(json.getClientURL(), Util.jarVersionFile(Constants.JAR_JAR16_CLIENT_BAK))
        Util.download(json.getServerURL(), Util.jarFile(Constants.JAR_JAR_SERVER))
    }

    def private set152Incrementals()
    {
        inputs.file { baseFile(Constants.DIR_FML, "mc_versions.cfg") }
        outputs.with {
            file { Util.jarFile(Constants.JAR_JAR_CLIENT_BAK) }
            file { Util.jarFile(Constants.JAR_JAR_SERVER) }
            file { Util.jarFile("bin", "lwjgl.jar") }
            file { Util.jarFile("bin", "lwjgl_util.jar") }
            file { Util.jarFile("bin", "jinput.jar") }
            file { Util.jarFile("bin", "natives.jar") }
        }
    }

    def private do152()
    {
        def root = Util.jarFile(Constants.DIR_JAR_BIN)
        root.mkdirs()

        // read config
        ConfigParser parser = new ConfigParser(Util.baseFile(Constants.DIR_FML, "mc_versions.cfg"))
        def baseUrl = parser.getProperty("default", "base_url")

        def mcver = parser.getProperty("default", "current_ver")
        Util.download(parser.getProperty(mcver, "client_url"), Util.jarFile(Constants.JAR_JAR_CLIENT_BAK))
        Util.download(parser.getProperty(mcver, "server_url"), Util.jarFile(Constants.JAR_JAR_SERVER))

        def dls = parser.getProperty("default", "libraries").split(/\s/)
        dls.each { Util.download(baseUrl+it, new File(root, it)) }

        def nativesName = parser.getProperty("default", "natives").split(/\s/)[GMCP.os.ordinal()]
        Util.download(baseUrl + nativesName, Util.jarFile("bin", "natives.jar"))
    }
}
