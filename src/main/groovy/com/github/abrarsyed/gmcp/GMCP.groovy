package com.github.abrarsyed.gmcp

import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream

import org.gradle.api.Plugin
import org.gradle.api.Project

import com.github.abrarsyed.gmcp.Constants.OperatingSystem
import com.github.abrarsyed.gmcp.extensions.GMCPExtension
import com.github.abrarsyed.gmcp.extensions.ModInfoExtension

import cpw.mods.fml.common.asm.transformers.MCPMerger

public class GMCP implements Plugin<Project>
{
	//public GMCPExtension ext
	public OperatingSystem os
	private Project project

	@Override
	public void apply(Project project)
	{
		// se project
		this.project = project

		// make extensions and set variables
		project.extensions.create("minecraft", GMCPExtension)
		//ext = project.minecraft
		project.minecraft.extensions.create("mcmodinfo", ModInfoExtension)

		// ensure java is in.
		project.apply( plugin: "java")

		// get os
		os = Util.getOS()

		// start the tasks
		downloadTasks()
	}

	def downloadTasks()
	{
		// Get Forge task
		def task = project.task('getForge') << {
			def base = file(project.minecraft.baseDir)
			base.mkdirs()
			def forgeZip = new File(base, "/forge.zip")
			Util.download(project.minecraft.forgeURL, forgeZip)
			Util.unzip(forgeZip, file(project.minecraft.baseDir), false)
			forgeZip.delete()
		}
		// setup outputs
		task.getOutputs().dir(project.minecraft.baseDir + "/forge")


		// ----------------------------------------------------------------------------
		// download necessary stuff.
		task = project.task('getMinecraft', dependsOn: "getForge") << {
			def root = file(project.minecraft.baseDir+"/"+Constants.DIR_MC_JARS)
			root.mkdirs()

			// read config
			ConfigParser parser = new ConfigParser(project.minecraft.baseDir, Constants.DIR_FML, "mc_versions.cfg")
			def baseUrl = parser.getProperty("default", "base_url")

			project.logger.lifecycle "Downloading Minecraft"
			def mcver = parser.getProperty("default", "current_ver")
			Util.download(parser.getProperty(mcver, "client_url"), baseFile(Constants.JAR_CLIENT))
			Util.download(parser.getProperty(mcver, "server_url"), baseFile(Constants.JAR_SERVER))

			project.logger.lifecycle "Downloading libraries"
			def dls = parser.getProperty("default", "libraries").split(/\s/)
			dls.each { Util.download(baseUrl+it, new File(root, it)) }

			project.logger.lifecycle "Downloading natives"
			def nativesJar = baseFile("natives.jar")
			def nativesName = parser.getProperty("default", "natives").split(/\s/)[os.ordinal()]
			Util.download(baseUrl + nativesName, nativesJar)

			Util.unzip(nativesJar, file(root, "natives"), true)
			nativesJar.delete()
		}
		// setup more outputs
		task.getOutputs().with {
			dir project.minecraft.baseDir+"/"+Constants.DIR_MC_JARS
			dir project.minecraft.baseDir+"/"+Constants.DIR_MC_JARS + "/" + "natives"
			file project.minecraft.baseDir+"/"+Constants.JAR_CLIENT
			file project.minecraft.baseDir+"/"+Constants.JAR_SERVER
		}

		// ----------------------------------------------------------------------------
		// to do the package changes
		task = project.task('doFMLPreProcess', dependsOn: "getMinecraft") << {
			(new PackageFixer(baseFile(Constants.DIR_MAPPINGS, Constants.CSVS["packages"]))).with {
				// calls the following on the package fixer.
				// gotta love groovy :)
				fixSRG(baseFile(Constants.DIR_MAPPINGS, "joined.srg"), baseFile(Constants.DIR_MAPPINGS, "packaged.srg"))
				fixExceptor(baseFile(Constants.DIR_MAPPINGS, "joined.exc"), baseFile(Constants.DIR_MAPPINGS, "packaged.exc"))
				fixPatch(baseFile(Constants.DIR_MCP_PATCHES, "minecraft_ff.patch"))
				fixPatch(baseFile(Constants.DIR_MCP_PATCHES, "minecraft_server_ff.patch"))
			}
		}
		task.getOutputs().with {
			baseFile(Constants.DIR_MAPPINGS, "packaged.srg")
			baseFile(Constants.DIR_MAPPINGS, "packaged.exc")
		}
	}

	def jarTasks()
	{
		// merge jars task
		def task = project.task('mergeMinecraftJars', dependsOn: "doFMLPreProcess") << {

			def client = baseFile(Constants.JAR_CLIENT)
			def server = baseFile(Constants.JAR_SERVER)
			def merged = baseFile(Constants.JAR_MERGED)

			//Constants.JAR_CLIENT, Constants.JAR_SERVER
			String[] args = new String[3]
			args[0] = [
				project.minecraft.baseDir,
				Constants.DIR_FML,
				"mcp_merge.cfg"
			].join "/"
			args[1] = client.getPath()
			args[2] = server.getPath()
			MCPMerger.main(args)

			// copy and strip META-INF
			def output = new ZipOutputStream(merged.newDataOutputStream())
			def ZipFile input = new ZipFile(client)

			input.entries().each{ ZipEntry entry ->
				if (entry.name.contains("META-INF"))
					return
				else if (entry.size > 0)
				{
					output.putNextEntry(entry)
					output.write(input.getInputStream(entry).bytes)
					output.closeEntry()
				}
			}

			input.close()
			output.close()
		}
		// set output
		task.getOutputs().file baseFile(Constants.JAR_MERGED)
		
	}

	def File file(String... args)
	{
		return project.file(args.join("/"))
	}

	def File file(File file, String... args)
	{
		return project.file(new File(file, args.join("/")))
	}
	
	def File baseFile(String... args)
	{
		def arguments = []
		arguments += project.minecraft.baseDir
		arguments.addAll(args)
		
		return file(arguments as String[])
	}

}