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

import org.gradle.api.Plugin
import org.gradle.api.Project

import com.github.abrarsyed.gmcp.Constants.OperatingSystem
import com.github.abrarsyed.gmcp.extensions.GMCPExtension
import com.github.abrarsyed.gmcp.extensions.ModInfoExtension
import com.google.common.io.Files

import cpw.mods.fml.common.asm.transformers.MCPMerger
import de.fernflower.main.decompiler.ConsoleDecompiler

public class GMCP implements Plugin<Project>
{
    //public GMCPExtension ext
    public OperatingSystem os
    private Project project

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

        // get os
        os = Util.getOS()

        // start the tasks
        downloadTasks()
        jarTasks()
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

            def base = file(project.minecraft.baseDir)
            base.mkdirs()
            def forgeZip = baseFile("forge.zip")
            Util.download(project.minecraft.forgeURL, forgeZip)
            project.copy {
                from project.fileTree(forgeZip)
                into base
            }
            forgeZip.delete()
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

            def nativesJar = baseFile("natives.jar")
            def nativesName = parser.getProperty("default", "natives").split(/\s/)[os.ordinal()]
            Util.download(baseUrl + nativesName, nativesJar)

            project.copy {
                from project.fileTree(nativesJar)
                into file(root, "natives")
            }
            nativesJar.delete()
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
        }
        task.dependsOn("getMinecraft", "doFMLMappingPreProcess")
        // merge jars
        task << {
            def server = file(temporaryDir, "server.jar")
            def merged = file(temporaryDir, "merged.jar")
            def mergeTemp = file(temporaryDir, "merged.jar.tmp")

            Files.copy(jarFile(Constants.JAR_JAR_CLIENT), mergeTemp)
            Files.copy(jarFile(Constants.JAR_JAR_CLIENT), server)

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
            def merged = file(temporaryDir, "merged.jar")
            def deobf = file(temporaryDir, "deobf.jar")

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
            args[0] = file(temporaryDir, "deobf.jar").getPath()
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
            inputs.file {baseFile(Constants.JAR_PROC)}
        }
        task << {
            // unzip
            def unzippedDir = file(temporaryDir, "unzipped")
            def decompiledDir = file(temporaryDir, "decompiled")
            def recDir = file(project.minecraft.srcDir, Constants.DIR_SRC_RESOURCES)
            def srcDir = file(project.minecraft.srcDir, Constants.DIR_SRC_SOURCES)

            project.mkdirs(unzippedDir)
            project.copy {
                from project.fileTree(baseFile(Constants.JAR_PROC))
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


            def tree = project.fileTree(decompiledDir)

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
            }

            // copy classes
            project.mkdir(srcDir)
            project.copy {
                exclude "META-INF"
                from (tree) { include "net/minecraft/**/*.java" }
                into srcDir
            }
        }

    }

    def File file(String... args)
    {
        return new File(args.join("/"))
    }

    def File file(File file, String... args)
    {
        return new File(file, args.join("/"))
    }

    def File baseFile(String... args)
    {
        def arguments = []
        arguments += project.minecraft.baseDir
        arguments.addAll(args)

        return file(arguments as String[])
    }

    def File jarFile(String... args)
    {
        def arguments = []
        arguments += project.minecraft.jarDir
        arguments.addAll(args)

        return file(arguments as String[])
    }

}