package com.github.abrarsyed.gmcp

import groovy.json.JsonSlurper

class Json16Reader
{
    private JsonSlurper slurper

    // other stuff
    def mainClass
    def libs = []
    def nativeLibs = []

    public Json16Reader(String version)
    {
        slurper = new JsonSlurper()
    }

    def static doesFileExist()
    {
        return Util.baseFile(Constants.DIR_FML, 'fml.json').exists();
    }

    def parseJson()
    {
        //def url = String.format(URL + JSON, version).toURL()
        //def reader = new BufferedReader(url.newReader())
        def json = slurper.parse(Util.baseFile(Constants.DIR_FML, 'fml.json').newReader())

        mainClass = json['mainClass']

        for (obj in json['libraries'])
        {
            String lib = obj['name']

            if (lib.contains('debug') || lib.contains('_fixed'))
            {
                continue
            }
            else if (obj['natives'] || obj['extract'])
            // native. will need extraction.
            {
                nativeLibs += lib + ':' + obj['natives'][GMCP.os.name().toLowerCase()]
            }
            else
            {
                // force it to a good version.. of argo
                if (lib.contains('argo') && lib.split(/\:/)[2].toFloat() <= 3.4)
                {
                    lib += 'net.sourceforge.argo:argo:3.4'
                }

                else
                // standard download.
                {
                    libs += lib
                }
            }

        }
    }
}
