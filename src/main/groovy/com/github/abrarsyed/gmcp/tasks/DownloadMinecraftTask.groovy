package com.github.abrarsyed.gmcp.tasks

import com.github.abrarsyed.gmcp.Constants
import com.github.abrarsyed.gmcp.Json16Reader
import com.github.abrarsyed.gmcp.Util
import org.gradle.api.DefaultTask
import org.gradle.api.tasks.TaskAction

class DownloadMinecraftTask extends DefaultTask
{

    Json16Reader json

    @TaskAction
    def task()
    {
        new File(project.minecraft.jarDir).mkdirs()

        do16Plus()
    }

    def public setIncrementals()
    {
        set16PlusIncrementals()
    }

    def private set16PlusIncrementals()
    {
        inputs.file { Util.baseFile(Constants.DIR_FML, "mc_versions.cfg") }
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
}
