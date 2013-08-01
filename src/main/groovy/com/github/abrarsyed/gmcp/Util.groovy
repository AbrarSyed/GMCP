package com.github.abrarsyed.gmcp

import com.github.abrarsyed.gmcp.Constants.OperatingSystem

class Util
{

    def static download(String url, File output)
    {
        output.getParentFile().mkdirs()
        output.createNewFile()
        while (url)
        {
            new URL(url).openConnection().with
                    { conn ->
                        conn.instanceFollowRedirects = false
                        url = conn.getHeaderField("Location")
                        if (!url)
                        {
                            output.withOutputStream
                                    { out ->
                                        conn.inputStream.with
                                                { inp ->
                                                    out << inp
                                                    inp.close()
                                                }
                                    }
                        }
                    }
        }
    }

    public static OperatingSystem getOS()
    {
        def name = System.properties["os.name"].toString().toLowerCase()
        if (name.contains("windows"))
            OperatingSystem.WINDOWS
        else if (name.contains("mac"))
            OperatingSystem.OSX
        else if (name.contains("linux"))
            OperatingSystem.LINUX
        else
            null
    }

    public static createOrCleanDir(File file)
    {

        if (file.exists() && file.isDirectory())
        {
            file.eachFile
                    {
                        if (it.isDirectory())
                            it.deleteDir()
                        else
                            it.delete()
                    }
        }
        else
            file.mkdirs()
    }

    def static File gradleDir(String... args)
    {
        def arguments = []
        arguments += System.getProperty("user.home")
        arguments += '.gradle'
        arguments.addAll(args)

        return file(arguments as String[])
    }

    def static String getRelative(File root, File file)
    {
        return file.getAbsolutePath().substring(root.getAbsolutePath().length())
    }

    def static File file(String... args)
    {
        return new File(args.join('/'))
    }

    def static File file(File file, String... args)
    {
        return new File(file, args.join('/'))
    }

    def static File baseFile(String... args)
    {
        def arguments = []
        arguments += GMCP.project.minecraft.baseDir
        arguments.addAll(args)

        return file(arguments as String[])
    }

    def static File jarFile(String... args)
    {
        def arguments = []
        arguments += GMCP.project.minecraft.jarDir
        arguments.addAll(args)

        return file(arguments as String[])
    }

    /**
     * Just like the jarFile method, except it uses the version in the path.
     * @param args
     * @return
     */
    def static File jarVersionFile(String... args)
    {
        def arguments = []
        arguments += 'versions'
        arguments += GMCP.project.minecraft.minecraftVersion
        arguments.addAll(args)

        return jarFile(arguments as String[])
    }

    def static File srcFile(String... args)
    {
        def arguments = []
        arguments += GMCP.project.minecraft.srcDir
        arguments.addAll(args)

        return file(arguments as String[])
    }
}
