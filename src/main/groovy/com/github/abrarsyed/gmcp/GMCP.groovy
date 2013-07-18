package com.github.abrarsyed.gmcp

import static com.github.abrarsyed.gmcp.Util.baseFile
import static com.github.abrarsyed.gmcp.Util.jarFile
import static com.github.abrarsyed.gmcp.Util.srcFile
import groovy.io.FileType

import java.lang.reflect.Method
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream

import net.md_5.specialsource.AccessMap
import net.md_5.specialsource.Jar
import net.md_5.specialsource.JarMapping
import net.md_5.specialsource.JarRemapper
import net.md_5.specialsource.RemapperPreprocessor
import net.md_5.specialsource.provider.JarProvider
import net.md_5.specialsource.provider.JointProvider

import org.gradle.api.Plugin
import org.gradle.api.Project

import com.github.abrarsyed.gmcp.Constants.OperatingSystem
import com.github.abrarsyed.gmcp.extensions.GMCPExtension
import com.github.abrarsyed.gmcp.extensions.ModInfoExtension
import com.github.abrarsyed.gmcp.source.FFPatcher
import com.github.abrarsyed.gmcp.source.FMLCleanup
import com.github.abrarsyed.gmcp.source.MCPCleanup
import com.github.abrarsyed.gmcp.source.SourceRemapper
import com.github.abrarsyed.gmcp.tasks.PatchTask
import com.google.common.io.Files

import cpw.mods.fml.common.asm.transformers.MCPMerger
import de.fernflower.main.decompiler.ConsoleDecompiler

public class GMCP implements Plugin<Project>
{
    //public GMCPExtension ext
    public static OperatingSystem os = Util.getOS()
    public static Project project
    public static dependancies

    @Override
    public void apply(Project project)
    {
        // se project
        this.project = project

        // make extensions and set variables
        project.extensions.create("minecraft", GMCPExtension, this)
        //ext = project.minecraft
        project.minecraft.extensions.create("mcmodinfo", ModInfoExtension)

        // ensure java is in.
        project.apply( plugin: "java")
        project.apply( plugin: "idea")
        project.apply( plugin: "eclipse")

        // manage dependancy configurations
        configureSourceSet()
        doConfigurationStuff()
        configureJarCreation()

        // start the tasks
        downloadTasks()
        jarTasks()
        sourceTasks()
        buildTasks()
    }

    def doConfigurationStuff()
    {
        project.configurations {
            gmcp {
                transitive = true
                visible = false
                description = "GMCP internal configuration. Don't use!"
            }

            provided {
                transitive = true
                visible = true
                description = "Compile time, but not runtime"
            }

            project.sourceSets.minecraft.compileClasspath += gmcp
            project.sourceSets.main.compileClasspath += gmcp
            project.sourceSets.test.compileClasspath += gmcp
            project.idea.module.scopes.COMPILE.plus += gmcp
            project.eclipse.classpath.plusConfigurations += gmcp

            project.sourceSets.minecraft.compileClasspath += provided
            project.sourceSets.main.compileClasspath += provided
            project.sourceSets.test.compileClasspath += provided
            project.idea.module.scopes.PROVIDED.plus += provided
            project.eclipse.classpath.plusConfigurations += provided
        }
    }

    def configureSourceSet()
    {
        project.sourceSets{
            minecraft {
                java {
                    srcDirs {
                        [
                            srcFile(Constants.DIR_SRC_FML),
                            srcFile(Constants.DIR_SRC_FORGE),
                            srcFile(Constants.DIR_SRC_MINECRAFT)
                        ]
                    }
                }
                resources {
                    srcDir {srcFile(Constants.DIR_SRC_RESOURCES)}
                }
            }
        }
    }

    def configureJarCreation()
    {
        project.tasks.jar {
            exclude 'net/minecraft/**', 'net/minecraftforge/**', 'cpw/mods/fml/**'
            exclude { project.fileTree(srcFile(Constants.DIR_SRC_RESOURCES)) }
            exclude {it.file in configurations.gmcp.files}
            exclude {it.file in project.fileTree(srcFile(Constants.DIR_SRC_RESOURCES))}
        }
    }

    def downloadTasks()
    {
        // Get Forge task
        def task = project.task('getForge') {
            description = "Downloads the correct version of Forge"
            group = "minecraft"
            outputs.dir { baseFile(Constants.DIR_FORGE) }
            outputs.upToDateWhen {
                def file = baseFile("forge", "forgeversion.properties")
                if (!file.exists())
                    return false
                def props = new Properties()
                props.load(file.newInputStream())
                def version = String.format("%s.%s.%s.%s", props.get("forge.major.number"), props.get("forge.minor.number"), props.get("forge.revision.number"), props.get("forge.build.number"))
                return project.minecraft.forgeVersion == version
            }
        }
        task << {

            def base = Util.file(project.minecraft.baseDir)
            base.mkdirs()
            def forgeZip = Util.file(temporaryDir, "forge.zip")
            Util.download(project.minecraft.forgeURL, forgeZip)
            project.copy {
                from project.zipTree(forgeZip)
                into base
            }
        }


        // ----------------------------------------------------------------------------
        // download necessary stuff.
        task = project.task('getMinecraft', dependsOn: "getForge") {
            description = "Downloads the correct version of Minecraft and lwJGL and its natives"
            group = "minecraft"
            inputs.file { baseFile(Constants.DIR_FML, "mc_versions.cfg") }
            outputs.with {
                file { jarFile(Constants.JAR_JAR_CLIENT) }
                file { jarFile(Constants.JAR_JAR_SERVER) }
                file { jarFile("bin", "lwjgl.jar") }
                file { jarFile("bin", "lwjgl_util.jar") }
                file { jarFile("bin", "jinput.jar") }
                dir { jarFile("bin", "natives") }
            }
        }
        task << {
            new File(project.minecraft.jarDir).mkdirs()
            def root = jarFile(Constants.DIR_JAR_BIN)
            root.mkdirs()

            // read config
            ConfigParser parser = new ConfigParser(baseFile(Constants.DIR_FML, "mc_versions.cfg"))
            def baseUrl = parser.getProperty("default", "base_url")

            def mcver = parser.getProperty("default", "current_ver")
            Util.download(parser.getProperty(mcver, "client_url"), jarFile(Constants.JAR_JAR_CLIENT))
            Util.download(parser.getProperty(mcver, "server_url"), jarFile(Constants.JAR_JAR_SERVER))

            def dls = parser.getProperty("default", "libraries").split(/\s/)
            dls.each { Util.download(baseUrl+it, new File(root, it)) }

            def nativesJar = Util.file(temporaryDir, "natives.jar")
            def nativesName = parser.getProperty("default", "natives").split(/\s/)[os.ordinal()]
            Util.download(baseUrl + nativesName, nativesJar)

            project.copy {
                from project.zipTree(nativesJar)
                into Util.file(root, "natives")
            }
        }

        // ----------------------------------------------------------------------------
        // to do the package changes
        task = project.task('doFMLMappingPreProcess', dependsOn: "getForge") {
            description = "Copies and updates the mappings and configs with the new package structure"
            group = "minecraft"
            inputs.dir { baseFile(Constants.DIR_FML, "conf") }

            outputs.with {
                file { baseFile(Constants.DIR_MAPPINGS, "packaged.srg") }
                file  { baseFile(Constants.DIR_MAPPINGS, "packaged.exc") }
                file { baseFile(Constants.DIR_MCP_PATCHES, "minecraft_ff.patch") }
            }

        }
        task << {
            // copy files over.
            project.copy {
                from baseFile(Constants.DIR_FML, "conf")
                into baseFile(Constants.DIR_MAPPINGS)
            }

            // gotta love groovy  and its .with closure :)
            (new PackageFixer(baseFile(Constants.DIR_MAPPINGS, Constants.CSVS["packages"]))).with {
                // calls the following on the package fixer.
                fixSRG(baseFile(Constants.DIR_MAPPINGS, "joined.srg"), baseFile(Constants.DIR_MAPPINGS, "packaged.srg"))
                fixExceptor(baseFile(Constants.DIR_MAPPINGS, "joined.exc"), baseFile(Constants.DIR_MAPPINGS, "packaged.exc"))
                fixPatch(baseFile(Constants.DIR_MCP_PATCHES, "minecraft_ff.patch"))
                fixPatch(baseFile(Constants.DIR_MCP_PATCHES, "minecraft_server_ff.patch"))
            }
        }
        task << {
            // generate robf SRG
            SRGCreator.createReobfSrg()
        }

        // ----------------------------------------------------------------------------
        // any other things I need to downlaod from github or otherwise...
        task = project.task('extractMisc') {
            outputs.dir baseFile(Constants.DIR_MISC)
        }
        task << {
            baseFile(Constants.DIR_MISC).mkdirs()

            InputStream stream = this.getClass().classLoader.getResourceAsStream(Constants.REC_FORMAT_CFG)
            baseFile(Constants.CFG_FORMAT) << stream.getBytes()

            stream = this.getClass().classLoader.getResourceAsStream(Constants.REC_PATCH_EXEC)
            baseFile(Constants.EXEC_WIN_PATCH) << stream.getBytes()

            // extract astyle
            def astyleIn = String.format(Constants.REC_ASTYLE_EXEC, os.toString().toLowerCase())
            def astyleOut = Constants.EXEC_ASTYLE
            if (os == OperatingSystem.WINDOWS)
            {
                astyleIn += ".exe"
                astyleOut += ".exe"
            }

            stream = this.getClass().classLoader.getResourceAsStream(astyleIn)
            baseFile(astyleOut) << stream.getBytes()
        }
    }

    def jarTasks()
    {
        // ----------------------------------------------------------------------------
        // deobfuscate and apply exceptor
        def task = project.task("doJarPreProcess") {
            description = "Deobfuscates Minecraft, and applies the Exceptor"
            group = "minecraft"
            inputs.with {
                file { jarFile(Constants.JAR_JAR_CLIENT) }
                file { jarFile(Constants.JAR_JAR_SERVER) }
                file { baseFile(Constants.DIR_FML, "mcp_merge.cfg") }
                file { baseFile(Constants.DIR_MAPPINGS, "packaged.srg") }
                file { baseFile(Constants.DIR_FML, "common/fml_at.cfg") }
                file { baseFile(Constants.DIR_FORGE, "common/forge_at.cfg") }
                project.minecraft.accessTransformers.collect { String str -> file {str} }
            }

            outputs.with {
                file { baseFile(Constants.JAR_PROC) }
            }

            dependsOn "getMinecraft", "doFMLMappingPreProcess"
        }
        // merge jars
        task << {
            def server = Util.file(temporaryDir, "server.jar")
            def merged = Util.file(temporaryDir, "merged.jar")
            def mergeTemp = Util.file(temporaryDir, "merged.jar.tmp")

            Files.copy(jarFile(Constants.JAR_JAR_CLIENT), mergeTemp)
            Files.copy(jarFile(Constants.JAR_JAR_SERVER), server)

            logger.lifecycle "Merging jars"

            //Constants.JAR_CLIENT, Constants.JAR_SERVER
            def args = [
                baseFile(Constants.DIR_FML, "mcp_merge.cfg").getPath(),
                mergeTemp.getPath(),
                server.getPath()
            ]
            MCPMerger.main(args as String[])

            // copy and strip METAINF
            def ZipFile input = new ZipFile(mergeTemp)
            def output = new ZipOutputStream(merged.newDataOutputStream())

            input.entries().each{ ZipEntry entry ->
                if (entry.name.contains("META-INF"))
                    return
                else if (entry.size > 0)
                {
                    output.putNextEntry(entry)
                    output.write(input.getInputStream(entry).bytes)
                    output.closeEntry()
                }
            }

            input.close()
            output.close()
        }
        // deobfuscate---------------------------
        task << {
            def merged = Util.file(temporaryDir, "merged.jar")
            def deobf = Util.file(temporaryDir, "deobf.jar")

            logger.lifecycle "DeObfuscating jar"

            // load mapping
            JarMapping mapping = new JarMapping()
            mapping.loadMappings(baseFile(Constants.DIR_MAPPINGS, "packaged.srg"))

            // load in AT
            def accessMap = new AccessMap()
            accessMap.loadAccessTransformer(baseFile(Constants.DIR_FML, "common/fml_at.cfg"))
            accessMap.loadAccessTransformer(baseFile(Constants.DIR_FORGE, "common/forge_at.cfg"))
            project.minecraft.accessTransformers.collect {
                accessMap.loadAccessTransformer(project.file(Constants.DIR_FORGE, "common/forge_at.cfg"))
            }
            def processor = new  RemapperPreprocessor(null, mapping, accessMap)

            // make remapper
            JarRemapper remapper = new JarRemapper(processor, mapping)

            // load jar
            Jar input = Jar.init(merged)

            // ensure that inheritance provider is used
            JointProvider inheritanceProviders = new JointProvider()
            inheritanceProviders.add(new JarProvider(input))
            mapping.setFallbackInheritanceProvider(inheritanceProviders)

            // remap jar
            remapper.remapJar(input, deobf)
        }
        // apply exceptor------------------------
        task << {

            logger.lifecycle "Applying Exceptor to jar"

            baseFile(Constants.DIR_LOGS).mkdirs()
            String[] args = new String[4]
            args[0] = Util.file(temporaryDir, "deobf.jar").getPath()
            args[1] = baseFile(Constants.JAR_PROC).getPath()
            args[2] = baseFile(Constants.DIR_MAPPINGS, "packaged.exc")
            args[3] = baseFile(Constants.DIR_LOGS, "MCInjector.log").getPath()

            try
            {
                Class c = Class.forName("MCInjector", true, getClass().classLoader)
                Method m = c.getMethod("main", String[].class)
                m.invoke(null, [args] as Object[])
            }
            catch (Exception e)
            {
                logger.error "MCInjector has failed!"
                e.printStackTrace()
            }
        }

        // ----------------------------------------------------------------------------
        // decompile
        task = project.task("decompileMinecraft", dependsOn: "doJarPreProcess") {
            inputs.with {
                file {baseFile(Constants.JAR_PROC)}
                file {baseFile(Constants.DIR_MAPPINGS, "astyle.cfg")}
                dir {baseFile(Constants.DIR_MCP_PATCHES)}
            }

            outputs.dir {srcFile(Constants.DIR_SRC_RESOURCES)}
            outputs.dir {srcFile(Constants.DIR_SRC_MINECRAFT)}

            dependsOn "extractMisc"
        }
        task << {
            // unzip
            def unzippedDir = Util.file(temporaryDir, "unzipped")
            def decompiledDir = Util.file(temporaryDir, "decompiled")
            def recDir = srcFile(Constants.DIR_SRC_RESOURCES)
            def srcDir = srcFile(Constants.DIR_SRC_MINECRAFT)

            logger.info "Unpacking jar"
            project.mkdir(unzippedDir)
            project.copy {
                from project.zipTree(baseFile(Constants.JAR_PROC))
                into unzippedDir
                exclude "**/*/META-INF*"
                exclude "META-INF"
            }

            // decompile.
            project.mkdir(decompiledDir)
            // JarBouncer.fernFlower(Constants.DIR_CLASSES.getPath(), Constants.DIR_SOURCES.getPath())
            String[] args = new String[7]
            args[0] = "-din=0"
            args[1] = "-rbr=0"
            args[2] = "-dgs=1"
            args[3] = "-asc=1"
            args[4] = "-log=ERROR"
            args[5] = unzippedDir.getPath()
            args[6] = decompiledDir.getPath()

            logger.info "Applying fernflower"
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

            logger.info "Copying classes"

            def tree = project.fileTree(decompiledDir)

            // copy classes
            project.mkdir(srcDir)
            project.copy {
                exclude "META-INF"
                from (tree) { include "net/minecraft/**/*.java" }
                into srcDir
            }

            // copy resources
            project.mkdir(recDir)
            project.copy {
                exclude "*.java"
                exclude "**/*.java"
                exclude "*.class"
                exclude "**/*.class"
                exclude "META-INF"
                from tree
                into recDir
                includeEmptyDirs = false
            }
        }
        task << {
            logger.info "Applying FernFlower fixes"
            FFPatcher.processDir(srcFile(Constants.DIR_SRC_MINECRAFT))

            // copy patch, and fix lines
            def text = baseFile(Constants.DIR_MCP_PATCHES, "/minecraft_ff.patch").text
            text = text.replaceAll("(\r\n|\r|\n)", Constants.NEWLINE)
            text = text.replaceAll(/(\r\n|\r|\n)/, Constants.NEWLINE)
            def patch = Util.file(temporaryDir, "patch")
            patch.write(text)

            logger.info "applying MCP patches"
            def result = project.exec {
                if (os == Constants.OperatingSystem.WINDOWS)
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
        }
        task << {
            srcFile(Constants.DIR_SRC_MINECRAFT).eachFileRecurse(FileType.FILES) {

                def text = it.text

                // pre-formatting cleanup
                text = MCPCleanup.cleanFile(text)

                // write text
                it.write(text)
            }
        }

    }

    def sourceTasks()
    {
        // ----------------------------------------------------------------------------
        // Process MC sources, format and stuff
        def task = project.task("processMCSources", dependsOn: "decompileMinecraft") {
            inputs.dir {srcFile(Constants.DIR_SRC_MINECRAFT)}
            inputs.file {baseFile(Constants.DIR_MAPPINGS, "astyle.cfg")}
            outputs.dir {srcFile(Constants.DIR_SRC_MINECRAFT)}

            dependsOn "extractMisc"
        }
        // do random source stuff
        task << {
            def srcDir = srcFile(Constants.DIR_SRC_MINECRAFT)

            // run astyle
            project.exec {
                def exec
                switch(os)
                {
                    case OperatingSystem.LINUX:
                        exec = "astyle"
                        break
                    case OperatingSystem.MAC:
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
                    srcDir.getPath()+File.separator+'*.java"'
                ]
            }

            srcDir.eachFileRecurse(FileType.FILES) {
                def text = it.text

                // do FML fixes...
                text = FMLCleanup.updateFile(text)

                // ensure line endings
                text = text.replaceAll("(\r\n|\n|\r)", Constants.NEWLINE)
                text = text.replaceAll(/(\r\n|\n|\r)/, Constants.NEWLINE)

                // write text
                it.write(text)
            }
        }

        // ----------------------------------------------------------------------------
        // apply the renamer.
        task = project.task("renameSources", dependsOn: "decompileMinecraft") {
            inputs.dir {srcFile(Constants.DIR_SRC_MINECRAFT)}
            outputs.dir {srcFile(Constants.DIR_SRC_MINECRAFT)}
        }
        task << {
            def files = Constants.CSVS.collectEntries { key, value ->
                [
                    key,
                    baseFile(Constants.DIR_MAPPINGS, value)
                ]
            }
            def remapper = new SourceRemapper(files)

            srcFile(Constants.DIR_SRC_MINECRAFT).eachFileRecurse(FileType.FILES) { remapper.remapFile(it) }

        }

        // ----------------------------------------------------------------------------
        // do forge and FML patches
        task = project.task("doFMLPatches", type: PatchTask, dependsOn: "processMCSources") {
            patchDir = baseFile(Constants.DIR_FML_PATCHES)
            srcDir = srcFile(Constants.DIR_SRC_MINECRAFT)
            logFile = baseFile(Constants.DIR_LOGS, "FMLPatches.log")
        }

        task = project.task("doForgePatches", type: PatchTask, dependsOn: "doFMLPatches") {
            patchDir = baseFile(Constants.DIR_FORGE_PATCHES)
            srcDir = srcFile(Constants.DIR_SRC_MINECRAFT)
            logFile = baseFile(Constants.DIR_LOGS, "ForgePatches.log")

            dependsOn "renameSources"
        }
    }

    def buildTasks()
    {

    }
}