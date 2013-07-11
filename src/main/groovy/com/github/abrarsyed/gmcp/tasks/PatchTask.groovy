package com.github.abrarsyed.gmcp.tasks

import org.gradle.api.DefaultTask
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.SkipWhenEmpty
import org.gradle.api.tasks.TaskAction

import com.github.abrarsyed.gmcp.Constants
import com.github.abrarsyed.gmcp.GMCP
import com.github.abrarsyed.gmcp.Util

class PatchTask extends DefaultTask
{
    @SkipWhenEmpty
    @InputDirectory
    File patchDir

    @InputDirectory
    @OutputDirectory
    @SkipWhenEmpty
    File srcDir

    private final File tempPatch = new File(temporaryDir, "temp.patch")

    @TaskAction
    def doPatches()
    {
        def command, arguments

        // prepare command
        if (GMCP.os == Constants.OperatingSystem.WINDOWS)
            command = Util.baseFile(Constants.EXEC_WIN_PATCH).getPath()
        else
            command = "patch"

        arguments = [
            "-p3",
            "-i",
            "\""+tempPatch.getAbsolutePath()+"\"",
            "-d",
            "\""+srcDir.getAbsolutePath()+"\""
        ]

        logger.lifecycle "COMMAND : "+command
        logger.lifecycle "ARGS: "+arguments.join(" ")

        patchDir.eachFileRecurse
        {
            if (it.isDirectory())
                return


            fixPatch(it)

            project.exec {
                executable = command
                args = arguments

                def log = Util.baseFile(Constants.DIR_LOGS, "FMLPatches.log")
                project.file log
                def stream = log.newOutputStream()
                standardOutput = stream
                errorOutput = stream
            }
        }
    }

    def fixPatch(File patch)
    {
        def text = patch.text
        text = text.replaceAll("(\r\n|\n|\r)", System.getProperty("line.separator"))
        text = text.replaceAll(/(\r\n|\n|\r)/, System.getProperty("line.separator"))
        tempPatch.write(text)
    }
}
