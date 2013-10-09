package com.github.abrarsyed.gmcp

import com.github.abrarsyed.gmcp.tasks.obfuscate.ReobfTask
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
                    srcDir { Util.cacheFile(Constants.CACHE_ASSETS) }
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
}