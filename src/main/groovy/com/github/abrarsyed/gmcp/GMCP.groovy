package com.github.abrarsyed.gmcp

import static com.github.abrarsyed.gmcp.Util.baseFile
import static com.github.abrarsyed.gmcp.Util.jarFile
import static com.github.abrarsyed.gmcp.Util.jarVersionFile
import static com.github.abrarsyed.gmcp.Util.srcFile
import groovy.xml.StreamingMarkupBuilder
import groovy.xml.XmlUtil

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
import org.gradle.api.Task

import com.github.abrarsyed.gmcp.Constants.OperatingSystem
import com.github.abrarsyed.gmcp.extensions.GMCPExtension
import com.github.abrarsyed.gmcp.extensions.ModInfoExtension
import com.github.abrarsyed.gmcp.tasks.DecompileMinecraftTask
import com.github.abrarsyed.gmcp.tasks.DownloadMinecraftTask
import com.google.common.io.Files

import cpw.mods.fml.common.asm.transformers.MCPMerger

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
        project.apply( plugin: "idea")
        project.apply( plugin: "eclipse")

        // manage dependancy configurations
        configureSourceSet()
        doConfigurationStuff()
        configureCompilation()

        // final resolving.
        doResolving()

        // start the tasks
        downloadTasks()
        nativesUnpackTask()
        jarTasks()
        decompileTask()

        // IDE stuff
        configureEclipse()

        // replace normal jar task with mine.
        project.tasks.jar << reobfJarClosure()
        project.tasks.jar.dependsOn('doJarPreProcess')
    }

    def doResolving()
    {
        project.with {
            afterEvaluate {

                minecraft.resolveVersion(false)
                minecraft.resolveSrcDir()
                minecraft.resolveJarDir()

                def mcver = minecraft.minecraftVersion
                def is152Minus = minecraft.is152OrLess()
                
                // read 1.6 json
                def json16 = null
                if (!is152Minus)
                {
                    json16 = new Json16Reader(mcver)
                    json16.parseJson()
                }

                // yay for maven central.
                repositories {

                    mavenCentral()

                    if (!is152Minus)
                    {
                        mavenRepo name: "minecraft_"+mcver, url: "http://s3.amazonaws.com/Minecraft.Download/libraries"
                    }
                }

                // dependancy management.
                dependencies
                {

                    if (is152Minus)
                    {
                        // 1.5.2-

                        for (dep in Constants.DEP_152_MINUS)
                            gmcp dep
                            
                        gmcp files(Util.jarFile(Constants.JAR_JAR_CLIENT).getPath())
                        gmcpNative files(Util.jarFile(Constants.DIR_JAR_BIN, 'natives.jar').getPath())

                    }
                    else
                    {
                        // 1.6+
                        
                        for (dep in json16.libs)
                            gmcp dep
                        
                        for (dep in json16.nativeLibs)
                            gmcpNative dep
                        
                        gmcp files(Util.jarVersionFile(Constants.JAR_JAR16_CLIENT).getPath())
                    }
                }
                
                // minectaft download configuration
                tasks.getMinecraft {
                    json = json16
                }
            }
        }
    }

    def doConfigurationStuff()
    {
        project.configurations {
            gmcp {
                transitive = true
                visible = false
                description = "GMCP internal configuration. Don't use!"
            }
            
            gmcpNative {
                transitive = false
                visible = false
                description = "GMCP internal configuration. Don't use!"
            }

            minecraftCompile.extendsFrom gmcp

            project.sourceSets.main.compileClasspath += gmcp
            project.sourceSets.test.compileClasspath += gmcp
            project.idea.module.scopes.COMPILE.plus += gmcp
            project.eclipse.classpath.plusConfigurations += gmcp
        }
    }

    def configureSourceSet()
    {
        project.sourceSets
        {
            minecraft
            {
                java
                {
                    srcDir {srcFile(Constants.DIR_SRC_MINECRAFT)}
                    srcDir {srcFile(Constants.DIR_SRC_FORGE)}
                    srcDir {srcFile(Constants.DIR_SRC_FML)}
                }
                resources {
                    srcDir {srcFile(Constants.DIR_SRC_RESOURCES)}
                }
            }

            main {
                java {
                    // TODO make conditional for using agaricus's lib
                    compileClasspath += minecraft.output
                }
            }
        }
    }

    def configureCompilation()
    {
        project.compileMinecraftJava {
            dependsOn 'decompileMinecraft'
            options.warnings = false
            targetCompatibility = '1.6'
            sourceCompatibility = '1.6'
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
        task = project.task('getMinecraft', dependsOn: "getForge", type: DownloadMinecraftTask) {
            description = "Downloads the correct version of Minecraft and lwJGL and its natives"
            group = "minecraft"
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
            if (os != OperatingSystem.LINUX)
            {
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
    }
    
    def nativesUnpackTask()
    {
        def task = project.task("unpackNatives", dependsOn: 'getMinecraft') {
            inputs.files { project.configurations.gmcpNative }
            outputs.dir { jarFile(Constants.DIR_JAR_BIN, 'natives')}
            
            doLast {
                project.copy {
                    project.configurations.gmcpNative.resolvedConfiguration.resolvedArtifacts.each {
                        from project.zipTree(it.file)
                    }
                    
                    into jarFile(Constants.DIR_JAR_BIN, 'natives')
                }
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
                file { project.minecraft.is152OrLess() ? jarFile(Constants.JAR_JAR_CLIENT_BAK) : jarVersionFile(Constants.JAR_JAR16_CLIENT_BAK)}
                file { jarFile(Constants.JAR_JAR_SERVER) }
                file { baseFile(Constants.DIR_FML, "mcp_merge.cfg") }
                file { baseFile(Constants.DIR_MAPPINGS, "packaged.srg") }
                file { baseFile(Constants.DIR_FML, "common/fml_at.cfg") }
                file { baseFile(Constants.DIR_FORGE, "common/forge_at.cfg") }
                project.minecraft.accessTransformers.collect { String str -> file {str} }
            }

            outputs.with {
                file { project.minecraft.is152OrLess() ? jarFile(Constants.JAR_JAR_CLIENT) : jarVersionFile(Constants.JAR_JAR16_CLIENT)}
                file { baseFile(Constants.JAR_PROC) }
            }

            dependsOn "getMinecraft", "doFMLMappingPreProcess"
        }
        // merge jars
        task << {
            def is152 = project.minecraft.is152OrLess()
            println "is152? -> $is152"
            
            def server = Util.file(temporaryDir, "server.jar")
            def merged = is152 ? jarFile(Constants.JAR_JAR_CLIENT) : jarVersionFile(Constants.JAR_JAR16_CLIENT)
            def mergeTemp = Util.file(temporaryDir, "merged.jar.tmp")

            Files.copy(is152 ? jarFile(Constants.JAR_JAR_CLIENT_BAK) : jarVersionFile(Constants.JAR_JAR16_CLIENT_BAK), mergeTemp)
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
            def merged = project.minecraft.is152OrLess() ? jarFile(Constants.JAR_JAR_CLIENT) : jarVersionFile(Constants.JAR_JAR16_CLIENT)
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
    }

    def decompileTask()
    {
        def task = project.task('decompileMinecraft', type: DecompileMinecraftTask) {
            dependsOn 'extractMisc'
            dependsOn 'doJarPreProcess'
            dependsOn 'unpackNatives'

            inputs.with {
                dir {baseFile(Constants.DIR_FML_PATCHES)}
                dir {baseFile(Constants.DIR_FORGE_PATCHES)}
                file {baseFile(Constants.DIR_MAPPINGS, "astyle.cfg")}
                files { Constants.CSVS.collect { baseFile(Constants.DIR_MAPPINGS, it.getValue()) } }
                file {baseFile(Constants.JAR_PROC)}
                dir {baseFile(Constants.DIR_MCP_PATCHES)}
            }

            outputs.with {
                outputs.dir {srcFile(Constants.DIR_SRC_MINECRAFT)}
                outputs.dir {srcFile(Constants.DIR_SRC_RESOURCES)}
            }

        }
    }

    def configureEclipse()
    {
        project.eclipse {

            jdt {
                sourceCompatibility = 1.6
                targetCompatibility = 1.6
            }

            classpath {
                file.withXml {provider ->
                    Node rootNode = provider.asNode()

                    // NATIVES PART  ---------------------------------------------------------------------

                    // If this is doing anything, assume no gradle plugin.
                    [
                        'jinput.jar',
                        'lwjg.jar',
                        'lwjgl_util.jar'
                    ].each { nativ ->
                        def container = rootNode.children().find {it.@path && it.@path.endsWith(nativ)}
                        if (container)
                            container.appendNode('attributes').appendNode('attribute', [name:"org.eclipse.jdt.launching.CLASSPATH_ATTR_LIBRARY_PATH_ENTRY", value:'$MC_JAR/bin/natives'])
                    }

                    // IGNORE WARNINGS SRC  ---------------------------------------------------------------------
                    [
                        Constants.DIR_SRC_MINECRAFT,
                        Constants.DIR_SRC_FML,
                        Constants.DIR_SRC_FORGE
                    ].each { srcDir ->
                        def container = rootNode.children().find { it.@kind == 'src' && it.@path && it.@path.endsWith('/'+srcDir)}
                        if (container)
                            container.appendNode('attributes').appendNode('attribute', [name:"ignore_optional_problems", value:'true'])
                    }
                }
            }
        }

        def task = project.task('afterEclipseImport'){
        }
        task << {

            def file = project.file('.classpath')

            // open up classpath variable, and make edits.
            def rootNode = new XmlSlurper().parseText(file.text)

            // NATIVES PART  ---------------------------------------------------------------------
            def nativesDir = jarFile(Constants.DIR_JAR_BIN, 'natives').getPath()

            // using the gradle plugin.
            def container = rootNode.children().find { it.@kind == 'con' && it.@path && it.@path == 'org.springsource.ide.eclipse.gradle.classpathcontainer' }
            if (container)
            {
                container.appendNode {
                    attributes {
                        attribute(name: "org.eclipse.jdt.launching.CLASSPATH_ATTR_LIBRARY_PATH_ENTRY", value: nativesDir)
                    }
                }
                //container.appendNode('attributes').appendNode("org.eclipse.jdt.launching.CLASSPATH_ATTR_LIBRARY_PATH_ENTRY", '$MC_JAR/bin/natives')
            }

            // IGNORE WARNINGS SRC  ---------------------------------------------------------------------
            [
                Constants.DIR_SRC_MINECRAFT,
                Constants.DIR_SRC_FML,
                Constants.DIR_SRC_FORGE
            ].each { srcDir ->
                container = rootNode.children().find { it.@kind == 'src' && it.@path && it.@path.toString().endsWith('/'+srcDir)}
                if (container)
                {
                    container.appendNode {
                        attributes {
                            attribute(name: "ignore_optional_problems", value: true)
                        }
                    }
                }
            }

            // write XML
            // check the whole document using XmlUnit
            def builder = new StreamingMarkupBuilder()
            def result = builder.bind({mkp.yield rootNode })
            result = XmlUtil.serialize(result)

            println result

            file.write result
        }
    }

    public static Closure reobfSRGJarClosure()
    {
        def c = { Task task ->
            def file = task.archivePath
            def inTemp = Util.file(task.temporaryDir, 'jarIn.jar')
            Files.copy(file, inTemp)
            file.delete()

            def deobfed =  Util.baseFile(Constants.JAR_PROC)

            // load mapping
            JarMapping mapping = new JarMapping()
            mapping.loadMappings(Util.baseFile(Constants.DIR_MAPPINGS, "reobf_srg.srg"))

            // make remapper
            JarRemapper remapper = new JarRemapper(null, mapping)

            // load jar
            def input = Jar.init(inTemp)

            // ensure that inheritance provider is used
            JointProvider inheritanceProviders = new JointProvider()
            inheritanceProviders.add(new JarProvider(input))
            inheritanceProviders.add(new JarProvider(Jar.init(deobfed)))
            mapping.setFallbackInheritanceProvider(inheritanceProviders)

            // remap jar
            remapper.remapJar(input, file)
        }

        return c
    }

    public static Closure reobfJarClosure()
    {
        def c = { Task task ->
            def file = task.archivePath
            def inTemp = Util.file(task.temporaryDir, 'jarIn.jar')
            Files.copy(file, inTemp)
            file.delete()

            def deobfed =  Util.baseFile(Constants.JAR_PROC)

            // load mapping
            JarMapping mapping = new JarMapping()
            mapping.loadMappings(Util.baseFile(Constants.DIR_MAPPINGS, "reobf_mcp.srg"))

            // make remapper
            JarRemapper remapper = new JarRemapper(null, mapping)

            // load jar
            def input = Jar.init(inTemp)

            // ensure that inheritance provider is used
            JointProvider inheritanceProviders = new JointProvider()
            inheritanceProviders.add(new JarProvider(input))
            inheritanceProviders.add(new JarProvider(Jar.init(deobfed)))
            mapping.setFallbackInheritanceProvider(inheritanceProviders)

            // remap jar
            remapper.remapJar(input, file)
        }

        return c
    }

    public static File setReobfMinecraftNames()
    {
        return Util.baseFile(Constants.DIR_MAPPINGS, 'reobf_mcp.srg')
    }

    public static File setReobfSRGNames()
    {
        return  Util.baseFile(Constants.DIR_MAPPINGS, "reobf_srg.srg")
    }
}