package com.github.abrarsyed.gmcp

import groovy.json.JsonSlurper

class Json16Reader
{
    private static final String URL = 'http://s3.amazonaws.com/Minecraft.Download/versions/%1$s'
    private static final String JSON = '/%1$s.json'
    private static final String CLIENT = '/%1$s.jar'
    private static final String SERVER = '/minecraft_server.%1$s.jar'

    private String version
    private JsonSlurper slurper

    // other stuff
    def mainClass
    def libs = []
    def nativeLibs = []

    public Json16reader(String version)
    {
        this.version = version
        slurper = new JsonSlurper()
    }

    def parseJson()
    {
        def url = String.format(URL + JSON, version).toURL()
        def reader = new BufferedReader(url.newReader())
        def json = slurper.parse(reader)

        mainClass = json['mainClass']

        for (obj in json['libraries'])
        {
            if (obj['name'].contains('debug'))
                continue;
            else if (obj['natives'] || obj['extract'])
            // native. will need extraction.
                nativeLibs += obj['name'] + ':' + obj['natives'][GMCP.os.name()]
            else
            // standard download
                libs += obj['name']
        }
    }

    def getClientURL()
    {
        return String.format(URL + CLIENT, version)
    }

    def getServerURL()
    {
        return String.format(URL + SERVER, version)
    }
}
