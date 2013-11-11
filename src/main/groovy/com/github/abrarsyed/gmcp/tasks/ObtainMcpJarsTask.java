package com.github.abrarsyed.gmcp.tasks;

import com.google.common.io.ByteStreams;
import com.google.common.io.Files;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.OutputFile;
import org.gradle.api.tasks.TaskAction;

import java.io.File;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Locale;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

public class ObtainMcpJarsTask extends CachedTask
{
    @Input
    private String mcpUrl;

    @OutputFile
    @Cached
    private Object ffJar;

    @OutputFile
    @Cached
    private Object injectorJar;

    @TaskAction
    public void doTask() throws MalformedURLException, IOException
    {
        File ffJar = getFfJar();
        File injectorJar = getInjectorJar();

        getLogger().info("Downloading " + mcpUrl);
        getLogger().info("Fernflower output location " + ffJar);
        getLogger().info("Injector output location " + injectorJar);

        HttpURLConnection connect = (HttpURLConnection) (new URL(mcpUrl)).openConnection();
        connect.setInstanceFollowRedirects(true);

        final ZipInputStream zin = new ZipInputStream(connect.getInputStream());
        ZipEntry entry = null;

        while ((entry = zin.getNextEntry()) != null)
        {

            if (entry.getName().toLowerCase(Locale.ENGLISH).endsWith("fernflower.jar"))
            {
                ffJar.getParentFile().mkdirs();
                Files.touch(ffJar);
                Files.write(ByteStreams.toByteArray(zin), ffJar);
            }
            else if (entry.getName().toLowerCase(Locale.ENGLISH).endsWith("mcinjector.jar"))
            {
                injectorJar.getParentFile().mkdirs();
                Files.touch(injectorJar);
                Files.write(ByteStreams.toByteArray(zin), injectorJar);
            }
        }

        zin.close();

        getLogger().info("Download and Extraction complete");
    }

    public File getFfJar()
    {
        if (ffJar instanceof File)
            return (File) ffJar;
        else
        {
            ffJar = getProject().file(ffJar);
            return (File) ffJar;
        }
    }

    public void setFfJar(Object ffJar)
    {
        this.ffJar = ffJar;
    }

    public File getInjectorJar()
    {
        if (injectorJar instanceof File)
            return (File) injectorJar;
        else
        {
            injectorJar = getProject().file(injectorJar);
            return (File) injectorJar;
        }
    }

    public void setInjectorJar(Object injectorJar)
    {
        this.injectorJar = injectorJar;
    }

    public String getMcpUrl()
    {
        return mcpUrl;
    }

    public void setMcpUrl(String mcpUrl)
    {
        this.mcpUrl = mcpUrl;
    }

}

