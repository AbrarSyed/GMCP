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
            file { Util.cacheFile(Constants.FMED_JAR_MERGED) }
            file { Util.jarFile(Constants.FMED_JAR_SERVER_FRESH) }
        }
    }

    def private do16Plus()
    {
        Util.download(json.getClientURL(), Util.cacheFile(Constants.FMED_JAR_MERGED))
        Util.download(json.getServerURL(), Util.jarFile(Constants.FMED_JAR_SERVER_FRESH))
    }
}
