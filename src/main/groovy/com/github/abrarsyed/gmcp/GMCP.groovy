package com.github.abrarsyed.gmcp;

import org.gradle.api.Plugin
import org.gradle.api.Project

public class GMCP implements Plugin<Project>
{
	@Override
	public void apply(Project project)
	{
		project.extensions.create("minecraft", GMCPExtension)
	}

}