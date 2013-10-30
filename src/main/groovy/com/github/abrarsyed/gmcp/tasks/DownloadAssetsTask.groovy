package com.github.abrarsyed.gmcp.tasks

import com.github.abrarsyed.gmcp.Constants
import com.github.abrarsyed.gmcp.Util
import org.gradle.api.DefaultTask
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.TaskAction

public class DownloadAssetsTask extends DefaultTask
{
    @OutputDirectory
    def assetsDir

    @TaskAction
    public void doTask()
    {
        assetsDir = project.file(assetsDir)
        assetsDir.mkdirs()

        // get resource XML file
        def rootNode = new XmlSlurper().parse(Constants.URL_ASSETS)

        // construct a list of [file, hash] maps
        def files = rootNode.Contents.collect {
            if (it.Size.text() != '0')
                [path: it.Key.text(), hash: it.ETag.text().replace('"', '')]
        }

        // define vars
        def download, file

        files.each {
            // skip empty entries.  if there are any...
            if (!it)
                return

            file = Util.file((File)assetsDir, it.path)

            download = true;

            if (file.exists())
                // check hash if it already exists
                download = it.hash != Util.hash(file)

            // if your gonna download, download it.
            if (download)
                Util.download(Constants.URL_ASSETS + '/' + it.path, file)
        }
    }
}
