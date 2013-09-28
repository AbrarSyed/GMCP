package com.github.abrarsyed.gmcp

import com.github.abrarsyed.gmcp.Constants.OperatingSystem

import java.security.MessageDigest

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
        {
            OperatingSystem.WINDOWS
        }
        else if (name.contains("mac"))
        {
            OperatingSystem.OSX
        }
        else if (name.contains("linux"))
        {
            OperatingSystem.LINUX
        }
        else
        {
            null
        }
    }

    public static List<String> getClassPath()
    {
        return GMCP.class.getClassLoader().getURLs().collect {  it.getPath() };
    }

    /**
     * DON'T FORGET TO CLOSE
     */
    public static OutputStream getNullStream()
    {
        return new BufferedOutputStream(new ByteArrayOutputStream());
    }

    public static createOrCleanDir(File file)
    {

        if (file.exists() && file.isDirectory())
        {
            file.eachFile
                    {
                        if (it.isDirectory())
                        {
                            it.deleteDir()
                        }
                        else
                        {
                            it.delete()
                        }
                    }
        }
        else
        {
            file.mkdirs()
        }
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

    def static File buildFile(String... args)
    {
        def arguments = []
        arguments += GMCP.project.getBuildDir()
        arguments.addAll(args)

        return file(arguments as String[])
    }

    /**
     * Just like the jarFile method, except it uses the version in the path.
     * @param args
     * @return
     */
    def static File cacheFile(String... args)
    {
        def arguments = []
        arguments += GMCP.project.gradle.gradleUserHomeDir
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

    def static String hashFile(File file)
    {

        MessageDigest complete = MessageDigest.getInstance("MD5");
        byte[] hash = complete.digest(file.bytes);

        def builder = new StringBuilder(40);

        hash.each { builder.append Integer.toString((it & 0xff) + 0x100, 16).substring(1) }

        return builder.toString();

    }
}
