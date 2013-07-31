package com.github.abrarsyed.gmcp.tasks.obfuscate

import com.github.abrarsyed.gmcp.Constants
import com.github.abrarsyed.gmcp.GMCP
import com.github.abrarsyed.gmcp.Util
import com.google.common.io.Files
import org.gradle.api.artifacts.PublishArtifact
import org.gradle.api.tasks.bundling.AbstractArchiveTask

/**
 * Created with IntelliJ IDEA.
 * User: AbrarSyed
 * Date: 7/31/13
 * Time: 2:14 AM
 * To change this template use File | Settings | File Templates.
 */
class ArtifactSpec
{
    def baseName
    def appendix
    def version
    def classifier
    def extension
    def archiveName

    def srg

    def private archiveSet = false

    ArtifactSpec() {}

    ArtifactSpec(File file)
    {
        archiveName = file.getName()
        extension = Files.getFileExtension(file.getName())
    }

    ArtifactSpec(String file)
    {
        archiveName = file
        extension = Files.getFileExtension(file)
    }

    ArtifactSpec(PublishArtifact artifact)
    {
        baseName = artifact.name
        classifier = artifact.classifier
        extension = artifact.extension
    }

    ArtifactSpec(AbstractArchiveTask task)
    {
        baseName = task.baseName
        appendix = task.appendix
        version = task.version
        classifier = task.classifier
        extension = task.extension
    }

    public void setArchiveName(archiveName)
    {
        this.archiveName = archiveName
        archiveSet = true
    }

    protected void resolve()
    {
        // resolves all the properties.
        this.properties = this.properties.collect { name, val ->

            // ignore the boolean.
            if (name == 'archiveSet' || name == 'srg')
                return [name, val]

            if (val instanceof Closure)
                val = val.call()?.toString()
            else if (val)
                val = val.toString()

            return [name, val]
        }

        // resolve srg
        if (srg.toLowerCase() == 'srg')
            srg = Util.baseFile(Constants.DIR_MAPPINGS, 'reobf_srg.srg')
        else if (srg.toLowerCase() == 'mc' || srg.toLowerCase() == 'mcp')
            srg = Util.baseFile(Constants.DIR_MAPPINGS, 'reobf_mcp.srg')
        else
            srg = GMCP.project.file(srg)

        // skip if its already been set by the user.
        if (archiveSet)
            return

        // build archiveName thing
        if (appendix && classifier)
            archiveName = "$baseName-$appendix-$version-$classifier.$extension"
        else if (appendix)
            archiveName = "$baseName-$appendix-$version.$extension"
        else if (classifier)
            archiveName = "$baseName-$version-$classifier.$extension"
    }
}
