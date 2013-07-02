package com.github.abrarsyed.gmcp

public final class Constants
{
	// root dirs
	public static final File	DIR_TEMP			= new File("tmp")
	public static final File    DIR_NATIVES            = new File("natives")

	// temp dirs
	public static final File	DIR_LOGS			= new File(DIR_TEMP, "logs")
	public static final File	DIR_EXTRACTED		= new File(DIR_TEMP, "extracted")
	public static final File	DIR_CLASSES			= new File(DIR_TEMP, "classes")
	public static final File	DIR_SOURCES			= new File(DIR_TEMP, "sources")
	public static final File 	DIR_MC_JARS 		= new File(DIR_TEMP, "jars")
	public static final File 	DIR_FORGE 			= new File(DIR_TEMP, "forge")
	public static final File 	DIR_FML 			= new File(DIR_FORGE, "fml")
	public static final File 	DIR_FORGE_PATCHES 	= new File(DIR_FORGE, "patches/minecraft")
	public static final File 	DIR_FML_PATCHES 	= new File(DIR_FML, "patches/minecraft")
	public static final File 	DIR_MAPPINGS 		= new File(DIR_FML, "conf")
	public static final File 	DIR_MCP_PATCHES 	= new File(DIR_MAPPINGS, "patches")

	// jar files
	public static final File	JAR_CLIENT			= new File(DIR_TEMP, "jars/Minecraft_Client.jar")
	public static final File	JAR_SERVER			= new File(DIR_TEMP, "jars/Minecraft_Server.jar")
	public static final File	JAR_MERGED			= new File(DIR_TEMP, "jars/Minecraft.jar")
	public static final File	JAR_DEOBF			= new File(DIR_TEMP, "Minecraft_SS.jar")
	public static final File	JAR_EXCEPTOR		= new File(DIR_TEMP, "Minecraft_EXC.jar")

	// download URLs    versions are in #_#_# form rather than #.#.#
	public static final String 	URL_MC_JAR 			= "http://assets.minecraft.net/%s/minecraft.jar"
	public static final String 	URL_MCSERVER_JAR 	= "http://assets.minecraft.net/%s/minecraft_server.jar"
	public static final String 	URL_LIB_ROOT 		= "http://s3.amazonaws.com/MinecraftDownload/"
	public static final String	URL_JSON_FORGE 		= "http://files.minecraftforge.net/minecraftforge/json"

	// normal MC version form
	public static final String 	URL_FORGE 			= "http://files.minecraftforge.net/minecraftforge/minecraftforge-src-%s-%s.zip"

	// lib names
	public static final LIBRARIES = [
		"lwjgl.jar",
		"lwjgl_util.jar",
		"jinput.jar"
	]

	// natives
	public static final NATIVES = [
		WINDOWS:"windows_natives.jar",
		MAC: "macosx_natives.jar",
		LINUX: "linux_natives.jar"
	]

	// CSVs
	public static final CSVS = [
		methods:"methods.csv",
		fields: "fields.csv",
		params: "params.csv",
		packages: "packages.csv"
	]

	static enum OperatingSystem
	{
		WINDOWS, MAC, LINUX
	}
}
