package com.github.abrarsyed.gmcp

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

import org.eclipse.jdt.core.ToolFactory
import org.eclipse.jdt.core.formatter.CodeFormatter
import org.eclipse.jface.text.Document
import org.eclipse.text.edits.TextEdit
import org.gradle.api.Plugin
import org.gradle.api.Project

import com.github.abrarsyed.gmcp.Constants.OperatingSystem
import com.github.abrarsyed.gmcp.extensions.GMCPExtension
import com.github.abrarsyed.gmcp.extensions.ModInfoExtension
import com.github.abrarsyed.gmcp.source.FFPatcher
import com.github.abrarsyed.gmcp.source.MCPCleanup
import com.github.abrarsyed.gmcp.tasks.PatchTask
import com.google.common.io.Files

import cpw.mods.fml.common.asm.transformers.MCPMerger
import de.fernflower.main.decompiler.ConsoleDecompiler

public class GMCP implements Plugin<Project>
{
    //public GMCPExtension ext
    public static OperatingSystem os = Util.getOS()
    public static Project project

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

        // start the tasks
        downloadTasks()
        jarTasks()
        sourceTasks()
    }

    def downloadTasks()
    {
        // Get Forge task
        def task = project.task('getForge') {
            description = "Downloads the correct version of Forge"
            group = "minecraft"
            outputs.dir { Util.baseFile(Constants.DIR_FORGE) }
            outputs.upToDateWhen {
                def file = Util.baseFile("forge", "forgeversion.properties")
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
            inputs.file { Util.baseFile(Constants.DIR_FML, "mc_versions.cfg") }
            outputs.with {
                file { Util.jarFile(Constants.JAR_JAR_CLIENT) }
                file { Util.jarFile(Constants.JAR_JAR_SERVER) }
                file { Util.jarFile("bin", "lwjgl.jar") }
                file { Util.jarFile("bin", "lwjgl_util.jar") }
                file { Util.jarFile("bin", "jinput.jar") }
                dir { Util.jarFile("bin", "natives") }
            }
        }
        task << {
            new File(project.minecraft.jarDir).mkdirs()
            def root = Util.jarFile(Constants.DIR_JAR_BIN)
            root.mkdirs()

            // read config
            ConfigParser parser = new ConfigParser(Util.baseFile(Constants.DIR_FML, "mc_versions.cfg"))
            def baseUrl = parser.getProperty("default", "base_url")

            def mcver = parser.getProperty("default", "current_ver")
            Util.download(parser.getProperty(mcver, "client_url"), Util.jarFile(Constants.JAR_JAR_CLIENT))
            Util.download(parser.getProperty(mcver, "server_url"), Util.jarFile(Constants.JAR_JAR_SERVER))

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
            inputs.dir { Util.baseFile(Constants.DIR_FML, "conf") }

            outputs.with {
                file { Util.baseFile(Constants.DIR_MAPPINGS, "packaged.srg") }
                file  { Util.baseFile(Constants.DIR_MAPPINGS, "packaged.exc") }
                file { Util.baseFile(Constants.DIR_MCP_PATCHES, "minecraft_ff.patch") }
            }

        }
        task << {
            // copy files over.
            project.copy {
                from Util.baseFile(Constants.DIR_FML, "conf")
                into Util.baseFile(Constants.DIR_MAPPINGS)
            }

            // gotta love groovy  and its .with closure :)
            (new PackageFixer(Util.baseFile(Constants.DIR_MAPPINGS, Constants.CSVS["packages"]))).with {
                // calls the following on the package fixer.
                fixSRG(Util.baseFile(Constants.DIR_MAPPINGS, "joined.srg"), Util.baseFile(Constants.DIR_MAPPINGS, "packaged.srg"))
                fixExceptor(Util.baseFile(Constants.DIR_MAPPINGS, "joined.exc"), Util.baseFile(Constants.DIR_MAPPINGS, "packaged.exc"))
                fixPatch(Util.baseFile(Constants.DIR_MCP_PATCHES, "minecraft_ff.patch"))
                fixPatch(Util.baseFile(Constants.DIR_MCP_PATCHES, "minecraft_server_ff.patch"))
            }
        }

        // ----------------------------------------------------------------------------
        // any other things I need to downlaod from github or otherwise...
        task = project.task('extractMisc') {
            outputs.dir Util.baseFile(Constants.DIR_MISC)
        }
        task << {
            Util.baseFile(Constants.DIR_MISC).mkdirs()

            InputStream stream = this.getClass().classLoader.getResourceAsStream(Constants.REC_FORMAT_CFG)
            Util.baseFile(Constants.CFG_FORMAT) << stream.getBytes()

            stream = this.getClass().classLoader.getResourceAsStream(Constants.REC_PATCH_EXEC)
            Util.baseFile(Constants.EXEC_WIN_PATCH) << stream.getBytes()
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
                file { Util.jarFile(Constants.JAR_JAR_CLIENT) }
                file { Util.jarFile(Constants.JAR_JAR_SERVER) }
                file { Util.baseFile(Constants.DIR_FML, "mcp_merge.cfg") }
                file { Util.baseFile(Constants.DIR_MAPPINGS, "packaged.srg") }
                file { Util.baseFile(Constants.DIR_FML, "common/fml_at.cfg") }
                file { Util.baseFile(Constants.DIR_FORGE, "common/forge_at.cfg") }
                project.minecraft.accessTransformers.collect { String str -> file {str} }
            }

            outputs.with {
                file { Util.baseFile(Constants.JAR_PROC) }
            }
        }
        task.dependsOn("getMinecraft", "doFMLMappingPreProcess")
        // merge jars
        task << {
            def server = Util.file(temporaryDir, "server.jar")
            def merged = Util.file(temporaryDir, "merged.jar")
            def mergeTemp = Util.file(temporaryDir, "merged.jar.tmp")

            Files.copy(Util.jarFile(Constants.JAR_JAR_CLIENT), mergeTemp)
            Files.copy(Util.jarFile(Constants.JAR_JAR_CLIENT), server)

            logger.lifecycle "Merging jars"

            //Constants.JAR_CLIENT, Constants.JAR_SERVER
            String[] args = new String[3]
            args[0] = [
                project.minecraft.baseDir,
                Constants.DIR_FML,
                "mcp_merge.cfg"
            ].join "/"
            args[1] = mergeTemp.getPath()
            args[2] = server.getPath()
            MCPMerger.main(args)

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
            mapping.loadMappings(Util.baseFile(Constants.DIR_MAPPINGS, "packaged.srg"))

            // load in AT
            def accessMap = new AccessMap()
            accessMap.loadAccessTransformer(Util.baseFile(Constants.DIR_FML, "common/fml_at.cfg"))
            accessMap.loadAccessTransformer(Util.baseFile(Constants.DIR_FORGE, "common/forge_at.cfg"))
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

            Util.baseFile(Constants.DIR_LOGS).mkdirs()
            String[] args = new String[4]
            args[0] = Util.file(temporaryDir, "deobf.jar").getPath()
            args[1] = Util.baseFile(Constants.JAR_PROC).getPath()
            args[2] = Util.baseFile(Constants.DIR_MAPPINGS, "packaged.exc")
            args[3] = Util.baseFile(Constants.DIR_LOGS, "MCInjector.log").getPath()

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
                file {Util.baseFile(Constants.JAR_PROC)}
                file {Util.baseFile(Constants.DIR_MAPPINGS, "astyle.cfg")}
                dir {Util.baseFile(Constants.DIR_MCP_PATCHES)}
            }

            outputs.dir {Util.srcFile(Constants.DIR_SRC_RESOURCES)}
            outputs.dir {Util.srcFile(Constants.DIR_SRC_SOURCES)}

            dependsOn "extractMisc"
        }
        task << {
            // unzip
            def unzippedDir = Util.file(temporaryDir, "unzipped")
            def decompiledDir = Util.file(temporaryDir, "decompiled")
            def recDir = Util.srcFile(Constants.DIR_SRC_RESOURCES)
            def srcDir = Util.srcFile(Constants.DIR_SRC_SOURCES)

            logger.info "Unpacking jar"
            project.mkdir(unzippedDir)
            project.copy {
                from project.zipTree(Util.baseFile(Constants.JAR_PROC))
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
                def log = Util.baseFile(Constants.DIR_LOGS, "FF.log")
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
            FFPatcher.processDir(Util.srcFile(Constants.DIR_SRC_SOURCES))

            // copy patch, and fix lines
            def text = Util.baseFile(Constants.DIR_MCP_PATCHES, "/minecraft_ff.patch").text
            text = text.replaceAll("(\r\n|\r|\n)", System.getProperty("line.separator"))
            text = text.replaceAll(/(\r\n|\r|\n)/, System.getProperty("line.separator"))
            def patch = Util.file(temporaryDir, "patch")
            patch.write(text)

            logger.info "applying MCP patches"
            def result = project.exec {
                if (os == Constants.OperatingSystem.WINDOWS)
                    executable = Util.baseFile(Constants.EXEC_WIN_PATCH).getPath()
                else
                    executable = "patch"

                def log = Util.baseFile(Constants.DIR_LOGS, "MCPPatches.log")
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
                    '"'+Util.srcFile(Constants.DIR_SRC_SOURCES).getPath()+'"'
                ]
            }
        }

    }

    def sourceTasks()
    {
        def task = project.task("processMCSources", dependsOn: "decompileMinecraft") {
            inputs.with {
                dir {Util.srcFile(Constants.DIR_SRC_SOURCES)}
                Constants.CSVS.each {
                    file {Util.baseFile(Constants.DIR_MAPPINGS, it)}
                }
                file {Util.baseFile(Constants.DIR_MAPPINGS, "astyle.cfg")}
                dir {Util.baseFile(Constants.DIR_FML_PATCHES)}
                dir {Util.baseFile(Constants.DIR_FORGE_PATCHES)}
            }
            outputs.dir {Util.srcFile(Constants.DIR_SRC_SOURCES)}

            dependsOn "extractMisc"
        }
        // do random source stuff
        task << {
            def srcDir = Util.srcFile(Constants.DIR_SRC_SOURCES)

            // set up formatter
            def config = [:]

            Util.baseFile(Constants.CFG_FORMAT).eachLine {
                if (it == null || it.isEmpty())
                    return

                def split = it.split("=")
                config[split[0].trim()] = split[1].trim()
            }

            CodeFormatter formatter = ToolFactory.createCodeFormatter(config)

            srcDir.eachFileRecurse {
                // lose the folders already.
                if (it.isDirectory())
                    return

                def text = it.text

                // pre-formatting cleanup
                text = MCPCleanup.cleanFile(text)

                // format
                TextEdit te = formatter.format(CodeFormatter.K_COMPILATION_UNIT, text, 0, text.length(), 0, null)
                Document doc = new Document(text)
                te?.apply(doc)
                text = doc.get()

                // post-format fixes for empty methods
                text = text.replaceAll(/(?m)(^\s+(?:\w+ )+\w+\([\w\d ,]*?\))(?:\r\n|\r|\n)^\s*\{\s*\}/, '$1 {}')
                
                // write text
                it.write(text)
            }
        }

        task = project.task("doFMLPatches", type:PatchTask, dependsOn: "processMCSources") {
            patchDir = Util.baseFile(Constants.DIR_FML_PATCHES)
            srcDir = Util.srcFile(Constants.DIR_SRC_SOURCES)
        }
    }
}