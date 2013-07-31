package com.github.abrarsyed.gmcp.tasks.obfuscate

import com.github.abrarsyed.gmcp.Constants
import com.github.abrarsyed.gmcp.Util
import org.gradle.api.DefaultTask
import org.gradle.api.DomainObjectSet
import org.gradle.api.InvalidUserDataException
import org.gradle.api.Task
import org.gradle.api.artifacts.Configuration
import org.gradle.api.artifacts.PublishArtifact
import org.gradle.api.file.FileCollection
import org.gradle.api.internal.DefaultDomainObjectSet
import org.gradle.api.internal.file.collections.SimpleFileCollection
import org.gradle.api.tasks.TaskAction
import org.gradle.api.tasks.bundling.AbstractArchiveTask
import org.gradle.plugins.signing.Signature

/**
 * Created with IntelliJ IDEA.
 * User: AbrarSyed
 * Date: 7/30/13
 * Time: 6:30 PM
 * To change this template use File | Settings | File Templates.
 */
class ReobfTask extends DefaultTask
{
    // srg...
    def srg = Util.baseFile(Constants.DIR_MAPPINGS, 'reobf_srg.srg')

    final private DefaultDomainObjectSet<ObfArtifact> obfed = new DefaultDomainObjectSet<ObfArtifact>(ObfArtifact)

    ReobfTask()
    {
        super()

        // Have to include this in the up-to-date checks because the signatory may have changed
        inputs.file { srg }

        inputs.files { obfed*.getToObf() }
        outputs.files { obfed*.getToObf() }
    }

    void reobf(Task task, Closure artifactSpec)
    {
        if (!(task instanceof AbstractArchiveTask))
        {
            throw new InvalidUserDataException("You cannot sign tasks that are not 'archive' tasks, such as 'jar', 'zip' etc. (you tried to sign $task)")
        }

        def spec = new ArtifactSpec(task)
        artifactSpec.call(spec)

        dependsOn(task)
        addArtifact(new ObfArtifact({ task.archivePath }, spec, this))
    }

    /**
     * Configures the task to sign the archive produced for each of the given tasks (which must be archive tasks).
     */
    void reobf(Task... tasks)
    {
        for (task in tasks)
        {
            if (!(task instanceof AbstractArchiveTask))
            {
                throw new InvalidUserDataException("You cannot sign tasks that are not 'archive' tasks, such as 'jar', 'zip' etc. (you tried to sign $task)")
            }

            dependsOn(task)
            addArtifact(new ObfArtifact({ task.archivePath }, new ArtifactSpec(task), this))
        }
    }

    /**
     * Configures the task to sign each of the given artifacts
     */
    void reobf(PublishArtifact publishArtifact, Closure artifactSpec)
    {
        def spec = new ArtifactSpec(publishArtifact)
        artifactSpec.call(spec)

        dependsOn(publishArtifact)
        addArtifact(new ObfArtifact(publishArtifact, spec, this))
    }

    /**
     * Configures the task to sign each of the given artifacts
     */
    void reobf(PublishArtifact... publishArtifacts)
    {
        for (publishArtifact in publishArtifacts)
        {
            dependsOn(publishArtifact)
            addArtifact(new ObfArtifact(publishArtifact, new ArtifactSpec(publishArtifact), this))
        }
    }

    /**
     * Configures the task to reobf each of the given files
     */
    void reobf(File file, Closure artifactSpec)
    {
        def spec = new ArtifactSpec(file)
        artifactSpec.call(spec)

        addArtifact(new ObfArtifact(file, spec, this))
    }

    /**
     * Configures the task to reobf each of the given files
     */
    void reobf(File... files)
    {
        files.each { addArtifact(new ObfArtifact(it, new ArtifactSpec(it), this)) }
    }

    /**
     * Configures the task to obfuscate every artifact of the given configurations
     */
    void reobf(Configuration configuration, Closure artifactSpec)
    {
        configuration.allArtifacts.all { PublishArtifact artifact ->
            if (!(artifact instanceof ObfArtifact))
            {
                reobf(artifact, artifactSpec)
            }
        }
        configuration.allArtifacts.whenObjectRemoved { PublishArtifact artifact ->
            obfed.remove(obfed.find { it.toObfArtifact == artifact })
        }
    }

    /**
     * Configures the task to obfuscate every artifact of the given configurations
     */
    void reobf(Configuration... configurations)
    {
        for (configuration in configurations)
        {
            configuration.allArtifacts.all { PublishArtifact artifact ->
                if (!(artifact instanceof ObfArtifact))
                {
                    reobf(artifact)
                }
            }
            configuration.allArtifacts.whenObjectRemoved { PublishArtifact artifact ->
                obfed.remove(obfed.find { it.toreobfArtifact == artifact })
            }
        }
    }

    /**
     * Generates the signature files.
     */
    @TaskAction
    void generate() {
        if (srg == null) {
            throw new InvalidUserDataException("Cannot perform obfuscation task '${getPath()}' because it has no configured srg")
        }

        getObfuscated()*.generate()
    }

    private addArtifact(ObfArtifact artifact)
    {
        obfed.add(artifact)
    }

    /**
     * The signatures generated by this task.
     */
    DomainObjectSet<ObfArtifact> getObfuscated() {
        obfed
    }

    /**
     * All of the files that will be signed by this task.
     */
    FileCollection getFilesToObfuscate() {
        new SimpleFileCollection(*getObfuscated()*.toObf.findAll({ it != null }))
    }

    /**
     * All of the signature files that will be generated by this operation.
     */
    FileCollection getObfuscatedFiles() {
        new SimpleFileCollection(*getObfuscated()*.file.findAll({ it != null }))
    }
}
