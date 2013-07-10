package com.github.abrarsyed.gmcp


public final class Constants
{
    // root dirs
    def public static final     DIR_NATIVES         = "natives"

    // temp dirs
    def public static final 	DIR_LOGS			= "logs"
    def public static final     DIR_EXECS           = "execs"
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
    def public static final  	DIR_JAR_BIN 		= "bin"
    def public static final 	JAR_JAR_CLIENT		= DIR_JAR_BIN + "/minecraft.jar"
    def public static final 	JAR_JAR_SERVER		= "minecraft_server.jar"
    
    // executeables...
    def public static final     EXEC_WIN_PATCH      = DIR_EXECS + "/applydiff.exe"

    // download URLs    versions are in #_#_# form rather than #.#.#
    public static final String	URL_JSON_FORGE 		= "http://files.minecraftforge.net/minecraftforge/json"
    public static final String  URL_JSON_FORGE2     = "http://files.minecraftforge.net/minecraftforge/json2"
    public static final String  URL_WINDOWS_PATCH   = "https://github.com/AbrarSyed/GMCP/raw/master/execs/applydiff.exe"
    
    // cache file paths
    public static final String  CACHE_JSON_FORGE    = "caches/gmcp/forge.json"
    public static final String  CACHE_JSON_FORGE2   = "caches/gmcp/forge2.json"

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
