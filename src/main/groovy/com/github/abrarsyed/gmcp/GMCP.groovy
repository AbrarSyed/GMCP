package com.github.abrarsyed.gmcp

import com.github.abrarsyed.gmcp.Constants.OperatingSystem
import com.github.abrarsyed.gmcp.extensions.GMCPExtension
import com.github.abrarsyed.gmcp.extensions.ModInfoExtension
import com.github.abrarsyed.gmcp.tasks.*
import com.github.abrarsyed.gmcp.tasks.obfuscate.ReobfTask
import groovy.xml.StreamingMarkupBuilder
import groovy.xml.XmlUtil
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.artifacts.Configuration
import org.gradle.api.tasks.Copy
import org.gradle.api.tasks.Delete

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
        configureIntelliJ()
        createIntellijTasks()

        // setup task
        project.task('setupCIWorkspace') {
            dependsOn 'deobfuscateJar', 'processMCSource'
        }
        project.task('setupDevWorkspace') {
            dependsOn 'setupCIWorkspace', 'getAssets', 'unpackNatives'
        }

        // replace normal jar task with mine.
        //project.tasks.jar << reobfJarClosure()
        //project.tasks.jar.dependsOn('deobfuscateJar')

        // clean task.
        project.task('cleanMinecraft', type: Delete) {
            delete { project.minecraft.baseDir }
            group = "Clean"
            description = "Clean Minecraft related files"
        }
    }

    def doResolving()
    {
        project.afterEvaluate {
            project.minecraft.resolveVersion(false)
            project.minecraft.resolveSrcDir()
            project.dependencies.add 'gmcp', project.files(Util.cacheFile(String.format(Constants.FMED_JAR_MERGED, project.minecraft.minecraftVersion)).getPath())
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
            project.sourceSets.api.compileClasspath += gmcp
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

            api {
                compileClasspath += minecraft.output
            }

            main {
                compileClasspath += minecraft.output + api.output
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

        project.compileApiJava {
            options.warnings = false
        }

        // reobfuscation
        project.task('reobf', type: ReobfTask, dependsOn: 'genReobfSrgs') {

            reobf(project.tasks.jar) { spec ->
                spec.classpath = project.sourceSets.main.compileClasspath
            }

        }

        project.tasks.assemble.dependsOn 'reobf'
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

                if (project.configurations.gmcp.state != Configuration.State.UNRESOLVED)
                {
                    return
                }

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

        project.task('obtainMcpJars', type: ObtainMcpJarsTask) {
            mcpUrl = Constants.URL_MCP_JARS
            ffJar = { Util.cacheFile(Constants.FERNFLOWER) }
            injectorJar = { Util.cacheFile(Constants.EXCEPTOR) }
        };

        project.task('getAssets', type: DownloadAssetsTask, dependsOn: 'extractForge') {
            assetsDir = Util.cacheFile(Constants.CACHE_ASSETS)
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
        project.task("unpackNatives", dependsOn: 'extractForge') {
            inputs.files { project.configurations.gmcpNative }
            outputs.dir { Util.baseFile(Constants.DIR_NATIVES) }

            doLast {
                project.copy {
                    project.configurations.gmcpNative.resolvedConfiguration.resolvedArtifacts.each {
                        from project.zipTree(it.file)
                    }

                    into { Util.baseFile(Constants.DIR_NATIVES) }
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
            outJar = { Util.baseFile(Constants.JAR_SRG) };
            outMap = { Util.cacheFile(String.format(Constants.FMED_INH_MAP, project.minecraft.minecraftVersion)) }
            srg = { Util.cacheFile(String.format(Constants.FMED_PACKAGED_SRG, project.minecraft.minecraftVersion)) }
            exceptorCfg = { Util.cacheFile(String.format(Constants.FMED_PACKAGED_EXC, project.minecraft.minecraftVersion)) }
            addTransformer Util.baseFile(Constants.DIR_FML, "common", "fml_at.cfg")
            addTransformer Util.baseFile(Constants.DIR_FORGE, "common", "forge_at.cfg")

            dependsOn 'obtainMcpJars', 'mergeJars', 'fixMappings'
        }
    }

    def decompileTask()
    {
        project.task('decompileMinecraft', type: ApplyFernflowerTask) {
            dependsOn 'deobfuscateJar', 'obtainMcpJars'
            input = { Util.baseFile(Constants.JAR_SRG) }
            fernflower = Util.cacheFile(Constants.FERNFLOWER)
            output = { Util.baseFile(Constants.JAR_DECOMP) }
        }

        project.task('processMCSource', type: ProcessSourceTask) {
            dependsOn 'decompileMinecraft'

            decompJar = { Util.baseFile(Constants.JAR_DECOMP) }

            inputs.with {
                dir { Util.baseFile(Constants.DIR_FML_PATCHES) }
                dir { Util.baseFile(Constants.DIR_FORGE_PATCHES) }
                file { Util.baseFile(Constants.DIR_MAPPINGS, "astyle.cfg") }
                files { Constants.CSVS.collect { Util.baseFile(Constants.DIR_MAPPINGS, it.getValue()) } }
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
                    def nativesDir = Util.baseFile(Constants.DIR_NATIVES)

                    // If this is doing anything, assume no gradle plugin.
                    [ 'jinput', 'lwjg'].each { nativ ->
                        def container = rootNode.children().find { it.@path && it.@path.contains(nativ) }
                        if (container)
                        {
                            container.appendNode{
                                attributes {
                                    attribute(name: "org.eclipse.jdt.launching.CLASSPATH_ATTR_LIBRARY_PATH_ENTRY", value: nativesDir.getAbsolutePath())
                                }
                            }
                        }
                    }

                    // IGNORE WARNINGS SRC  ---------------------------------------------------------------------
                    (
                    project.sourceSets.api.allSource.getSrcDirs() + project.sourceSets.minecraft.allSource.getSrcDirs()
                    ).each { srcDir ->
                        def container = rootNode.children().find { it.@kind == 'src' && it.@path && srcDir.getPath().replace("\\", "/").endsWith(it.@path.toString()) }
                        if (container)
                        {
                            container.appendNode{
                                attributes {
                                    attribute(name: "ignore_optional_problems", value: 'true')
                                }
                            }
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
            def nativesDir = Util.baseFile(Constants.DIR_NATIVES)

            // using the gradle plugin.
            def container = rootNode.children().find { it.@kind == 'con' && it.@path && it.@path == 'org.springsource.ide.eclipse.gradle.classpathcontainer' }
            if (container)
            {
                container.appendNode {
                    attributes {
                        attribute(name: "org.eclipse.jdt.launching.CLASSPATH_ATTR_LIBRARY_PATH_ENTRY", value: nativesDir.getAbsolutePath())
                    }
                }
                //container.appendNode('attributes').appendNode("org.eclipse.jdt.launching.CLASSPATH_ATTR_LIBRARY_PATH_ENTRY", '$MC_JAR/bin/natives')
            }

            // IGNORE WARNINGS SRC  ---------------------------------------------------------------------
            (
            project.sourceSets.api.allSource.getSrcDirs() + project.sourceSets.minecraft.allSource.getSrcDirs()
            ).each { srcDir ->
                println "" + srcDir + "  >>  " + srcDir.getPath().replace("\\", "/")
                container = rootNode.children().find { it.@kind == 'src' && it.@path && srcDir.getPath().replace("\\", "/").endsWith(it.@path.toString()) }
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

    def configureIntelliJ()
    {
        project.idea {
            module {
                sourceDirs.addAll project.sourceSets.api.allSource.getSrcDirs()
                sourceDirs.addAll project.sourceSets.minecraft.allSource.getSrcDirs()
                sourceDirs.addAll project.sourceSets.main.allSource.getSrcDirs()
            }
        }
    }

    def createIntellijTasks()
    {
        project.task("injectIntellijRunConigs") {
            doLast {
                def file = project.file('.idea/workspace.xml')

                // open up the file and make edits
                def rootNode = new XmlSlurper().parseText(file.text)

                // find the configuration section.
                def runManager = rootNode.component.find { it.@name == 'RunManager'}
                if (runManager)
                {
                    runManager.appendNode {
                        //default="false" name="Client" type="Application" factoryName="Application"
                        configuration(['default': 'false', 'name': 'Minecraft Client', 'type': 'Application', 'factoryName':'Application']) {

                            extension(name:'coverage', enabled:'false', merge:'false', sample_coverage:'true', runner:'idea')
                            option(name:'MAIN_CLASS_NAME', value:'net.minecraft.launchwrapper.Launch')
                            option(name:'VM_PARAMETERS', value:'-Xincgc -Xmx1024M -Xms1024M -Djava.library.path=\"$PROJECT_DIR$/minecraft/natives\"')
                            option(name:'PROGRAM_PARAMETERS', value:'--version 1.6 --tweakClass cpw.mods.fml.common.launcher.FMLTweaker --username=Player1234')
                            option(name:'WORKING_DIRECTORY', value:'file://$PROJECT_DIR$/minecraft/run')
                            option(name:'ALTERNATIVE_JRE_PATH_ENABLED', value:'false')
                            option(name:'ALTERNATIVE_JRE_PATH', value:'')
                            option(name:'ENABLE_SWING_INSPECTOR', value:'false')
                            option(name:'ENV_VARIABLES')
                            option(name:'PASS_PARENT_ENVS', value:'true')
                            module(name:project.idea.module.name)
                            envs()
                            RunnerSettings(RunnerId:'Run')
                            ConfigurationWrapper(RunnerId:'Run')
                            method()
                        }

                        configuration(['default': 'false', 'name': 'Minecraft Server', 'type': 'Application', 'factoryName':'Application']) {

                            extension(name:'coverage', enabled:'false', merge:'false', sample_coverage:'true', runner:'idea')
                            option(name:'MAIN_CLASS_NAME', value:'cpw.mods.fml.relauncher.ServerLaunchWrapper')
                            option(name:'VM_PARAMETERS', value:'XX:-UseSplitVerifier')
                            option(name:'PROGRAM_PARAMETERS', value:'')
                            option(name:'WORKING_DIRECTORY', value:'file://$PROJECT_DIR$/minecraft/run')
                            option(name:'ALTERNATIVE_JRE_PATH_ENABLED', value:'false')
                            option(name:'ALTERNATIVE_JRE_PATH', value:'')
                            option(name:'ENABLE_SWING_INSPECTOR', value:'false')
                            option(name:'ENV_VARIABLES')
                            option(name:'PASS_PARENT_ENVS', value:'true')
                            module(name:project.idea.module.name)
                            envs()
                            RunnerSettings(RunnerId:'Run')
                            ConfigurationWrapper(RunnerId:'Run')
                            method()
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

        project.task("injectIntellijSrcDirs") {
            doLast {
                def file = project.file(project.idea.module.name + '.iml')

                // open up the file and make edits
                def rootNode = new XmlSlurper().parseText(file.text)

                // find the configuration section.
                def container = rootNode.component.content
                if (container)
                {
                    container.appendNode {

                        def module = project.getProjectDir().getAbsolutePath();
                        // srcSet stuff
                        project.sourceSets.each { srcSet ->
                            if (srcSet.is(project.sourceSets.main) || srcSet.is(project.sourceSets.test))
                            {
                                return
                            }

                            srcSet.getAllSource().getSrcDirs().each { srcDir ->
                                sourceFolder('url':srcDir.getAbsolutePath().replace(module, 'file://$MODULE_DIR$'), 'isTestSource':'false')
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

        project.task("fixIntellijProject") {
            dependsOn "injectIntellijSrcDirs", "injectIntellijRunConigs"
        }
    }
}
