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
		jarTasks()
	}

	def downloadTasks()
	{
		// Get Forge task
		def task = project.task('getForge') {
			outputs.dir project.minecraft.baseDir
			outputs.dir project.minecraft.baseDir+"/forge"
		}
		task << {
			def base = file(project.minecraft.baseDir)
			base.mkdirs()
			def forgeZip = new File(temporaryDir, "/forge.zip")
			Util.download(project.minecraft.forgeURL, forgeZip)
			Util.unzip(forgeZip, file(project.minecraft.baseDir), false)
			forgeZip.delete()
		}


		// ----------------------------------------------------------------------------
		// download necessary stuff.
		task = project.task('getMinecraft', dependsOn: "getForge") {
			inputs.file baseFile(Constants.DIR_FML, "mc_versions.cfg")

			outputs.with {
				dir project.minecraft.baseDir+"/"+Constants.DIR_MC_JARS
				dir project.minecraft.baseDir+"/"+Constants.DIR_MC_JARS + "/" + "natives"
				file project.minecraft.baseDir+"/"+Constants.JAR_CLIENT
				file project.minecraft.baseDir+"/"+Constants.JAR_SERVER
			}
		}
		task.doLast {
			def root = baseFile Constants.DIR_MC_JARS
			root.mkdirs()

			// read config
			ConfigParser parser = new ConfigParser(baseFile(Constants.DIR_FML, "mc_versions.cfg"))
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

		// ----------------------------------------------------------------------------
		// to do the package changes
		task = project.task('doFMLPreProcess', dependsOn: "getMinecraft") {
			inputs.with {
				dir baseFile(Constants.DIR_MAPPINGS)
				file baseFile(Constants.DIR_MAPPINGS, Constants.CSVS["packages"])
				file baseFile(Constants.DIR_MAPPINGS, "joined.srg")
				file baseFile(Constants.DIR_MAPPINGS, "joined.exc")
				file baseFile(Constants.DIR_MCP_PATCHES, "minecraft_ff.patch")
				file baseFile(Constants.DIR_MCP_PATCHES, "minecraft_ff.patch")

			}

			outputs.with {
				file baseFile(Constants.DIR_MAPPINGS, "packaged.srg")
				file baseFile(Constants.DIR_MAPPINGS, "packaged.exc")
				file baseFile(Constants.DIR_MCP_PATCHES, "minecraft_ff.patch")
				file baseFile(Constants.DIR_MCP_PATCHES, "minecraft_ff.patch")
			}
		}
		task << {
			(new PackageFixer(baseFile(Constants.DIR_MAPPINGS, Constants.CSVS["packages"]))).with {
				// calls the following on the package fixer.
				// gotta love groovy :)
				fixSRG(baseFile(Constants.DIR_MAPPINGS, "joined.srg"), baseFile(Constants.DIR_MAPPINGS, "packaged.srg"))
				fixExceptor(baseFile(Constants.DIR_MAPPINGS, "joined.exc"), baseFile(Constants.DIR_MAPPINGS, "packaged.exc"))
				fixPatch(baseFile(Constants.DIR_MCP_PATCHES, "minecraft_ff.patch"))
				fixPatch(baseFile(Constants.DIR_MCP_PATCHES, "minecraft_server_ff.patch"))
			}
		}
	}

	def jarTasks()
	{
		// merge jars task
		def task = project.task('mergeMinecraftJars', dependsOn: "doFMLPreProcess") {
			inputs.with {
				file baseFile(Constants.JAR_CLIENT)
				file baseFile(Constants.JAR_SERVER)
				file baseFile(Constants.DIR_FML, "mcp_merge.cfg")
			}

			outputs.with {
				file baseFile(Constants.JAR_MERGED)
			}
		}
		task << {
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

		task = project.task('deobfuscateMinecraft', type: SpecialSourceTask, dependsOn: "mergeMinecraftJars") {

			inputs.with {
				file baseFile(Constants.JAR_MERGED)
				file baseFile(Constants.DIR_MAPPINGS, "packaged.srg")
				file baseFile(Constants.DIR_FML, "common/fml_at.cfg")
				file baseFile(Constants.DIR_FORGE, "common/forge_at.cfg")
				project.minecraft.accessTransformers.collect { file it }

				outputs.with {
					file baseFile(Constants.JAR_DEOBF)
				}
			}

			input = baseFile(Constants.JAR_MERGED)
			output = baseFile(Constants.JAR_DEOBF)
			srg = baseFile(Constants.DIR_MAPPINGS, "packaged.srg")
			ats.addAll project.minecraft.accessTransformers
			ats += baseFile(Constants.DIR_FML, "common/fml_at.cfg")
			ats += baseFile(Constants.DIR_FORGE, "common/forge_at.cfg")
		}

		//		task = project.task("deobfuscateMinecraft", dependsOn: "mergeMinecraftJars") << {
		//			// load mapping
		//			JarMapping mapping = new JarMapping()
		//			mapping.loadMappings(baseFile(Constants.DIR_MAPPINGS, "packaged.srg"))
		//
		//			// load in AT
		//			def accessMap = new AccessMap()
		//			accessMap.loadAccessTransformer(baseFile(Constants.DIR_FML, "common/fml_at.cfg"))
		//			accessMap.loadAccessTransformer(baseFile(Constants.DIR_FORGE, "common/forge_at.cfg"))
		//			project.minecraft.accessTransformers.collect {
		//				accessMap.loadAccessTransformer(project.file(Constants.DIR_FORGE, "common/forge_at.cfg"))
		//			}
		//			def processor = new  RemapperPreprocessor(null, mapping, accessMap)
		//
		//			// make remapper
		//			JarRemapper remapper = new JarRemapper(processor, mapping)
		//
		//			// load jar
		//			Jar input = Jar.init(baseFile(Constants.JAR_MERGED))
		//
		//			// ensure that inheritance provider is used
		//			JointProvider inheritanceProviders = new JointProvider()
		//			inheritanceProviders.add(new JarProvider(input))
		//			mapping.setFallbackInheritanceProvider(inheritanceProviders)
		//
		//			// remap jar
		//			remapper.remapJar(input, baseFile(Constants.JAR_DEOBF))
		//		}

	}

	def File file(String... args)
	{
		return new File(args.join("/"))
	}

	def File file(File file, String... args)
	{
		return new File(file, args.join("/"))
	}

	def File baseFile(String... args)
	{
		def arguments = []
		arguments += project.minecraft.baseDir
		arguments.addAll(args)

		return file(arguments as String[])
	}

}