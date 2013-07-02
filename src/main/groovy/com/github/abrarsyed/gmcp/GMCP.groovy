package com.github.abrarsyed.gmcp

import org.gradle.api.Plugin
import org.gradle.api.Project

import com.github.abrarsyed.gmcp.Constants.OperatingSystem
import com.github.abrarsyed.gmcp.extensions.GMCPExtension
import com.github.abrarsyed.gmcp.extensions.ModInfoExtension

public class GMCP implements Plugin<Project>
{
	//public GMCPExtension ext
	public OperatingSystem os

	@Override
	public void apply(Project project)
	{
		// make extensions and set variables
		project.extensions.create("minecraft", GMCPExtension)
		//ext = project.minecraft
		project.minecraft.extensions.create("mcmodinfo", ModInfoExtension)

		// ensure java is in.
		project.apply( plugin: "java")

		// get os
		os = Util.getOS()
		
		// start the tasks
		downloadTasks(project)
	}

	def downloadTasks(Project project)
	{
		// Get Forge
		project.task('getForge') << {
			def base = project.file(project.minecraft.baseDir)
			base.mkdirs()
			def forgeZip = new File(base, "/forge.zip")
			Util.download(project.minecraft.forgeURL, forgeZip)
			Util.unzip(forgeZip, project.file(project.minecraft.baseDir), false)
			forgeZip.delete()
		}

		// download necessary stuff.
		project.task('getMinecraft', dependsOn: "getForge") << {
			def root = project.file(project.minecraft.baseDir+"/"+Constants.DIR_MC_JARS)
			root.mkdirs()

			// read config
			ConfigParser parser = new ConfigParser(project.minecraft.baseDir+"/"+Constants.DIR_FML+"/"+"mc_versions.cfg")
			def baseUrl = parser.getProperty("default", "base_url")

			project.logger.lifecycle "Downloading Minecraft"
			def mcver = parser.getProperty("default", "current_ver")
			Util.download(parser.getProperty(mcver, "client_url"), project.file(project.minecraft.baseDir+"/"+Constants.JAR_CLIENT))
			Util.download(parser.getProperty(mcver, "server_url"), project.file(project.minecraft.baseDir+"/"+Constants.JAR_SERVER))

			project.logger.lifecycle "Downloading libraries"
			def dls = parser.getProperty("default", "libraries").split(/\s/)
			dls.each { Util.download(baseUrl+it, project.file(root+"/"+it)) }

			project.logger.lifecycle "Downloading natives"
			def nativesJar = project.file(project.minecraft.baseDir+"/"+"natives.jar")
			def nativesName = parser.getProperty("default", "natives").split(/\s/)[os.ordinal()]
			Util.download(baseUrl + nativesName, nativesJar)

			Util.unzip(nativesJar, project.file(root+"/"+"natives"), true)
			nativesJar.delete()
		}
	}

}