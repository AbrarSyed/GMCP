package com.github.abrarsyed.gmcp.tasks

import com.cloudbees.diff.ContextualPatch
import com.github.abrarsyed.gmcp.Constants
import com.github.abrarsyed.gmcp.GMCP
import com.github.abrarsyed.gmcp.Util
import com.google.common.io.Files
import groovy.io.FileType
import org.gradle.api.DefaultTask
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.SkipWhenEmpty
import org.gradle.api.tasks.TaskAction

import java.nio.charset.Charset

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
        libPatch(patchDir, srcDir, logFile, tempPatch.getParentFile())
    }

    private static libPatch(File patchDir, File srcDir, File logFile, temp)
    {
        temp.mkdirs();

        logFile.getParentFile().mkdirs()
        logFile.createNewFile()
        def writer = logFile.newPrintWriter()
        def loadedPatches = new ArrayList<ContextualPatch>()

        patchDir.eachFile(FileType.FILES) {
            writer.println "Fixing patch: " + it
            String relative = it.getAbsolutePath().substring(patchDir.getAbsolutePath().length());
            File outFile = new File(temp, relative);

            String text = it.text

            // newlines
            text = text.replaceAll("(\r\n|\r|\n)", Constants.NEWLINE).replaceAll("(\\r\\n|\\r|\\n)", Constants.NEWLINE);

            // fixing for the paths.
            text = text.replaceAll("\\.\\./src-base/minecraft/(net/minecraft)", '$1');
            outFile.getParentFile().mkdirs();
            Files.touch(outFile);
            Files.write(text, outFile, Charset.defaultCharset());

            writer.println "Loading Patch: " + it
            loadedPatches.add(ContextualPatch.create(outFile, srcDir));
        }

        // apply patches
        loadedPatches.each {
            it.patch(false);
            List<ContextualPatch.PatchReport> errors = it.patch(false);
            for (ContextualPatch.PatchReport report : errors)
            {
                if (report.getStatus() != ContextualPatch.PatchStatus.Patched)
                {
                    writer.println "Patching failed: " + report.getFile(), report.getFailure()
                }
            }
        }

        writer.close();
    }
}
