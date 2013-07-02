package com.github.abrarsyed.gmcp

import org.gradle.api.Plugin
import org.gradle.api.Project

public class GMCP implements Plugin<Project>
{
	@Override
	public void apply(Project project)
	{
		project.extensions.create("minecraft", GMCPExtension)

		// ensure java is in.
		project.apply( plugin: "java")


		// Get Forge
		project.task('getForge')
		{
			project.file(".gradle").mkdirs()
			def forgeZip = project.file(".gradle\forge.zip")
			def forgeFolder = project.file("forge")
			Util.download(project.minecraft.forgeURL, project.file(".gradle\forge.zip"))
			Util.unzip(forgeZip, forgeFolder, false)
			forgeZip.delete()
		}
		
		// download necessary stuff.
		project.task('getOtherStuff')
		{
			
		}

	}

}