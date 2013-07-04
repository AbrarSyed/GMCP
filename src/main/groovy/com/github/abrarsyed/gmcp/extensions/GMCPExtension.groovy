package com.github.abrarsyed.gmcp.extensions

import java.io.File;

import org.gradle.api.Nullable

import argo.jdom.JdomParser
import argo.jdom.JsonRootNode

import com.github.abrarsyed.gmcp.Constants;
import com.github.abrarsyed.gmcp.exceptions.MalformedVersionException

class GMCPExtension
{
	@Nullable
	private String minecraftVersion
	private forgeVersion = "latest"
	def String forgeURL
	def baseDir = "minecraft"
    def jarDir = "minecraft/jars"
	def accessTransformers = []

	private resolved = false

	private static final JdomParser JDOM_PARSER = new JdomParser()

	public void setForgeVersion(Object obj)
	{
		if (obj instanceof String)
			obj = obj.toLowerCase()
		forgeVersion = obj
		resolved = false
	}

	public void setMinecraftVersion(String obj)
	{
		if (obj instanceof String)
			obj = obj.toLowerCase()
		minecraftVersion = obj
		resolved = false
	}

	public void setForgeURL(String str)
	{
		resolved = true
		forgeURL = str
	}

	public String getForgeVersion()
	{
		if (!resolved)
			resolve()

		forgeVersion
	}

	public String getMinecraftVersion()
	{
		if (!resolved)
			resolve()

		minecraftVersion
	}

	public String getForgeURL()
	{
		if (!resolved)
			resolve()

		forgeURL
	}

	protected void resolve()
	{
		String jsonText = Constants.URL_JSON_FORGE.toURL().text
		JsonRootNode root = JDOM_PARSER.parse(jsonText)

		def builds = root.getArrayNode("builds")
		def files, temp, finished = false

		// build number is defined.  ignore MC version
		if (forgeVersion.toString().isInteger())
		{
			// loop through builds array
			for (int i = 0; i < builds.size() && !finished; i++)
			{
				// check for build number match
				if (builds[i].getNumberValue("build") == forgeVersion)
				{
					files = builds[i].getArrayNode("files")
					// loop through files to find src download
					for (int j = 0; j < files.size() && !finished; j++)
					{
						temp = files[j]
						if (temp.getStringValue("buildtype") == "src")
						{
							// find and get properties from it.
							forgeVersion = builds[i].getStringValue("version")
							minecraftVersion = temp.getStringValue("mcver")
							forgeURL = temp.getStringValue("url")
							finished = true
						}
					}
				}
			}
		}
		else if (forgeVersion instanceof String)
		{
			def string = (forgeVersion as String).trim().toLowerCase()

			if (string == "latest")
			{
				// loop through builds array
				for (int i = 0; i < builds.size() && !finished; i++)
				{
					// loop through files array
					files = builds[i].getArrayNode("files")
					for (int j = 0; j < files.size() && !finished; j++)
					{
						temp = files[j]
						if (minecraftVersion) // is MC version set?
						{
							// if so, check the MC version of this file
							if (temp.getStringValue("mcver") == minecraftVersion)
							{
								// IT MATCHES! grab this info and leave.
								// must be src build to get the url though...
								if (temp.getStringValue("buildtype") == "src")
								{
									forgeVersion = builds[i].getStringValue("version")
									minecraftVersion = temp.getStringValue("mcver")
									forgeURL = temp.getStringValue("url")
									finished = true
								}
							}
							else
							// break out of this build.
							break
						}
					}
				}
			}
			else
			{
				// doesn't match forge version.
				if (!(forgeVersion ==~ /\d+\.\d+\.\d+\.\d+/))
					throw new MalformedVersionException()

				// loop through builds array
				for (int i = 0; i < builds.size() && !finished; i++)
				{
					// check for build number match
					if (builds[i].isNumberValue("version") == forgeVersion)
					{
						files = builds[i].getArrayNode("files")
						// loop through files to find src download
						for (int j = 0; j < files.size() && !finished; j++)
						{
							temp = files[j]
							if (temp.getStringValue("buildtype") == "src")
							{
								// find and get properties from it.
								forgeVersion = builds[i].getStringValue("version")
								minecraftVersion = temp.getStringValue("mcver")
								forgeURL = temp.getStringValue("url")
								finished = true
							}
						}
					}
				}
			}
		}

		resolved = true
	}
}
