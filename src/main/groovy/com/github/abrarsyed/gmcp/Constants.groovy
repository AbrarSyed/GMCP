package com.github.abrarsyed.gmcp


public final class Constants
{
    // root dirs
    def public static final     DIR_NATIVES         = "natives"

    // temp dirs
    def public static final 	DIR_LOGS			= "logs"
    def public static final 	DIR_SRC_SOURCES		= "sources"
    def public static final     DIR_SRC_RESOURCES   = "resources"
    def public static final  	DIR_FORGE 			= "forge"
    def public static final  	DIR_FML 			= DIR_FORGE + "/fml"
    def public static final  	DIR_FORGE_PATCHES 	= DIR_FORGE + "/patches/minecraft"
    def public static final  	DIR_FML_PATCHES 	= DIR_FML + "/patches/minecraft"
    def public static final  	DIR_MAPPINGS 		= "mappings"
    def public static final  	DIR_MCP_PATCHES 	= DIR_MAPPINGS + "/patches"

    // jars
    def public static final 	JAR_PROC			= "Minecraft_processed.jar"

    // stuff in the jars folder
    def public static final  	DIR_JAR_BIN 			= "bin"
    def public static final 	JAR_JAR_CLIENT			= DIR_JAR_BIN + "/minecraft.jar"
    def public static final 	JAR_JAR_SERVER			= "minecraft_server.jar"

    // download URLs    versions are in #_#_# form rather than #.#.#
    public static final String	URL_JSON_FORGE 		    = "http://files.minecraftforge.net/minecraftforge/json2"
    public static final String  URL_JSON_FORGE_CACHE    = "cache/forge.json"

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
