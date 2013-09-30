package com.github.abrarsyed.gmcp


public final class Constants
{
    // temp dirs
    public static final String  	DIR_LOGS			    = "logs"
    public static final String      DIR_MISC                = "misc"
    public static final String      DIR_SRC_FORGE           = "Forge"
    public static final String      DIR_SRC_FML             = "FML"
    public static final String  	DIR_SRC_MINECRAFT	    = "Minecraft"
    public static final String      DIR_SRC_RESOURCES       = "resources"
    public static final String  	DIR_FORGE 			    = "forge"
    public static final String  	DIR_FML 			    = DIR_FORGE + "/fml"
    public static final String  	DIR_FORGE_PATCHES     	= DIR_FORGE + "/patches/minecraft"
    public static final String  	DIR_FML_PATCHES 	    = DIR_FML + "/patches/minecraft"
    public static final String  	DIR_MAPPINGS 		    = DIR_FML + "/conf"
    public static final String  	DIR_MCP_PATCHES 	    = DIR_MAPPINGS + "/patches"

    // jars
    public static final String  	JAR_PROC		    	= "build/jars/processed.jar"

    public static final String      DIR_NATIVES             = "build/natives"

    // stuff in the jars folder
    public static final String  	DIR_JAR_BIN 	        = "bin"
    public static final String      DIR_JAR_ASSETS          = "assets"

    // things in the cache dir.
    public static final String CACHE_DIR            = "caches/minecraft";
    public static final String CACHE_ASSETS                = "assets"
    public static final String FMED_JAR_CLIENT_FRESH     = CACHE_DIR + '/net/minecraft/minecraft/%1$s/minecraft-%1$s.jar'
    public static final String FMED_JAR_SERVER_FRESH     = CACHE_DIR + '/net/minecraft/minecraft_server/%1$s/minecraft_server-%1$s.jar'
    public static final String FMED_JAR_MERGED           = CACHE_DIR + '/net/minecraft/minecraft_merged/%1$s/minecraft_merged-%1$s.jar'
    public static final String FMED_PACKAGED_SRG         = CACHE_DIR + '/net/minecraft/minecraft_srg/%1$s/packaged-%1$s.srg'
    public static final String FMED_PACKAGED_EXC         = CACHE_DIR + '/net/minecraft/minecraft_srg/%1$s/packaged-%1$s.exc'
    public static final String FMED_PACKAGED_PATCH       = CACHE_DIR + '/net/minecraft/minecraft_srg/%1$s/packaged-%1$s.patch'
    public static final String FERNFLOWER           = "caches/fernflower.jar";
    public static final String EXCEPTOR             = "caches/exceptor.jar";

    // misc and executeables
    public static final String  EXEC_WIN_PATCH      = DIR_MISC + "/applydiff.exe"
    public static final String  CFG_FORMAT          = DIR_MISC + "/formatter.cfg"
    public static final String  EXEC_ASTYLE         = DIR_MISC + "/astyle"

    // download URLs
    public static final String	URL_JSON_FORGE 		= "http://files.minecraftforge.net/minecraftforge/json"
    public static final String  URL_JSON_FORGE2     = "http://files.minecraftforge.net/minecraftforge/json2"
    public static final String  URL_MC_CLIENT       = 'http://s3.amazonaws.com/Minecraft.Download/versions/%1$s/%1$s.jar'
    public static final String  URL_MC_SERVER       = 'http://s3.amazonaws.com/Minecraft.Download/versions/%1$s/minecraft_server.%1$s.jar'
    public static final String  URL_ASSETS          = 'http://s3.amazonaws.com/Minecraft.Resources'
    public static final String  URL_FERNFLOWER      = "https://github.com/AbrarSyed/FML/raw/working/mcplibs/fernflower.jar";
    public static final String  URL_EXCEPTOR        = "https://github.com/AbrarSyed/FML/raw/working/mcplibs/mcinjector.jar";

    // cache file paths
    public static final String  CACHE_DIR_FORGE     = "caches/gmcp/forge"
    public static final String  CACHE_JSON_FORGE    = "caches/gmcp/forge.json"
    public static final String  CACHE_JSON_FORGE2   = "caches/gmcp/forge2.json"

    // in-jar resource paths
    public static final String  REC_FORMAT_CFG      = "misc/formatter.cfg"
    public static final String  REC_PATCH_EXEC      = "misc/applydiff.exe"
    public static final String  REC_ASTYLE_EXEC     = "misc/astyle/astyle_%s"

    // util
    public static final String  NEWLINE             = System.getProperty("line.separator")

    // CSVs
    public static final CSVS = [
        methods:"methods.csv",
        fields: "fields.csv",
        params: "params.csv",
        packages: "packages.csv"
    ]

    static enum OperatingSystem
    {
        WINDOWS, OSX, LINUX
    }
}
