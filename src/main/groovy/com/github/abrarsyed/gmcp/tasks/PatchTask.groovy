package com.github.abrarsyed.gmcp.tasks

import com.github.abrarsyed.gmcp.Constants
import com.github.abrarsyed.gmcp.GMCP
import com.github.abrarsyed.gmcp.Util
import difflib.DiffUtils
import difflib.Patch
import groovy.io.FileType
import org.gradle.api.DefaultTask
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.SkipWhenEmpty
import org.gradle.api.tasks.TaskAction

class PatchTask extends DefaultTask
{
    @SkipWhenEmpty
    @InputDirectory
    def File patchDir

    @InputDirectory
    @OutputDirectory
    @SkipWhenEmpty
    def File srcDir

    def File logFile

    private final File tempPatch = new File(temporaryDir, "temp.patch")

    @TaskAction
    def doBinaryPatches()
    {
        patchStuff(patchDir, srcDir, logFile, tempPatch)
    }

    def static fixPatch(File patch, File tempPatch)
    {
        def text = patch.text
        text = text.replaceAll("(\r\n|\n|\r)", Constants.NEWLINE)
        text = text.replaceAll(/(\r\n|\n|\r)/, Constants.NEWLINE)
        tempPatch.write(text)
    }

    public static patchStuff(File patchDir, File srcDir, File logFile, File tempPatch)
    {
        //binaryPatch(patchDir, srcDir, logFile, tempPatch);
        libPatch(patchDir, srcDir, logFile)
    }

    private static binaryPatch(File patchDir, File srcDir, File logFile, File tempPatch)
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
                "\"" + tempPatch.getAbsolutePath() + "\"",
                "-d",
                "\"" + srcDir.getAbsolutePath() + "\""
        ]

        def log = logFile
        if (log)
        {
            if (logFile.exists())
                logFile.delete()

            // make it new, delete was to clear data.
            GMCP.project.file logFile
        }

        patchDir.eachFileRecurse(FileType.FILES)
                {
                    fixPatch(it, tempPatch)

                    def result = GMCP.project.exec {
                        executable = command
                        args = arguments

                        if (log)
                        {
                            def stream = new FileOutputStream(logFile, true)
                            standardOutput = stream
                            errorOutput = stream
                        }

                        ignoreExitValue = true
                    }

                    //            if (result.getExitValue() != 0)
                    //            {
                    //                throw new RuntimeException("Gnu patch failed! See log file: "+logFile)
                    //            }

                }
    }

    private static libPatch(File patchDir, File srcDir, File logFile)
    {
        Map<File, Patch> patchMap = [:]
        def newFile, patch

        def writer = logFile.newPrintWriter();

        // recurse through files
        patchDir.eachFileRecurse(FileType.FILES) {
            // if its a patch
            if (it.isFile() && it.path.endsWith(".patch"))
            {
                newFile = new File(srcDir, Util.getRelative(patchDir, it).replace(/.patch/, ""))
                patch = DiffUtils.parseUnifiedDiff(it.text.readLines())
                patchMap.put(newFile, patch)
            }
        }

        def counter = 0, success = 0
        patchMap.each
                { file, delta ->
                    try
                    {
                        def lines = file.text.readLines()
                        lines = delta.applyTo(lines)
                        file.write(lines.join("\n"))
                        success++
                    }
                    catch (Exception e)
                    {
                        writer.writeLine "error patching " + file + "   skipping."
                        if (counter <= 1)
                            e.printStackTrace(writer);
                    }
                    counter++
                }

        writer.writeLine success + " out of " + counter + " succeeded"
        writer.flush();
        writer.close();
    }
}
