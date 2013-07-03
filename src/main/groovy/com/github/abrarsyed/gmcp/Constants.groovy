package com.github.abrarsyed.gmcp


public final class Constants
{
	// root dirs
	def public static final     DIR_NATIVES         = "natives"

	// temp dirs
	def public static final 	DIR_LOGS			= "logs"
	def public static final 	DIR_EXTRACTED		= "extracted"
	def public static final 	DIR_CLASSES			= "classes"
	def public static final 	DIR_SOURCES			= "sources"
	def public static final  	DIR_MC_JARS 		= "jars"
	def public static final  	DIR_FORGE 			= "forge"
	def public static final  	DIR_FML 			= DIR_FORGE + "/fml"
	def public static final  	DIR_FORGE_PATCHES 	= DIR_FORGE + "/patches/minecraft"
	def public static final  	DIR_FML_PATCHES 	= DIR_FML + "/patches/minecraft"
	def public static final  	DIR_MAPPINGS 		= "mappings"
	def public static final  	DIR_MCP_PATCHES 	= DIR_MAPPINGS + "/patches"

	// jars
	def public static final 	JAR_CLIENT			= DIR_MC_JARS + "/Minecraft_Client.jar"
	def public static final 	JAR_SERVER			= DIR_MC_JARS + "/Minecraft_Server.jar"
	def public static final 	JAR_MERGED			= DIR_MC_JARS + "/Minecraft.jar"
	def public static final 	JAR_DEOBF			= "Minecraft_SS.jar"
	def public static final 	JAR_EXCEPTOR		= "Minecraft_EXC.jar"

	// download URLs    versions are in #_#_# form rather than #.#.#
	public static final String	URL_JSON_FORGE 		= "http://files.minecraftforge.net/minecraftforge/json"

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
