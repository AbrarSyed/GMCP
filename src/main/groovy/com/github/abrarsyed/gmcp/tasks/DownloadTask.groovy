package com.github.abrarsyed.gmcp.tasks

import org.gradle.api.tasks.Input
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.TaskAction

public class DownloadTask extends CachedTask
{
    @Input
    def url;

    @OutputFile
    @CachedTask.Cached
    def output;

    @TaskAction
    public void doTask() throws IOException
    {
        output = getOutput()
        output.createNewFile()
        getLogger().info("Downloading " + url + " to " + output);

        def tempUrl = url instanceof Closure ? url.call() : url;

        while (tempUrl)
        {
            new URL(tempUrl).openConnection().with
                    { conn ->
                        conn.instanceFollowRedirects = false
                        tempUrl = conn.getHeaderField("Location")
                        if (!tempUrl)
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

        getLogger().info("Download complete");
    }

    File getOutput()
    {
        if (output instanceof File)
            return output;
        else
        {
            output = project.file(output);
            return output;
        }
    }
}
