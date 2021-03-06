package com.github.abrarsyed.gmcp

public final class Constants
{
    // temp dirs
    public static final String DIR_LOGS = "logs"
    public static final String DIR_RUN = "run"
    public static final String DIR_SRC_FORGE = "Forge"
    public static final String DIR_SRC_FML = "FML"
    public static final String DIR_SRC_MINECRAFT = "Minecraft"
    public static final String DIR_SRC_RESOURCES = "resources"
    public static final String DIR_FORGE = "forge"
    public static final String DIR_FML = DIR_FORGE + "/fml"
    public static final String DIR_FORGE_PATCHES = DIR_FORGE + "/patches/minecraft"
    public static final String DIR_FML_PATCHES = DIR_FML + "/patches/minecraft"
    public static final String DIR_MAPPINGS = DIR_FML + "/conf"
    public static final String DIR_MCP_PATCHES = DIR_MAPPINGS + "/patches"

    // jars
    public static final String JAR_SRG = "processed.jar"
    public static final String JAR_DECOMP = "decomp.jar"
    public static final String DIR_NATIVES = "natives"

    // things in the cache dir.
    public static final String CACHE_DIR = "caches/minecraft";
    public static final String CACHE_ASSETS = "caches/minecraft/assets"
    public static final String FMED_JAR_CLIENT_FRESH = CACHE_DIR + '/net/minecraft/minecraft/%1$s/minecraft-%1$s.jar'
    public static final String FMED_JAR_SERVER_FRESH = CACHE_DIR + '/net/minecraft/minecraft_server/%1$s/minecraft_server-%1$s.jar'
    public static final String FMED_JAR_MERGED = CACHE_DIR + '/net/minecraft/minecraft_merged/%1$s/minecraft_merged-%1$s.jar'
    public static final String FMED_PACKAGED_SRG = CACHE_DIR + '/net/minecraft/minecraft_srg/%1$s/packaged-%1$s.srg'
    public static final String FMED_INH_MAP = CACHE_DIR + '/net/minecraft/minecraft_srg/%1$s/inheritanceMap-%1$s.dat'
    public static final String FMED_OBF_MCP_SRG = CACHE_DIR + '/net/minecraft/minecraft_srg/%1$s/reobf-mcp-%1$s.srg'
    public static final String FMED_OBF_SRG_SRG = CACHE_DIR + '/net/minecraft/minecraft_srg/%1$s/reobf-srg-%1$s.srg'
    public static final String FMED_PACKAGED_EXC = CACHE_DIR + '/net/minecraft/minecraft_srg/%1$s/packaged-%1$s.exc'
    public static final String FMED_PACKAGED_PATCH = CACHE_DIR + '/net/minecraft/minecraft_srg/%1$s/packaged-%1$s.patch'
    public static final String FERNFLOWER = CACHE_DIR + '/fernflower.jar';
    public static final String EXCEPTOR = CACHE_DIR + '/exceptor.jar';

    // download URLs
    public static final String URL_JSON_FORGE = "http://files.minecraftforge.net/minecraftforge/json"
    public static final String URL_JSON_FORGE2 = "http://files.minecraftforge.net/minecraftforge/json2"
    public static final String URL_MC_CLIENT = 'http://s3.amazonaws.com/Minecraft.Download/versions/%1$s/%1$s.jar'
    public static final String URL_MC_SERVER = 'http://s3.amazonaws.com/Minecraft.Download/versions/%1$s/minecraft_server.%1$s.jar'
    public static final String URL_ASSETS = 'http://s3.amazonaws.com/Minecraft.Resources'
    public static final String URL_MCP_JARS = "http://mcp.ocean-labs.de/files/archive/mcp804.zip";

    // cache file paths
    public static final String CACHE_DIR_FORGE = "caches/gmcp/forge"
    public static final String CACHE_JSON_FORGE = "caches/gmcp/forge.json"
    public static final String CACHE_JSON_FORGE2 = "caches/gmcp/forge2.json"

    // util
    public static final String NEWLINE = System.getProperty("line.separator")

    // CSVs
    public static final CSVS = [
            methods: "methods.csv",
            fields: "fields.csv",
            params: "params.csv",
            packages: "packages.csv"
    ]

    static enum OperatingSystem
    {
        WINDOWS, OSX, LINUX
    }
}
