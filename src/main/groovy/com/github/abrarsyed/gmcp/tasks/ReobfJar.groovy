package com.github.abrarsyed.gmcp.tasks

import net.md_5.specialsource.JarMapping
import net.md_5.specialsource.JarRemapper
import net.md_5.specialsource.provider.JarProvider
import net.md_5.specialsource.provider.JointProvider

import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.bundling.Jar

import com.github.abrarsyed.gmcp.Constants
import com.github.abrarsyed.gmcp.Util
import com.google.common.io.Files

public class ReobfJar extends Jar
{

    @InputFile
    /**
     * SRG file to use.
     */
    File srg = Util.baseFile(Constants.DIR_MAPPINGS, 'reobf_srg.srg')

    @Input
    /**
     * Packages to exclude. Remember, packages are not hierarchical.
     */
    Set<String> packExcludes

    public ReobfJar()
    {
        super()
        this.doLast {
            reobf()
        }
    }

    public void reobf()
    {
        def file = this.archivePath
        project.logger.lifecycle ' archive path is '+file
        def inTemp = Util.file(temporaryDir, 'jarIn.jar')
        Files.copy(file, inTemp)
        file.delete()

        // load mapping
        JarMapping mapping = new JarMapping()
        mapping.loadMappings(srg)

        // make remapper
        JarRemapper remapper = new JarRemapper(null, mapping)

        // load jar
        def input = net.md_5.specialsource.Jar.init(inTemp)

        // ensure that inheritance provider is used
        JointProvider inheritanceProviders = new JointProvider()
        inheritanceProviders.add(new JarProvider(input))
        mapping.setFallbackInheritanceProvider(inheritanceProviders)

        // remap jar
        remapper.remapJar(input, file)
    }
}
