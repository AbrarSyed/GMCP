package com.github.abrarsyed.gmcp.extensions

import org.gradle.api.Nullable

import argo.jdom.JdomParser
import argo.jdom.JsonNode
import argo.jdom.JsonRootNode

import com.github.abrarsyed.gmcp.Constants
import com.github.abrarsyed.gmcp.GMCP
import com.github.abrarsyed.gmcp.Util
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

    public static boolean is152Minus

    private resolvedVersion = false
    private resolvedJarDir = false
    private resolvedSrcDir = false

    private final File cacheFile
    private final File cacheFile2
    private static final JdomParser JDOM_PARSER = new JdomParser()

    public GMCPExtension(GMCP project)
    {
        cacheFile = Util.file(System.getProperty("user.home"), Constants.CACHE_JSON_FORGE)
        cacheFile2 = Util.file(System.getProperty("user.home"), Constants.CACHE_JSON_FORGE2)

        cacheFile.getParentFile().mkdirs()
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
        String json1
        String json2

        // check JSON1 cache
        if (!cacheFile.exists() || refreshCache)
        {
            json1 = Constants.URL_JSON_FORGE.toURL().text
            cacheFile.write(json1)
        }
        else
            json1 = cacheFile.text

        // check JSON2 cache
        if (!cacheFile2.exists() || refreshCache)
        {
            json2 = Constants.URL_JSON_FORGE2.toURL().text
            cacheFile2.write(json2)
        }
        else
            json2 = cacheFile2.text


        // load JSON
        JsonRootNode root1 = JDOM_PARSER.parse(json1)
        JsonRootNode root2 = JDOM_PARSER.parse(json2)

        def url = root2.getStringValue("webpath")
        def isJSON1
        def JsonNode fileObj
        def JsonNode versionObj

        // latest or reccomended
        if (forgeVersion.toString().startsWith("latest") || forgeVersion.toString().startsWith("recommended"))
        {
            if (minecraftVersion)
            {
                // check JSON2 promotions, and ensure MC version.
                if (root2.isNode("promos", forgeVersion) && root2.getStringValue("promos", forgeVersion, "files", "src", "mcversion") == minecraftVersion)
                {
                    // minecraftVersion and promotion match.
                    isJSON1 = false
                    fileObj = versionObj = root2.getNode("promos", forgeVersion, "files", "src")
                }
                // grab the latest of that CM version from JSON 1
                else
                {
                    def builds = root1.getArrayNode("builds")

                    for (build in builds)
                    {
                        // biggest build will be the first we come accross
                        def files = build.getArrayNode("files")
                        if (files[0].getStringValue("mcver") == minecraftVersion)
                        {
                            for (file in files)
                            {
                                if (file.getStringValue("buildtype") == "src")
                                {
                                    isJSON1 = true
                                    versionObj = fileObj = file
                                    // break out of iterrration, found the file we want.
                                    break
                                }
                            }
                            // break out of itteration
                            break
                        }
                    }
                }
            }
            else
            {
                // check JSON2 promotions
                if (root2.isNode("promos", forgeVersion))
                {
                    isJSON1 = false
                    fileObj = versionObj = root2.getNode("promos", forgeVersion, "files", "src")
                }
            }
        }
        else if (minecraftVersion)
        {
            // build number or version number
            if (forgeVersion.toString().isInteger())
            {
                // buildNum AND mcversion, check JSON2
                versionObj = root2.getNode("mcversion", minecraftVersion, forgeVersion)
                def list = root2.getArrayNode("mcversion", minecraftVersion, forgeVersion, "files")
                for (build in list)
                {
                    if (build.getStringValue("type") == "src")
                    {
                        isJSON1 = false
                        fileObj = build
                        break
                    }
                }

                //				if (!versionObj || !fileObj)
                //					throw new MalformedVersionException("Forge "+forgeVersion+" found for Minecraft "+minecraftVersion)
            }
            else if (forgeVersion.toString() ==~ /\d+\.\d+\.\d+\.\d+/)
            {
                // buildnum AND forge version

                // list of builds
                def builds = root2.getNode("mcversion", minecraftVersion)

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
                                isJSON1 = false
                                fileObj = build2
                                break
                            }
                        }
                        versionObj = build
                        break
                    }
                }
                //
                //				if (!versionObj || !fileObj)
                //					throw new MalformedVersionException("Forge "+forgeVersion+" found for Minecraft "+minecraftVersion)

            }
        }
        else
        {
            if (forgeVersion.toString().isInteger())
            {
                def list = root1.getArrayNode("builds")

                for (build in list)
                {
                    if (build.getNumberValue("build") == forgeVersion)
                    {
                        for (file in build.getArrayNode("files"))
                        {
                            if (file.getStringValue("buildtype") == "src")
                            {
                                isJSON1 = true
                                versionObj = fileObj = file
                            }
                        }

                        break
                    }
                }
            }
            else if (forgeVersion.toString() ==~ /\d+\.\d+\.\d+\.\d+/)
            {
                def list = root1.getArrayNode("builds")

                for (build in list)
                {
                    if (build.getNumberValue("version") == forgeVersion)
                    {
                        for (file in build.getArrayNode("files"))
                        {
                            if (file.getStringValue("buildtype") == "src")
                            {
                                isJSON1 = true
                                versionObj = fileObj = file
                            }
                        }

                        break
                    }
                }
            }
        }

        // couldnt find the version?? wut??
        if (!fileObj || !versionObj)
        {
            // cache has already been refreshed??
            if (refreshCache)
            {
                def str = ""
                if (minecraftVersion)
                    str = " for Minecraft "+minecraftVersion

                throw new MalformedVersionException("No Forge \""+forgeVersion+"\" found"+str)
            }
            // try again with refreshed cache.
            else
                resolveVersion(true)
        }
        // worked.
        else
        {
            if (isJSON1)
            {
                forgeVersion = fileObj.getStringValue("jobbuildver")
                minecraftVersion = fileObj.getStringValue("mcver")
                forgeURL = fileObj.getStringValue("url")
            }
            else
            {
                forgeVersion = versionObj.getStringValue("version")
                minecraftVersion = versionObj.getStringValue("mcversion")
                forgeURL = root2.getStringValue("webpath") + "/" + fileObj.getStringValue("filename")
            }

            // check for 1.5.2 or lower.
            def match = minecraftVersion =~ /(\d)\.(\d)(\.\d)?/
            match.find()

            def major = match.group(1) as int
            def minor = match.group(2) as int

            if (major > 1)
                is152Minus = false
            else if (minor > 5)
                is152Minus = false
            else
                is152Minus = true

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
