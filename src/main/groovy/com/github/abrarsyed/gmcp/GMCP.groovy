package com.github.abrarsyed.gmcp

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.tasks.Copy

public class GMCP implements Plugin<Project>
{
	@Override
	public void apply(Project project)
	{
		project.extensions.create("minecraft", GMCPExtension)

		// Download stuff
//		project.task('downloadStuff', type: Copy) {
//			ant.get(project.minecraft.forgeUrl, project.buildDir, false)
//		}

	}

}