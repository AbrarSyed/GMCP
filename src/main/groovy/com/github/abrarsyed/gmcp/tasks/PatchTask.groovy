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

    //    @TaskAction
    //    def doLibPatches()
    //    {
    //        Map<File, Patch> patchMap = [:]
    //        def newFile, patch
    //
    //        // recurse through files
    //        patchDir.eachFileRecurse {
    //            // if its a patch
    //            if (it.isFile() && it.path.endsWith(".patch"))
    //            {
    //                newFile = new File(srcDir, Util.getRelative(patchDir, it).replace(/.patch/, ""))
    //                patch = DiffUtils.parseUnifiedDiff(it.text.readLines())
    //                patchMap.put(newFile, patch)
    //            }
    //        }
    //
    //        def counter = 0, success = 0
    //        patchMap.each
    //        { file, delta ->
    //            try
    //            {
    //                def lines = file.text.readLines()
    //                lines = delta.applyTo(lines)
    //                file.write(lines.join("\n"))
    //                success++
    //            }
    //            catch(Exception e)
    //            {
    //                logger.error "error patching "+file+"   skipping."
    //                if (counter <= 1)
    //                    e.printStackTrace()
    //            }
    //            counter++
    //        }
    //
    //        logger.lifecycle success + " out of " + counter + " succeeded"
    //    }

    @TaskAction
    def doBinaryPatches()
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

        def log = Util.baseFile(Constants.DIR_LOGS, "FMLPatches.log")
        if (log.exists())
            log.delete()

        // make it new, delete was to clear data.
        project.file log

        patchDir.eachFileRecurse
        {
            if (it.isDirectory())
                return


            fixPatch(it)

            project.exec {
                executable = command
                args = arguments

                def stream = new FileOutputStream(log, true)
                standardOutput = stream
                errorOutput = stream
                
                ignoreExitValue = true
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
