package com.github.abrarsyed.gmcp.tasks

import com.github.abrarsyed.gmcp.Constants
import com.github.abrarsyed.gmcp.GMCP
import com.github.abrarsyed.gmcp.Util
import com.github.abrarsyed.gmcp.patching.ContextualPatch
import com.google.common.io.Files
import groovy.io.FileType
import org.gradle.api.DefaultTask
import org.gradle.api.logging.LogLevel
import org.gradle.api.logging.Logger
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

    public static patchStuff(File patchDir, File srcDir, Logger log, File tempPatch)
    {
        libPatch(patchDir, srcDir, log, tempPatch.getParentFile())
    }

    private static libPatch(File patchDir, File srcDir, Logger log, temp)
    {
        temp.mkdirs();

        def loadedPatches = new ArrayList<ContextualPatch>()

        patchDir.eachFileRecurse(FileType.FILES) {
            log.debug "Fixing patch: " + it
            String relative = it.getAbsolutePath().substring(patchDir.getAbsolutePath().length());
            File outFile = new File(temp, relative);

            String text = it.text

            // newlines
            text = text.replaceAll("(\r\n|\r|\n)", Constants.NEWLINE).replaceAll("(\\r\\n|\\r|\\n)", Constants.NEWLINE);

            // fixing for the paths.
            text = text.replaceAll("\\.\\./src[_-]base/minecraft/(net/minecraft)", '$1');
            text = text.replaceAll("\\.\\./src[_-]work/minecraft/(net/minecraft)", '$1');
            outFile.getParentFile().mkdirs();
            Files.touch(outFile);
            Files.write(text, outFile, Charset.defaultCharset());

            log.debug "Loading Patch: " + it
            loadedPatches.add(ContextualPatch.create(outFile, srcDir));
        }

        // apply patches
        loadedPatches.each {

            // fix access levels
            it.setAccessC14N(true)
            it.setWhitespaceC14N(true)

            log.debug "Applying Patch: " + it
            List<ContextualPatch.PatchReport> errors = it.patch(false);
            for (ContextualPatch.PatchReport report : errors)
            {
                if (!report.getStatus().success)
                {
                    log.log LogLevel.INFO, "Patching failed: " + report.getTarget(), report.getFailure()

                    report.hunks.each { ContextualPatch.HunkReport hunk ->
                        if (!hunk.status.success)
                        {
                            log.info "Hunk "+hunk.index+" failed!"
                        }
                    }

                    throw report.getFailure()
                }
                else if (report.getStatus() == ContextualPatch.PatchStatus.Fuzzed)
                {
                    log.info "Patch Fuzzed: " + report.target

                    report.hunks.each { ContextualPatch.HunkReport hunk ->
                        if (!hunk.status.success)
                        {
                            log.info "Hunk "+hunk.index+" FUZZED "+ hunk.fuzz + "!"
                        }
                    }
                }
                else
                {
                    log.info "Patch Succeeded: " + report.target
                }
            }
        }
    }
}
