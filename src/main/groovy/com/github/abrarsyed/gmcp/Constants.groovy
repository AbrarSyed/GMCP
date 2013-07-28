package com.github.abrarsyed.gmcp


public final class Constants
{
    // temp dirs
    static final String 	DIR_LOGS			    = "logs"
    static final String     DIR_MISC                = "misc"
    static final String     DIR_SRC_FORGE           = "Forge"
    static final String     DIR_SRC_FML             = "FML"
    static final String 	DIR_SRC_MINECRAFT	    = "Minecraft"
    static final String     DIR_SRC_RESOURCES       = "resources"
    static final String  	DIR_FORGE 			    = "forge"
    static final String  	DIR_FML 			    = DIR_FORGE + "/fml"
    static final String  	DIR_FORGE_PATCHES     	= DIR_FORGE + "/patches/minecraft"
    static final String  	DIR_FML_PATCHES 	    = DIR_FML + "/patches/minecraft"
    static final String  	DIR_MAPPINGS 		    = "mappings"
    static final String  	DIR_MCP_PATCHES 	    = DIR_MAPPINGS + "/patches"

    // jars
    static final String 	JAR_PROC		    	= "Minecraft_processed.jar"

    // stuff in the jars folder
    static final String  	DIR_JAR_BIN 	    	= "bin"
    static final String 	JAR_JAR_CLIENT	    	= DIR_JAR_BIN + "/minecraft.jar"
    static final String     JAR_JAR_CLIENT_BAK      = DIR_JAR_BIN + "/minecraft.jar.bak"
    static final String 	JAR_JAR_SERVER	 	    = "minecraft_server.jar"

    // misc and executeables
    static final String         EXEC_WIN_PATCH      = DIR_MISC + "/applydiff.exe"
    public static final String  CFG_FORMAT          = DIR_MISC + "/formatter.cfg"
    public static final String  EXEC_ASTYLE         = DIR_MISC + "/astyle"

    // download URLs
    public static final String	URL_JSON_FORGE 		= "http://files.minecraftforge.net/minecraftforge/json"
    public static final String  URL_JSON_FORGE2     = "http://files.minecraftforge.net/minecraftforge/json2"
    public static final String  URL_JSON_FML        = 'https://raw.github.com/MinecraftForge/FML/master/jsons/%1$s-dev.json'
    public static final String  URL_MC16_CLIENT     = 'http://s3.amazonaws.com/Minecraft.Download/versions/%1$s/%1$s.jar'
    public static final String  URL_MC16_SERVER     = 'http://s3.amazonaws.com/Minecraft.Download/versions/%1$s/minecraft_server.%1$s.jar'

    // cache file paths
    public static final String  CACHE_JSON_FORGE    = "caches/gmcp/forge.json"
    public static final String  CACHE_JSON_FORGE2   = "caches/gmcp/forge2.json"

    // in-jar resource paths
    public static final String  REC_FORMAT_CFG      = "misc/formatter.cfg"
    public static final String  REC_PATCH_EXEC      = "misc/applydiff.exe"
    public static final String  REC_ASTYLE_EXEC     = "misc/astyle/astyle_%s"

    // random, 1.5.2-minus dependancies
    public static final List<String> DEP_152_MINUS = [
        'net.sourceforge.argo:argo:3.4',
        'org.ow2.asm:asm-all:4.0',
        'org.bouncycastle:bcprov-ext-jdk15on:1.48',
        'com.google.guava:guava:14.0',
        'org.scala-lang:scala-library:2.10.0'
    ] as List

    // util
    public static final String NEWLINE = System.getProperty("line.separator")

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
