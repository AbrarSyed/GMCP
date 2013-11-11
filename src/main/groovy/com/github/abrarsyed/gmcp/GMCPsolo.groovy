package com.github.abrarsyed.gmcp

import com.github.abrarsyed.gmcp.tasks.obfuscate.ReobfTask
import groovy.xml.StreamingMarkupBuilder
import groovy.xml.XmlUtil
import org.gradle.api.Project

class GMCPsolo extends GMCP
{
    //             compile.extendsFrom gmcp

    @Override
    public void apply(Project project)
    {
        super.apply(project)
        project.configurations.compile.extendsFrom project.configurations.gmcp
    }

    @Override
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
        }
    }

    @Override
    def configureSourceSet()
    {
        project.sourceSets {
            main {
                java {
                    srcDir { Util.srcFile(Constants.DIR_SRC_MINECRAFT) }
                    srcDir { Util.srcFile(Constants.DIR_SRC_FORGE) }
                    srcDir { Util.srcFile(Constants.DIR_SRC_FML) }
                }
                resources {
                    srcDir { Util.srcFile(Constants.DIR_SRC_RESOURCES) }
                }
            }
        }
    }

    def configureCompilation()
    {
        project.compileJava {
            options.warnings = false
            targetCompatibility = '1.6'
            sourceCompatibility = '1.6'
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
                    project.sourceSets.main.allSource.getSrcDirs().each { srcDir ->
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
            project.sourceSets.main.allSource.getSrcDirs().each { srcDir ->
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
                sourceDirs.addAll project.sourceSets.main.allSource.getSrcDirs()
            }
        }
    }
}