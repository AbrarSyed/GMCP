package com.github.abrarsyed.gmcp

import com.github.abrarsyed.gmcp.Constants.OperatingSystem
import com.github.abrarsyed.gmcp.extensions.GMCPExtension
import com.github.abrarsyed.gmcp.extensions.ModInfoExtension
import com.github.abrarsyed.gmcp.tasks.*
import com.github.abrarsyed.gmcp.tasks.obfuscate.ReobfTask
import com.google.common.io.Files
import groovy.xml.StreamingMarkupBuilder
import groovy.xml.XmlUtil
import net.md_5.specialsource.Jar
import net.md_5.specialsource.JarMapping
import net.md_5.specialsource.JarRemapper
import net.md_5.specialsource.provider.JarProvider
import net.md_5.specialsource.provider.JointProvider
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.tasks.Copy

public class GMCP implements Plugin<Project>
{
    //public GMCPExtension ext
    public static OperatingSystem os = Util.getOS()
    public static Project project
    public static Json16Reader json;

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
        project.apply(plugin: "java")
        project.apply(plugin: "idea")
        project.apply(plugin: "eclipse")

        // final resolving.   this closure is executed later...
        doResolving()

        // manage dependency configurations
        configureSourceSet()
        doConfigurationStuff()
        configureCompilation()

        // well why not?  read the json NOW
        if (Json16Reader.doesFileExist())
        {
            json = new Json16Reader("1.6")
            json.parseJson()

            // do dependencies
            project.dependencies {
                for (dep in json.libs)
                {
                    add 'gmcp', dep
                }

                for (dep in json.nativeLibs)
                {
                    add 'gmcpNative', dep
                }

                add 'gmcp', project.files(Util.cacheFile(String.format(Constants.FMED_JAR_MERGED, project.minecraft.minecraftVersion)).getPath())
            }
        }

        // repos
        // yay for maven central.
        project.with {
            repositories {
                maven {
                    name 'forge'
                    url 'http://files.minecraftforge.net/maven'
                }
                maven {
                    name "minecraft"
                    url "http://s3.amazonaws.com/Minecraft.Download/libraries"
                }
                mavenCentral()
            }
        }

        // start the tasks
        downloadTasks()
        nativesUnpackTask()
        jarTasks()
        decompileTask()

        // IDE stuff
        configureEclipse()
        //configureIntelliJ()

        // setup task
        project.task('setupCIWorkspace') {
            dependsOn 'deobfuscateJar', 'decompileMinecraft'
        }
        project.task('setupDevWorkspace') {
            dependsOn 'setupCIWorkspace', 'getAssets', 'unpackNatives'
        }

        // replace normal jar task with mine.
        //project.tasks.jar << reobfJarClosure()
        //project.tasks.jar.dependsOn('deobfuscateJar')
    }

    def doResolving()
    {
        project.afterEvaluate {
            project.minecraft.resolveVersion(false)
            project.minecraft.resolveSrcDir()
        }
    }

    def doConfigurationStuff()
    {
        project.configurations {
            gmcp {
                transitive = true
                visible = false
                description = "GMCP internal configuration. Don't use!"
                getTaskDependencyFromProjectDependency(true, 'resolveMinecraftStuff')
            }

            obfuscated {
                transitive = false
                visible = true
                description = "For obfuscated artifacts"
            }

            gmcpNative {
                transitive = false
                visible = false
                description = "GMCP internal configuration. Don't use!"
                getTaskDependencyFromProjectDependency(true, 'resolveMinecraftStuff')
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
        project.sourceSets {
            minecraft {
                java {
                    srcDir { Util.srcFile(Constants.DIR_SRC_MINECRAFT) }
                    srcDir { Util.srcFile(Constants.DIR_SRC_FORGE) }
                    srcDir { Util.srcFile(Constants.DIR_SRC_FML) }
                }
                resources {
                    srcDir { Util.srcFile(Constants.DIR_SRC_RESOURCES) }
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
            options.warnings = false
            targetCompatibility = '1.6'
            sourceCompatibility = '1.6'
        }

        def task = project.task('reobf', type: ReobfTask) {
            reobf project.tasks.jar
            dependsOn 'deobfuscateJar', 'genReobfSrgs'
        }

        project.tasks.assemble.dependsOn 'reobf'
        //project.tasks.dependencies.dependsOn 'resolveMinecraftStuff'
    }

    def downloadTasks()
    {
        // download forge
        project.task('downloadForge', type: DownloadTask) {
            output = { Util.cacheFile(Constants.CACHE_DIR_FORGE, project.minecraft.forgeVersion + '.zip') }
            url = { project.minecraft.forgeURL }
        }

        // extract the forge zip
        project.task('extractForge', type: Copy, dependsOn: 'downloadForge') {
            from { project.zipTree(Util.cacheFile(Constants.CACHE_DIR_FORGE, project.minecraft.forgeVersion + '.zip')) }
            into { project.minecraft.baseDir }
            outputs.upToDateWhen {
                def file = Util.baseFile("forge", "forgeversion.properties")
                if (!file.exists())
                {
                    return false
                }
                def props = new Properties()
                props.load(file.newInputStream())
                def version = String.format("%s.%s.%s.%s", props.get("forge.major.number"), props.get("forge.minor.number"), props.get("forge.revision.number"), props.get("forge.build.number"))
                return project.minecraft.forgeVersion == version
            }
            doLast {
                this.json = new Json16Reader("1.6")
                this.json.parseJson()

                // do dependnecies
                project.dependencies {
                    for (dep in json.libs)
                    {
                        add 'gmcp', dep
                    }

                    for (dep in json.nativeLibs)
                    {
                        add 'gmcpNative', dep
                    }

                    add 'gmcp', project.files(Util.cacheFile(String.format(Constants.FMED_JAR_MERGED, project.minecraft.minecraftVersion)).getPath())
                }
            }
        }

        // download the client
        project.task('downloadClient', type: DownloadTask, dependsOn: "extractForge") {
            output = { Util.cacheFile(String.format(Constants.FMED_JAR_CLIENT_FRESH, project.minecraft.minecraftVersion)) }
            url = { String.format(Constants.URL_MC_CLIENT, project.minecraft.minecraftVersion) }
        }

        // download the server
        project.task('downloadServer', type: DownloadTask, dependsOn: "extractForge") {
            output = { Util.cacheFile(String.format(Constants.FMED_JAR_SERVER_FRESH, project.minecraft.minecraftVersion)) }
            url = { String.format(Constants.URL_MC_SERVER, project.minecraft.minecraftVersion) }
        }

        // download the FernFlower
        project.task('downloadFernFlower', type: DownloadTask, dependsOn: "extractForge") {
            output = { Util.cacheFile(Constants.FERNFLOWER) }
            url = Constants.URL_FERNFLOWER
        }

        // download the exceptor
        project.task('downloadExceptor', type: DownloadTask, dependsOn: "extractForge") {
            output = { Util.cacheFile(Constants.EXCEPTOR) }
            url = Constants.URL_EXCEPTOR
        }

        // TODO: assets
        project.task('getAssets', dependsOn: 'extractForge') {
            outputs.dir { Util.jarFile(Constants.DIR_JAR_ASSETS) }

            doLast {

                // make assets dir.
                def assets = Util.jarFile(Constants.DIR_JAR_ASSETS)
                assets.mkdirs()

                // get resources
                def rootNode = new XmlSlurper().parse(Constants.URL_ASSETS)

                //ListBucketResult
                def files = rootNode.Contents.collect { it.Size.text() != '0' ? it.Key.text() : null }

                files.each {
                    // skip empty entries.
                    if (!it)
                    {
                        return
                    }

                    def file = Util.file(assets, it)
                    def url = Constants.URL_ASSETS + '/' + it
                    Util.download(url, file)
                }
            }
        }

        // ----------------------------------------------------------------------------
        // to do the package changes
        project.task('fixMappings', type: MergeMappingsTask, dependsOn: "extractForge") {
            inSRG = Util.baseFile(Constants.DIR_MAPPINGS, "joined.srg")
            inPatch = Util.baseFile(Constants.DIR_MCP_PATCHES, "minecraft_ff.patch")
            inEXC = Util.baseFile(Constants.DIR_MAPPINGS, "joined.exc")

            packageCSV = Util.baseFile(Constants.DIR_MAPPINGS, Constants.CSVS["packages"])

            outSRG = { Util.cacheFile(String.format(Constants.FMED_PACKAGED_SRG, project.minecraft.minecraftVersion)) }
            outPatch = { Util.cacheFile(String.format(Constants.FMED_PACKAGED_PATCH, project.minecraft.minecraftVersion)) }
            outEXC = { Util.cacheFile(String.format(Constants.FMED_PACKAGED_EXC, project.minecraft.minecraftVersion)) }
        }

        project.task('genReobfSrgs', type: GenReobfSrgTask, dependsOn: "extractForge") {
            methodsCSV = { Util.baseFile(Constants.DIR_MAPPINGS, Constants.CSVS['methods']) }
            fieldsCSV = { Util.baseFile(Constants.DIR_MAPPINGS, Constants.CSVS['fields']) }

            inSrg = { Util.cacheFile(String.format(Constants.FMED_PACKAGED_SRG, project.minecraft.minecraftVersion)) }

            outMcpSrg = { Util.cacheFile(String.format(Constants.FMED_OBF_MCP_SRG, project.minecraft.minecraftVersion)) }
            outObfSrg = { Util.cacheFile(String.format(Constants.FMED_OBF_SRG_SRG, project.minecraft.minecraftVersion)) }
        }
    }

    def nativesUnpackTask()
    {
        def task = project.task("unpackNatives", dependsOn: 'extractForge') {
            inputs.files { project.configurations.gmcpNative }
            outputs.dir { project.file(Constants.DIR_NATIVES) }

            doLast {
                project.copy {
                    project.configurations.gmcpNative.resolvedConfiguration.resolvedArtifacts.each {
                        from project.zipTree(it.file)
                    }

                    into project.file(Constants.DIR_NATIVES)
                }
            }
        }
    }

    def jarTasks()
    {
        project.task("mergeJars", type: MergeJarsTask) {
            client = { Util.cacheFile(String.format(Constants.FMED_JAR_CLIENT_FRESH, project.minecraft.minecraftVersion)) }
            server = { Util.cacheFile(String.format(Constants.FMED_JAR_SERVER_FRESH, project.minecraft.minecraftVersion)) }
            outJar = { Util.cacheFile(String.format(Constants.FMED_JAR_MERGED, project.minecraft.minecraftVersion)) }
            mergeCfg = { Util.baseFile(Constants.DIR_FML, "mcp_merge.cfg") }

            dependsOn "downloadClient", "downloadServer", 'extractForge'
        }

        project.task("deobfuscateJar", type: ProcessJarTask) {
            inJar = { Util.cacheFile(String.format(Constants.FMED_JAR_MERGED, project.minecraft.minecraftVersion)) }
            exceptorJar = Util.cacheFile(Constants.EXCEPTOR);
            outJar = Util.file(Constants.JAR_SRG);
            srg = { Util.cacheFile(String.format(Constants.FMED_PACKAGED_SRG, project.minecraft.minecraftVersion)) }
            exceptorCfg = { Util.cacheFile(String.format(Constants.FMED_PACKAGED_EXC, project.minecraft.minecraftVersion)) }
            addTransformer Util.file(Constants.DIR_FML, "common", "fml_at.cfg")
            addTransformer Util.file(Constants.DIR_FORGE, "common", "forge_at.cfg")

            dependsOn 'downloadExceptor', 'mergeJars', 'fixMappings'
        }
    }

    def decompileTask()
    {
        def task = project.task('decompileMinecraft', type: DecompileMinecraftTask) {
            dependsOn 'deobfuscateJar', 'downloadFernFlower'

            inputs.with {
                dir { Util.baseFile(Constants.DIR_FML_PATCHES) }
                dir { Util.baseFile(Constants.DIR_FORGE_PATCHES) }
                file { Util.baseFile(Constants.DIR_MAPPINGS, "astyle.cfg") }
                files { Constants.CSVS.collect { Util.baseFile(Constants.DIR_MAPPINGS, it.getValue()) } }
                file { Util.file(Constants.JAR_SRG) }
                file { Util.cacheFile(Constants.FERNFLOWER) }
                file { Util.cacheFile(String.format(Constants.FMED_PACKAGED_PATCH, project.minecraft.minecraftVersion)) }
            }

            outputs.with {
                outputs.dir { Util.srcFile(Constants.DIR_SRC_MINECRAFT) }
                outputs.dir { Util.srcFile(Constants.DIR_SRC_RESOURCES) }
                outputs.dir { Util.srcFile(Constants.DIR_SRC_FML) }
                outputs.dir { Util.srcFile(Constants.DIR_SRC_FORGE) }
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
                file.withXml { provider ->
                    Node rootNode = provider.asNode()

                    // NATIVES PART  ---------------------------------------------------------------------

                    // If this is doing anything, assume no gradle plugin.
                    [
                            'jinput.jar',
                            'lwjg.jar',
                            'lwjgl_util.jar'
                    ].each { nativ ->
                        def container = rootNode.children().find { it.@path && it.@path.endsWith(nativ) }
                        if (container)
                        {
                            container.appendNode('attributes').appendNode('attribute', [name: "org.eclipse.jdt.launching.CLASSPATH_ATTR_LIBRARY_PATH_ENTRY", value: '$MC_JAR/bin/natives'])
                        }
                    }

                    // IGNORE WARNINGS SRC  ---------------------------------------------------------------------
                    [
                            Constants.DIR_SRC_MINECRAFT,
                            Constants.DIR_SRC_FML,
                            Constants.DIR_SRC_FORGE
                    ].each { srcDir ->
                        def container = rootNode.children().find { it.@kind == 'src' && it.@path && it.@path.endsWith('/' + srcDir) }
                        if (container)
                        {
                            container.appendNode('attributes').appendNode('attribute', [name: "ignore_optional_problems", value: 'true'])
                        }
                    }
                }
            }
        }

        def task = project.task('afterEclipseImport') {
        }
        task << {

            def file = project.file('.classpath')

            // open up classpath variable, and make edits.
            def rootNode = new XmlSlurper().parseText(file.text)

            // NATIVES PART  ---------------------------------------------------------------------
            def nativesDir = project.file(Constants.DIR_NATIVES)

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
                container = rootNode.children().find { it.@kind == 'src' && it.@path && it.@path.toString().endsWith('/' + srcDir) }
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
            def result = builder.bind({ mkp.yield rootNode })
            result = XmlUtil.serialize(result)

            println result

            file.write result
        }
    }

    @Deprecated
    public static Closure reobfSRGJarClosure()
    {
        def c = { Task task ->
            def file = task.archivePath
            def inTemp = Util.file(task.temporaryDir, 'jarIn.jar')
            Files.copy(file, inTemp)
            file.delete()

            def deobfed = Util.file(Constants.JAR_SRG)

            // load mapping
            JarMapping mapping = new JarMapping()
            mapping.loadMappings(Util.cacheFile(String.format(Constants.FMED_OBF_SRG_SRG, project.minecraft.minecraftVersion)))

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

    @Deprecated
    public static Closure reobfJarClosure()
    {
        def c = { Task task ->
            def file = task.archivePath
            def inTemp = Util.file(task.temporaryDir, 'jarIn.jar')
            Files.copy(file, inTemp)
            file.delete()

            def deobfed = Util.file(Constants.JAR_SRG)

            // load mapping
            JarMapping mapping = new JarMapping()
            mapping.loadMappings(Util.cacheFile(String.format(Constants.FMED_OBF_MCP_SRG, project.minecraft.minecraftVersion)))

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
}
