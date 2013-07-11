package com.github.abrarsyed.gmcp

import com.github.abrarsyed.gmcp.Constants.OperatingSystem

class Util
{

    def static download(String url, File output)
    {
        output.createNewFile()
        while(url)
        {
            new URL(url).openConnection().with
            { conn ->
                conn.instanceFollowRedirects = false
                url = conn.getHeaderField( "Location" )
                if( !url )
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
            OperatingSystem.MAC
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

    def static String getRelative(File root, File file)
    {
        return file.getAbsolutePath().substring(root.getAbsolutePath().length())
    }

    def static File file(String... args)
    {
        return new File(args.join("/"))
    }

    def static File file(File file, String... args)
    {
        return new File(file, args.join("/"))
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

    def static File srcFile(String... args)
    {
        def arguments = []
        arguments += GMCP.project.minecraft.srcDir
        arguments.addAll(args)

        return file(arguments as String[])
    }
}
