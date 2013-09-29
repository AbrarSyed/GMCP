package com.github.abrarsyed.gmcp.tasks

import com.cloudbees.diff.ContextualPatch
import com.github.abrarsyed.gmcp.Constants
import com.github.abrarsyed.gmcp.Util
import com.github.abrarsyed.gmcp.source.*
import com.github.abrarsyed.jastyle.ASFormatter
import com.github.abrarsyed.jastyle.OptParser
import com.google.common.io.Files
import groovy.io.FileType
import org.gradle.api.DefaultTask
import org.gradle.api.tasks.TaskAction

import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream

import static com.github.abrarsyed.gmcp.Util.*

class DecompileMinecraftTask extends DefaultTask
{
    def private final File decompJar = file(temporaryDir, "decompiled.jar")

    def private log(Object obj)
    {
        logger.lifecycle obj.toString()
    }

    @TaskAction
    def doTask()
    {
        log "Decompiling jar"
        decompile()

        log "Extracting and cleaning sources"
        copyClasses()

        log "Apply MCP patches and cleanup"
        doMCPPatches()
        doMCPCleanup()

        log "Formatting sources"
        applyAstyle()

        // TODO: make conditional for fml stuff.
        log "Applying FML tranformations"
        applyFMLModifications()

        log "Renaming sources"
        renameSources()

        log "Applying Forge patches"
        applyForgeModifications()
    }

    def decompile()
    {
        def fernFlower = Util.cacheFile(Constants.FERNFLOWER)

        project.javaexec {
            args(
                    fernFlower.getAbsolutePath(),
                    "-din=0",
                    "-rbr=0",
                    "-dgs=1",
                    "-asc=1",
                    "-log=ERROR",
                    Util.file(Constants.JAR_PROC).getAbsolutePath(),
                    decompJar.getAbsolutePath()
            );

            setMain "-jar"
            setWorkingDir fernFlower.getParentFile()

            classpath Util.getClassPath()

            setStandardOutput Util.getNullStream()
        }
    }

    def copyClasses()
    {
        final ZipInputStream zin = new ZipInputStream(decompJar.newInputStream());
        ZipEntry entry = null;

        def out;
        def srcDir = srcFile(Constants.DIR_SRC_MINECRAFT)
        def resDir = srcFile(Constants.DIR_SRC_RESOURCES)

        while ((entry = zin.getNextEntry()) != null)
        {
            // no META or dirs. wel take care of dirs later.
            if (entry.getName().contains("META-INF"))
            {
                continue;
            }

            // resources or directories.
            if (entry.isDirectory() || !entry.getName().endsWith(".java"))
            {
                out = file(resDir, entry.getName())
                out.getParentFile().mkdirs();
                out.delete();
                out.createNewFile();
                out << zin;
                zin.closeEntry();

            }
            else
            {
                // source!

                // get the text
                fileStr = zin.text
                zin.closeEntry();

                // fix
                fileStr = FFPatcher.processFile(new File(entry.getName()).getName(), fileStr);

                // write it.
                out = file(srcDir, entry.getName())
                out.getParentFile().mkdirs();
                out.write(fileStr)
            }
        }

        zin.close();

//        def tree = project.fileTree(decompiledDir)
//
//        // copy classes
//        project.mkdir(srcFile(Constants.DIR_SRC_MINECRAFT))
//        project.copy {
//            exclude "META-INF"
//            from(tree) { include "net/minecraft/**/*.java" }
//            into srcFile(Constants.DIR_SRC_MINECRAFT)
//        }
//
//        // copy resources
//        project.mkdir(srcFile(Constants.DIR_SRC_RESOURCES))
//        project.copy {
//            exclude "*.java"
//            exclude "**/*.java"
//            exclude "*.class"
//            exclude "**/*.class"
//            exclude "META-INF"
//            from tree
//            into srcFile(Constants.DIR_SRC_RESOURCES)
//            includeEmptyDirs = false
//        }
    }

    def doMCPPatches()
    {
        // fix the patch first.
        String text = cacheFile(String.format(Constants.FMED_PACKAGED_PATCH, project.minecraft.minecraftversion)).text

        // fix newlines
        text = text.replaceAll("(\r\n|\r|\n)", Constants.NEWLINE).replaceAll("(\\r\\n|\\r|\\n)", Constants.NEWLINE)

        // fixing for the paths.
        text = text.replaceAll("minecraft\\\\(net\\\\minecraft)", '$1')

        def tempPatch = new File(getTemporaryDir(), "patch");
        tempPatch.write(text)

        // actually do the patches now.
        ContextualPatch cPatch = ContextualPatch.create(tempPatch, srcFile(Constants.DIR_SRC_MINECRAFT));
        List<ContextualPatch.PatchReport> reports = cPatch.patch(true);
        for (ContextualPatch.PatchReport report : reports)
        {
            getLogger().info(report.getStatus() + "  -- " + report.getFile());
            if (report.getStatus() != ContextualPatch.PatchStatus.Patched)
            {
                getLogger().info("ERROR: ", report.getFailure());
            }
        }

        // copy over start.java
        Files.copy(baseFile(Constants.DIR_MCP_PATCHES, "Start.java"), srcFile(Constants.DIR_SRC_MINECRAFT, "Start.java"))
    }

    def doMCPCleanup()
    {
        srcFile(Constants.DIR_SRC_MINECRAFT).eachFileRecurse(FileType.FILES) {

            getLogger().debug("Processing file: " + it);
            String text = it.text

            getLogger().debug("processing comments");
            text = McpCleanup.stripComments(text);

            getLogger().debug("fixing imports comments");
            text = McpCleanup.fixImports(text);

            getLogger().debug("various other cleanup");
            text = McpCleanup.cleanup(text);

            getLogger().debug("fixing OGL constants");
            text = GLConstantFixer.fixOGL(text);

            getLogger().debug("Writing file");
            it.write(text)
        }
    }

    def applyAstyle()
    {
        def formatter = new ASFormatter();
        OptParser parser = new OptParser(formatter);

        def config = baseFile(Constants.DIR_MAPPINGS, "astyle.cfg")
        getLogger().info("Parsing astyle options file: " + config);
        parser.parseOptionFile(config);

        srcFile(Constants.DIR_SRC_MINECRAFT).eachFileRecurse(FileType.FILES) {
            logger.debug("Formatting file: " + it);
            formatter.formatFile(it);
        }

    }

    def applyFMLModifications()
    {
        srcFile(Constants.DIR_SRC_MINECRAFT).eachFileRecurse(FileType.FILES) {
            def text = it.text

            // do FML fixes...
            text = FMLCleanup.updateFile(text)

            // ensure line endings
            text = text.replaceAll("(\r\n|\n|\r)", Constants.NEWLINE)
            text = text.replaceAll(/(\r\n|\n|\r)/, Constants.NEWLINE)

            // write text
            it.write(text)
        }

        PatchTask.patchStuff(baseFile(Constants.DIR_FML_PATCHES),
                srcFile(Constants.DIR_SRC_MINECRAFT),
                baseFile(Constants.DIR_LOGS, "FMLPatches.log"),
                file(temporaryDir, 'temp.patch')
        )

        def common = project.fileTree(baseFile(Constants.DIR_FML, "common"))
        def client = project.fileTree(baseFile(Constants.DIR_FML, "client"))

        // copy classes
        project.mkdir(srcFile(Constants.DIR_SRC_FML))
        project.copy {
            exclude "META-INF"
            from(common) { include "**/*.java" }
            from(client) { include "**/*.java" }
            into srcFile(Constants.DIR_SRC_FML)
        }

        // copy resources
        project.mkdir(srcFile(Constants.DIR_SRC_RESOURCES))
        project.copy {
            exclude "*.java"
            exclude "**/*.java"
            exclude "*.class"
            exclude "**/*.class"
            exclude "META-INF"
            from common
            from client
            into srcFile(Constants.DIR_SRC_RESOURCES)
            includeEmptyDirs = false
        }
    }

    def renameSources()
    {
        def files = Constants.CSVS.collectEntries { key, value ->
            [
                    key,
                    baseFile(Constants.DIR_MAPPINGS, value)
            ]
        }
        def remapper = new SourceRemapper(files)

        srcFile(Constants.DIR_SRC_MINECRAFT).eachFileRecurse(FileType.FILES) { remapper.remapFile(it) }
        srcFile(Constants.DIR_SRC_FML).eachFileRecurse(FileType.FILES) { remapper.remapFile(it) }
    }

    def applyForgeModifications()
    {
        PatchTask.patchStuff(baseFile(Constants.DIR_FORGE_PATCHES),
                srcFile(Constants.DIR_SRC_MINECRAFT),
                baseFile(Constants.DIR_LOGS, "ForgePatches.log"),
                file(temporaryDir, 'temp.patch')
        )

        def common = project.fileTree(baseFile(Constants.DIR_FORGE, "common"))
        def client = project.fileTree(baseFile(Constants.DIR_FORGE, "client"))

        // copy classes
        project.mkdir(srcFile(Constants.DIR_SRC_FML))
        project.copy {
            exclude "META-INF"
            from(common) { include "**/*.java" }
            from(client) { include "**/*.java" }
            into srcFile(Constants.DIR_SRC_FORGE)
        }

        // copy resources
        project.copy {
            exclude "*.java"
            exclude "**/*.java"
            exclude "*.class"
            exclude "**/*.class"
            exclude "META-INF"
            from common
            from client
            into srcFile(Constants.DIR_SRC_RESOURCES)
            includeEmptyDirs = false
        }
    }
}
