package com.github.abrarsyed.gmcp;

import org.gradle.api.Plugin
import org.gradle.api.Project

public class GMCP implements Plugin<Project>
{

	@Override
	public void apply(Project project)
	{
		// set version stuff
		project.ext.mcVersion = "latest";
		project.ext.forgeVersion = "latest";
	}

}

/*
class GreetingPlugin implements Plugin<Project> {
	void apply(Project project) {
		project.task('hello') << {
			println "Hello from the GreetingPlugin"
		}
	}
}
*/
