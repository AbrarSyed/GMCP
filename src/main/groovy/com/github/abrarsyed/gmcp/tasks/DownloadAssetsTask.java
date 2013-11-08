package com.github.abrarsyed.gmcp.tasks;

import com.github.abrarsyed.gmcp.Constants;
import com.github.abrarsyed.gmcp.Util;
import com.google.common.io.ByteStreams;
import com.google.common.io.Files;
import groovy.util.Node;
import groovy.util.NodeList;
import groovy.util.XmlParser;
import org.gradle.api.DefaultTask;
import org.gradle.api.tasks.OutputDirectory;
import org.gradle.api.tasks.TaskAction;
import org.xml.sax.SAXException;

import javax.xml.parsers.ParserConfigurationException;
import java.io.BufferedInputStream;
import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;

public class DownloadAssetsTask extends DefaultTask
{
    File assetsDir;

    private final ConcurrentLinkedQueue<Asset> filesLeft = new ConcurrentLinkedQueue<Asset>();
    private final ArrayList<AssetsThread>      threads   = new ArrayList<AssetsThread>();

    @TaskAction
    public void doTask() throws ParserConfigurationException, SAXException, IOException, InterruptedException
    {
        assetsDir = getProject().file(assetsDir);
        assetsDir.mkdirs();

        // get resource XML file
        Node root = new XmlParser().parse(new BufferedInputStream((new URL(Constants.URL_ASSETS)).openStream()));

        getLogger().info("Parsing assets XML");

        // construct a list of [file, hash] maps
        for (Object childNode : ((NodeList) root.get("Contents")))
        {
            Node child = (Node) childNode;

            if (((NodeList) child.get("Size")).text().equals("0"))
            {
                continue;
            }

            String key = ((NodeList) child.get("Key")).text();
            String hash = ((NodeList) child.get("ETag")).text().replace('"', ' ').trim();
            filesLeft.offer(new Asset(key, hash));
        }

        getLogger().info("Finished parsing XML");
        getLogger().info("Files found: "+filesLeft.size());

        int threadNum = filesLeft.size()/100;
        for (int i = 0; i < threadNum; i++)
            spawnThread();

        getLogger().info("Threads initially spawned: "+threadNum);

        while (stillRunning())
        {
            spawnThread();
            Thread.sleep(1000);
        }
    }

    private void spawnThread()
    {
        if (threads.size() < 30)
        {
            getLogger().debug("Spawning thread #" + (threads.size() + 1));
            AssetsThread thread = new AssetsThread();
            thread.start();
            threads.add(thread);
        }
    }

    private boolean stillRunning()
    {
        for (Thread t : threads)
        {
            if (t.isAlive())
            {
                return true;
            }
        }
        getLogger().info("All "+threads.size()+" threads Complete");
        return false;
    }

    public File getAssetsDir()
    {
        return assetsDir;
    }

    public void setAssetsDir(File assetsDir)
    {
        this.assetsDir = assetsDir;
    }

    private class Asset
    {
        public final String path;
        public final String hash;

        Asset(String path, String hash)
        {
            this.path = path;
            this.hash = hash.toLowerCase();
        }
    }

    private class AssetsThread extends Thread
    {
        public AssetsThread()
        {
            this.setDaemon(true);
        }

        @Override
        public void run()
        {
            Asset asset;
            while ((asset = filesLeft.poll()) != null)
            {
                try
                {
                    boolean download = false;
                    File file = new File(assetsDir, asset.path);

                    if (!file.exists())
                    {
                        download = true;
                        file.getParentFile().mkdirs();
                        file.createNewFile();
                    }
                    else if (!Util.hash(file).toLowerCase().equals(asset.hash))
                    {
                        download = true;
                        file.delete();
                        file.createNewFile();
                    }

                    if (download)
                    {
                        URL url = new URL(Constants.URL_ASSETS + "/" + asset.path);
                        BufferedInputStream stream = new BufferedInputStream(url.openStream());
                        Files.write(ByteStreams.toByteArray(stream), file);
                        stream.close();
                    }
                }
                catch (Exception e)
                {
                    getLogger().error("Error downloading asset: " + asset.path);
                    e.printStackTrace();
                }
            }
        }
    }
}
