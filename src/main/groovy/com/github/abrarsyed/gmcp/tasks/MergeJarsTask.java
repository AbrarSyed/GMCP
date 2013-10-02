package com.github.abrarsyed.gmcp.tasks;

import com.github.abrarsyed.gmcp.Constants;
import com.github.abrarsyed.gmcp.Util;
import com.google.common.base.Joiner;
import com.google.common.io.Files;
import groovy.lang.Closure;
import lombok.Getter;
import lombok.Setter;
import org.gradle.api.tasks.InputFile;
import org.gradle.api.tasks.OutputFile;
import org.gradle.api.tasks.TaskAction;
import org.gradle.process.ExecSpec;
import org.gradle.process.JavaExecSpec;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

import static com.github.abrarsyed.gmcp.Util.baseFile;

public class MergeJarsTask extends CachedTask
{
    @Setter
    @InputFile
    private Object mergeCfg;

    @Setter
    @InputFile
    private Object client;

    @Setter
    @InputFile
    private Object server;

    @Setter
    @OutputFile
    @Cached
    private Object outJar;

    @TaskAction
    public void doTask() throws IOException
    {
        // since it merges everything to the client Jar. need this to keep it the same.
        File tempJar = new File(getTemporaryDir(), "tempClient.jar");
        Files.copy((File) client, tempJar);

        // compile merger
        compileMerger(baseFile(Constants.DIR_FML, "common"), this.getTemporaryDir());

        // call the merger.
        executeMerger(this.getTemporaryDir(), tempJar, (File) server, (File) mergeCfg);

        // copy and strip meta inf to the ACTUAL output.
        ZipInputStream in = new ZipInputStream(new FileInputStream(tempJar));
        ZipOutputStream out = new ZipOutputStream(new FileOutputStream((File) outJar));

        ZipEntry entry = in.getNextEntry();
        while (entry != null)
        {
            // ignore meta-inf
            if (!entry.getName().contains("META-INF"))
            {
                // write to the other.
                out.putNextEntry(new ZipEntry(entry.getName()));

                // write the actual data
                int tempData;
                while ((tempData = in.read()) != -1)
                {
                    out.write(tempData);
                }
            }

            // read the next one.
            entry = in.getNextEntry();
        }

        in.close();
        out.close();
    }

    private void compileMerger(final File inDir, final File outDir)
    {
        getProject().exec(new Closure(getProject())
        {
            @Override
            public Object call()
            {
                ExecSpec exec = (ExecSpec) getDelegate();

                exec.args(
                        "-deprecation",
                        "-g",
                        "-source",
                        "1.6",
                        "-target",
                        "1.6",
                        "-classpath",
                        Joiner.on(File.pathSeparatorChar).join(Util.getClassPath()), // the classpath.
                        "-sourcepath",
                        inDir.getAbsolutePath(),
                        "-d",
                        outDir.getAbsolutePath(),

                        // target to be recompiled.
                        Util.baseFile(Constants.DIR_FML, "common/cpw/mods/fml/common/asm/transformers/MCPMerger.java")
                );

                exec.setExecutable("javac");

                exec.setStandardOutput(Util.getNullStream());
                exec.setErrorOutput(Util.getNullStream());

                return exec;
            }
        });
    }

    private void executeMerger(final File classDir, final File client, final File server, final File config)
    {
        // http://www.gradle.org/docs/current/dsl/org.gradle.api.tasks.JavaExec.html
        getProject().javaexec(new Closure(this, this)
        {
            @Override
            public Object call()
            {
                JavaExecSpec exec = (JavaExecSpec) getDelegate();

                exec.args(
                        config.getAbsolutePath(),
                        client.getAbsolutePath(),
                        server.getAbsolutePath()
                );

                exec.setMain("cpw.mods.fml.common.asm.transformers.MCPMerger");

                exec.classpath(classDir);
                exec.classpath(Util.getClassPath());

                exec.setStandardOutput(Util.getNullStream());

                return exec;
            }
        });
    }

    public File getClient()
    {
        if (client instanceof File)
            return (File)client;
        else
        {
            client = getProject().file(client);
            return (File)client;
        }
    }

    public File getMergeCfg()
    {
        if (mergeCfg instanceof File)
            return (File)mergeCfg;
        else
        {
            mergeCfg = getProject().file(mergeCfg);
            return (File)mergeCfg;
        }
    }

    public File getServer()
    {
        if (server instanceof File)
            return (File)server;
        else
        {
            server = getProject().file(server);
            return (File)server;
        }
    }

    public File getOutJar()
    {
        if (outJar instanceof File)
            return (File)outJar;
        else
        {
            outJar = getProject().file(outJar);
            return (File)outJar;
        }
    }
}
