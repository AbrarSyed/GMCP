package com.github.abrarsyed.gmcp.tasks

import static com.github.abrarsyed.gmcp.Util.baseFile
import static com.github.abrarsyed.gmcp.Util.file
import static com.github.abrarsyed.gmcp.Util.srcFile
import groovy.io.FileType

import org.gradle.api.DefaultTask
import org.gradle.api.Project
import org.gradle.api.tasks.TaskAction

import com.github.abrarsyed.gmcp.Constants
import com.github.abrarsyed.gmcp.GMCP
import com.github.abrarsyed.gmcp.Constants.OperatingSystem
import com.github.abrarsyed.gmcp.source.FFPatcher
import com.github.abrarsyed.gmcp.source.FMLCleanup
import com.github.abrarsyed.gmcp.source.MCPCleanup
import com.github.abrarsyed.gmcp.source.SourceRemapper
import com.google.common.io.Files

import de.fernflower.main.decompiler.ConsoleDecompiler

class DecompileMinecraftTask extends DefaultTask
{
    private static Project project = GMCP.project
    def private final File unzippedDir = file(temporaryDir, "unzipped")
    def private final File decompiledDir = file(temporaryDir, "decompiled")

    def private log(Object obj)
    {
        logger.lifecycle obj.toString()
    }

    @TaskAction
    def doTask()
    {
        log "Unpacking jar"
        extractJar()

        log "Decompiling classes"
        decompile()

        log "Copying classes"
        copyClasses()

        log "Applying post-decompile cleaning and fixes"
        FFPatcher.processDir(srcFile(Constants.DIR_SRC_MINECRAFT))
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

    def void extractJar()
    {
        project.mkdir(unzippedDir)
        def temp = unzippedDir
        project.copy
        {
            from project.zipTree(baseFile(Constants.JAR_PROC))
            into temp
            exclude "**/*/META-INF*"
            exclude "META-INF"
        }
    }

    def decompile()
    {
        project.mkdir(decompiledDir)

        String[] args = new String[7]
        args[0] = "-din=0"
        args[1] = "-rbr=0"
        args[2] = "-dgs=1"
        args[3] = "-asc=1"
        args[4] = "-log=ERROR"
        args[5] = unzippedDir.getPath()
        args[6] = decompiledDir.getPath()

        try
        {
            PrintStream stream = System.out
            def log = baseFile(Constants.DIR_LOGS, "FF.log")
            project.file log
            System.setOut(new PrintStream(log))

            ConsoleDecompiler.main(args)
            // -din=0 -rbr=0 -dgs=1 -asc=1 -log=WARN {indir} {outdir}

            System.setOut(stream)
        }
        catch (Exception e)
        {
            project.logger.error "Fernflower failed"
            e.printStackTrace()
        }
    }

    def copyClasses()
    {
        def tree = project.fileTree(decompiledDir)

        // copy classes
        project.mkdir(srcFile(Constants.DIR_SRC_MINECRAFT))
        project.copy {
            exclude "META-INF"
            from (tree) { include "net/minecraft/**/*.java" }
            into srcFile(Constants.DIR_SRC_MINECRAFT)
        }

        // copy resources
        project.mkdir(srcFile(Constants.DIR_SRC_RESOURCES))
        project.copy {
            exclude "*.java"
            exclude "**/*.java"
            exclude "*.class"
            exclude "**/*.class"
            exclude "META-INF"
            from tree
            into srcFile(Constants.DIR_SRC_RESOURCES)
            includeEmptyDirs = false
        }
    }

    def doMCPPatches()
    {
        // copy patch, and fix lines
        def text = baseFile(Constants.DIR_MCP_PATCHES, "minecraft_ff.patch").text
        text = text.replaceAll("(\r\n|\r|\n)", Constants.NEWLINE)
        text = text.replaceAll(/(\r\n|\r|\n)/, Constants.NEWLINE)
        def patch = file(temporaryDir, "patch")
        patch.write(text)

        def result = project.exec {
            if (GMCP.os == Constants.OperatingSystem.WINDOWS)
                executable = baseFile(Constants.EXEC_WIN_PATCH).getPath()
            else
                executable = "patch"

            def log = baseFile(Constants.DIR_LOGS, "MCPPatches.log")
            project.file log
            def stream = log.newOutputStream()
            standardOutput = stream
            errorOutput = stream

            ignoreExitValue = true

            args = [
                "-p1",
                "-u",
                "-i",
                '"'+patch.getAbsolutePath()+'"',
                "-d",
                '"'+srcFile(Constants.DIR_SRC_MINECRAFT).getPath()+'"'
            ]
        }

        // copy over start.java
        Files.copy(baseFile(Constants.DIR_MCP_PATCHES, "start.java"), srcFile(Constants.DIR_SRC_MINECRAFT, "Start.java"))
    }

    def doMCPCleanup()
    {
        srcFile(Constants.DIR_SRC_MINECRAFT).eachFileRecurse(FileType.FILES) {

            def text = it.text

            // pre-formatting cleanup
            text = MCPCleanup.cleanFile(text)

            // write text
            it.write(text)
        }
    }

    def applyAstyle()
    {
        // run astyle
        project.exec {
            def exec
            switch(GMCP.os)
            {
                case OperatingSystem.LINUX:
                    exec = "astyle"
                    break
                case OperatingSystem.OSX:
                    exec = baseFile(Constants.EXEC_ASTYLE).getPath()
                    break
                case OperatingSystem.WINDOWS:
                    exec = baseFile(Constants.EXEC_ASTYLE + ".exe").getPath()

            }

            // %s --suffix=none --quiet --options={conffile} {classes}
            commandLine = [
                exec,
                "--suffix=none",
                "--quiet",
                "--options="+baseFile(Constants.DIR_MAPPINGS, "astyle.cfg").getPath(),
                "--recursive",
                srcFile(Constants.DIR_SRC_MINECRAFT).getPath()+File.separator+'*.java"'
            ]
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

        PatchTask.patchStuff ( baseFile(Constants.DIR_FML_PATCHES),
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
            from (common) { include "**/*.java" }
            from (client) { include "**/*.java" }
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
        PatchTask.patchStuff ( baseFile(Constants.DIR_FORGE_PATCHES),
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
            from (common) { include "**/*.java" }
            from (client) { include "**/*.java" }
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
