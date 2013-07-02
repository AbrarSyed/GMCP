package com.github.abrarsyed.gmcp

import net.md_5.specialsource.AccessMap
import net.md_5.specialsource.Jar
import net.md_5.specialsource.JarMapping
import net.md_5.specialsource.JarRemapper
import net.md_5.specialsource.RemapperPreprocessor
import net.md_5.specialsource.provider.JarProvider
import net.md_5.specialsource.provider.JointProvider

import org.gradle.api.DefaultTask
import org.gradle.api.tasks.TaskAction

public class SpecialSourceTask extends DefaultTask
{
	def target
	def output
	def srg
	List ats = []

	@TaskAction()
	def stuff()
	{
		// load mapping
		JarMapping mapping = new JarMapping()
		if (srg)
			mapping.loadMappings(project.file(srg))

		// load in AT
		def accessMap = new AccessMap()
		ats.collect() {
			accessMap.loadAccessTransformer(project.file(it))
		}
		def processor = new  RemapperPreprocessor(null, mapping, accessMap)

		// make remapper
		JarRemapper remapper = new JarRemapper(processor, mapping)

		// load jar
		Jar input = Jar.init(project.file(target))

		// ensure that inheritance provider is used
		JointProvider inheritanceProviders = new JointProvider()
		inheritanceProviders.add(new JarProvider(input))
		mapping.setFallbackInheritanceProvider(inheritanceProviders)

		// remap jar
		remapper.remapJar(input, project.file(target))
	}
}
