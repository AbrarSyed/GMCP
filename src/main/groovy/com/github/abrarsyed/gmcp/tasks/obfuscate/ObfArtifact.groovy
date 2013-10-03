package com.github.abrarsyed.gmcp.tasks.obfuscate

import com.github.abrarsyed.gmcp.Constants
import com.github.abrarsyed.gmcp.GMCP
import com.github.abrarsyed.gmcp.Util
import com.google.common.io.Files
import net.md_5.specialsource.InheritanceMap
import net.md_5.specialsource.Jar
import net.md_5.specialsource.JarMapping
import net.md_5.specialsource.JarRemapper
import net.md_5.specialsource.provider.JarProvider
import net.md_5.specialsource.provider.JointProvider
import org.gradle.api.InvalidUserDataException
import org.gradle.api.Task
import org.gradle.api.artifacts.PublishArtifact
import org.gradle.api.internal.artifacts.publish.AbstractPublishArtifact


class ObfArtifact extends AbstractPublishArtifact
{
    final PublishArtifact toObfArtifact

    String name
    String extension
    String type
    String classifier
    Date date
    File file

    def srg

    private final Closure toObfGenerator
    private final Task caller

    final ArtifactSpec outputSpec

    /**
     * Creates an obfuscated artifact for the given public artifact.
     *
     * <p>The file to obfuscate will be the file of the given artifact and the name of this obfuscated artifact
     * will default to the name of the given artifact to obfuscate.</p>
     * <p>
     * The artifact to obfuscate may change after being used as the source.</p>
     *
     * @param toObf The artifact that is to be obfuscated
     * @param artifactSpec The specification of how the obfuscated artifact is to be named
     * @param task The task(s) that will invoke {@link #generate()} on this jar (optional)
     */
    ObfArtifact(PublishArtifact toObf, ArtifactSpec artifactSpec, ReobfTask task)
    {
        this({ toObf.file }, artifactSpec, task)
        this.toObfArtifact = toObf
    }

    /**
     * Creates an obfuscated artifact for the given file.
     *
     * @param toObf The file that is to be obfuscated
     * @param artifactSpec The specification of how the obfuscated artifact is to be named
     * @param task The task(s) that will invoke {@link #generate()} on this jar (optional)
     */
    ObfArtifact(File toObf, ArtifactSpec artifactSpec, ReobfTask task)
    {
        this({ toObf }, artifactSpec, task)
        this.toObfArtifact = toObf
    }

    /**
     * Creates an obfuscated artifact for the file returned by the {@code toObf} closure.
     *
     * <p>The closures will be “evaluated” on demand whenever the value is needed (e.g. at generation time)</p>
     *
     * @param toObf A closure that produces a File for the object to obfuscate (non File return values will be used as the path to the file)
     * @param outputSpec The specification of artifact to outputted
     * @param task The task(s) that will invoke {@link #generate()} on this jar (optional)
     */
    ObfArtifact(Closure toObf, ArtifactSpec outputSpec, ReobfTask task)
    {
        super(task)
        this.caller = task
        toObfGenerator = toObf
        this.outputSpec = outputSpec
        this.srg = task.srg;
    }

    /**
     * The file that is to be obfuscated.
     *
     * @return The file. May be {@code null} if unknown at this time.
     */
    File getToObf()
    {
        def toObf = toObfGenerator?.call()

        if (toObf == null)
            null
        else if (toObf instanceof File)
            toObf
        else
            new File(toObf.toString())
    }

    /**
     * The name of the obfuscated artifact.
     *
     * <p>Defaults to the name of the obfuscated artifact {@link #getFile() file}.
     *
     * @return The name. May be {@code null} if unknown at this time.
     */
    String getName()
    {
        if (name)
            return name
        else if (toObfArtifact)
            return toObfArtifact.name
        else if (outputSpec.baseName)
            return outputSpec.baseName
        else
            return getFile()?.name
    }

    /**
     * The extension of the obfuscated artifact.
     *
     * <p>Defaults to '.jar'.</p>
     *
     * @return The extension. May be {@code null} if unknown at this time.
     */
    String getExtension()
    {
        if (extension)
            return extension
        else if (toObfArtifact)
            return toObfArtifact.extension
        else if (outputSpec.extension)
            return outputSpec.extension
        else
            return Files.getFileExtension(getFile()?.name)
    }

    String getType()
    {
        return getExtension()
    }

    /**
     * The classifier of the obfuscated artifact.
     *
     * <p>Defaults to the classifier of the source artifact (if obfuscating an artifact)
     * or the given classifier at construction (if given).</p>
     *
     * @return The classifier. May be {@code null} if unknown at this time.
     */
    String getClassifier()
    {
        if (classifier)
            return classifier
        else if (toObfArtifact)
            return toObfArtifact.classifier
        else if (outputSpec.classifier)
            return outputSpec.classifier
        else
            return null
    }

    /**
     * The date of the obfuscated artifact.
     *
     * <p>Defaults to the last modified time of the {@link #getFile() obfuscated file} (if exists)</p>
     *
     * @return The date of the obfuscation. May be {@code null} if unknown at this time.
     */
    Date getDate()
    {
        if (date == null)
        {
            def file = getFile()
            if (file == null)
                null
            else
            {
                def modified = file.lastModified()
                if (modified == 0)
                    null
                else
                    new Date(modified)
            }
        }
        else
            date
    }

    /**
     * The file for the obfuscated artifact, which may not yet exist.
     *
     * <p>Defaults to a the {@link #getToObf()} () file to obfuscate}</p>
     *
     * @return The obfuscated file. May be {@code null} if unknown at this time.
     */
    File getFile()
    {
        if (file == null)
        {
            def input = getToObf()

            outputSpec.resolve()
            this.name = outputSpec.archiveName
            this.classifier = outputSpec.classifier
            this.extension = outputSpec.extension

            if (outputSpec.srg)
                this.srg = outputSpec.srg

            file = Util.file(input.getParentFile(), outputSpec.archiveName)
            return file
        }
        else
        {
            file
        }
    }

    /**
     * Obfuscates the file
     *
     * @throws org.gradle.api.InvalidUserDataException if the there is insufficient information available to generate the signature.
     */
    void generate()
    {
        def toObf = getToObf()
        if (toObf == null)
        {
            throw new InvalidUserDataException("Unable to obfuscate as the file to obfuscate has not been specified")
        }

        File srg = GMCP.project.file(this.srg)
        if (srg == null)
        {
            throw new InvalidUserDataException("Unable to obfuscate '$toObf' as no srg is available to remap to")
        }

        def output = getFile()

        // obfuscate here
        def inTemp = Util.file(caller.temporaryDir , 'jarIn.jar')
        Files.copy(toObf, inTemp)

        //def deobfed = Util.file(Constants.JAR_SRG)

        // load mapping
        JarMapping mapping = new JarMapping()
        mapping.loadMappings(srg)

        // make remapper
        JarRemapper remapper = new JarRemapper(null, mapping)

        // load jar
        def input = Jar.init(inTemp)

        // construct inheritance map
        def inhMap = new InheritanceMap();
        inhMap.load(Util.cacheFile(String.format(Constants.FMED_INH_MAP, project.minecraft.minecraftVersion)).newReader(), null)

        // ensure that inheritance provider is used
        JointProvider inheritanceProviders = new JointProvider()
        inheritanceProviders.add(new JarProvider(input))
        //inheritanceProviders.add(inhMap)
        //inheritanceProviders.add(new JarProvider(Jar.init(deobfed)))
        mapping.setFallbackInheritanceProvider(inheritanceProviders)

        // remap jar
        remapper.remapJar(input, output)
    }
}
