package com.github.abrarsyed.gmcp

import java.io.File;
import java.security.MessageDigest
import java.util.zip.ZipEntry
import java.util.zip.ZipFile

import com.github.abrarsyed.gmcp.Constants.OperatingSystem

class Util
{

	def static download(String url, File output)
	{
		output.createNewFile()
		while(url)
		{
			new URL(url).openConnection().with
			{ conn ->
				conn.instanceFollowRedirects = false
				url = conn.getHeaderField( "Location" )
				if( !url )
				{
					output.withOutputStream
					{ out ->
						conn.inputStream.with
						{ inp ->
							out << inp
							inp.close()
						}
					}
				}
			}
		}
	}

	public static OperatingSystem getOS()
	{
		def name = System.properties["os.name"].toString().toLowerCase()
		if (name.contains("windows"))
			OperatingSystem.WINDOWS
		else if (name.contains("mac"))
			OperatingSystem.MAC
		else if (name.contains("linux"))
			OperatingSystem.LINUX
		else
			null
	}

	public static createOrCleanDir(File file)
	{
		
		if (file.exists() && file.isDirectory())
		{
			file.eachFile
			{
				if (it.isDirectory())
					it.deleteDir()
				else
					it.delete()
			}
		}
		else
			file.mkdirs()
	}
	
	def static String getRelative(File root, File file)
	{
		return file.getAbsolutePath().substring(root.getAbsolutePath().length());
	}
}
