package com.github.abrarsyed.gmcp.extensions

import org.gradle.api.Nullable

import argo.jdom.JdomParser
import argo.jdom.JsonNode
import argo.jdom.JsonRootNode

import com.github.abrarsyed.gmcp.Constants
import com.github.abrarsyed.gmcp.GMCP
import com.github.abrarsyed.gmcp.exceptions.MalformedVersionException

class GMCPExtension
{
    @Nullable
    private String minecraftVersion
    private forgeVersion = "latest"
    def String forgeURL
    def baseDir = "minecraft"
    def jarDir
    def srcDir
    def accessTransformers = []

    private resolvedVersion = false
    private resolvedJarDir = false
    private resolvedSrcDir = false

    private final GMCP plugin
    private final File cacheFile
    private static final JdomParser JDOM_PARSER = new JdomParser()

    public GMCPExtension(GMCP project)
    {
        this.plugin = project
        cacheFile = plugin.file(plugin.project.gradle.gradleUserHomeDir, Constants.URL_JSON_FORGE_CACHE2)
    }

    public void setForgeVersion(Object obj)
    {
        if (obj instanceof String)
            obj = obj.toLowerCase()
        forgeVersion = obj
        resolvedVersion = false
    }


    public String getForgeVersion()
    {
        if (!resolvedVersion)
            resolveVersion(false)

        forgeVersion
    }

    public void setMinecraftVersion(String obj)
    {
        if (obj instanceof String)
            obj = obj.toLowerCase()
        minecraftVersion = obj
        resolvedVersion = false
    }

    public String getMinecraftVersion()
    {
        if (!resolvedVersion)
            resolveVersion(false)

        minecraftVersion
    }

    public void setForgeURL(String str)
    {
        resolvedVersion = true
        forgeURL = str
    }

    public String getForgeURL()
    {
        if (!resolvedVersion)
            resolveVersion(false)

        forgeURL
    }

    public void setbaseDir(String obj)
    {
        resolvedSrcDir = false
        resolvedJarDir = false
    }

    public String getSrcDir()
    {
        if (!resolvedSrcDir)
            resolveSrcDir()

        srcDir
    }

    public String setSrcDir(String obj)
    {
        resolvedSrcDir = true
        srcDir = obj
    }

    public String getJarDir()
    {
        if (!resolvedJarDir)
            resolveJarDir()

        jarDir
    }

    public String setJarDir(String obj)
    {
        resolvedJarDir = true
        jarDir = obj
    }

    protected void resolveVersion(boolean refreshCache)
    {
        String text

        // check cache, not there or needs refreshing? refresh cache.
        if (!cacheFile.exists() || refreshCache)
        {
            text = Constants.URL_JSON_FORGE2.toURL().text
            cacheFile.parentFile.mkdirs()
            cacheFile.write(text)
        }
        else
            text = cacheFile.text


        // load JSON
        JsonRootNode root = JDOM_PARSER.parse(text)

        def url = root.getStringValue("webpath")
        def JsonNode fileObj
        def JsonNode versionObj

        if (root.isNode("promos", forgeVersion))
        {
            versionObj = fileObj = root.getNode("promos", forgeVersion, "files", "src")
        }
        // MC version is set?
        else if (minecraftVersion)
        {
            if (forgeVersion.toString().isInteger())
            {
                versionObj = root.getNode("mcversion", minecraftVersion, forgeVersion)
                def list = root.getArrayNode("mcversion", minecraftVersion, forgeVersion, "files")
                for (build in list)
                {
                    if (build.getStringValue("type") == "src")
                    {
                        fileObj = build
                        break

                    }
                }
            }
            else if (forgeVersion.toString().toLowerCase() == "latest")
            {
                // ohey, its in the promos.
                if (root.isNode("promos", "latest-$minecraftVersion"))
                {
                    // get it from the promo...
                    versionObj = fileObj = root.getArrayNode("promos", "latest-$minecraftVersion", "files", "src")
                }
                else
                {
                    // list of builds
                    def builds = root.getNode("mcversion", minecraftVersion)

                    // find biggest buildNum
                    def bigBuild = 0
                    builds.fieldList.each {
                        def num = it.getName().text.toInteger()
                        if (num > bigBuild)
                            bigBuild = num
                    }

                    // save it to the FileObj
                    versionObj = root.getNode("mcversion", minecraftVersion, bigBuild)
                    for (build in root.getArrayNode("mcversion", minecraftVersion, bigBuild, "files"))
                    {
                        if (build.getStringValue("type") == "src")
                        {
                            fileObj = build
                            break
    
                        }
                    }
                }
            }
            else if (forgeVersion.toString() ==~ /\d+\.\d+\.\d+\.\d+/)
            {
                if (minecraftVersion)
                {
                    // list of builds
                    def builds = root.getNode("mcversion", minecraftVersion)

                    // find biggest buildNum
                    for (build in builds.getElements())
                    {
                        if (build.getStringValue("version") == forgeVersion)
                        {
                            // found the version, now the file.
                            for (build2 in build.getArrayNode("files"))
                            {
                                if (build2.getStringValue("type") == "src")
                                {
                                    fileObj = build2
                                    break

                                }
                            }
                            break
                        }
                    }
                }
            }
        }

        // couldnt find the version?? wut??
        if (!fileObj)
        {
            // cache has already been refreshed??
            if (refreshCache)
                throw new MalformedVersionException()
            // try again with refreshed cache.
            else
                resolveVersion(true)
        }
        // worked.
        else
        {
            forgeVersion = versionObj.getStringValue("version")
            minecraftVersion = versionObj.getStringValue("version")
            forgeURL = url + "/" + fileObj.getStringValue("filename")
            resolvedVersion = true
        }
    }

    private void resolveSrcDir()
    {
        if (!srcDir)
            srcDir = baseDir + "/src"
    }

    private void resolveJarDir()
    {
        if (!jarDir)
            jarDir = baseDir + "/jars"
    }
}
