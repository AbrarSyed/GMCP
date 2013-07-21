package com.github.abrarsyed.gmcp

import au.com.bytecode.opencsv.CSVParser
import au.com.bytecode.opencsv.CSVReader

class SRGCreator
{
    def static createReobfSrg()
    {
        def reader = new CSVReader(Util.baseFile(Constants.DIR_MAPPINGS, Constants.CSVS['methods']).newReader(), CSVParser.DEFAULT_SEPARATOR, CSVParser.DEFAULT_QUOTE_CHARACTER, CSVParser.DEFAULT_ESCAPE_CHARACTER, 1, false)
        def methods = [:]
        reader.readAll().each
        {
            methods[it[0]] = it[1]
        }

        reader = new CSVReader(Util.baseFile(Constants.DIR_MAPPINGS, Constants.CSVS['fields']).newReader(), CSVParser.DEFAULT_SEPARATOR, CSVParser.DEFAULT_QUOTE_CHARACTER, CSVParser.DEFAULT_ESCAPE_CHARACTER, 1, false)
        def fields = [:]
        reader.readAll().each
        {
            fields[it[0]] = it[1]
        }

        Map deobf = readSrg(Util.baseFile(Constants.DIR_MAPPINGS, "packaged.srg"), false)
        // MC -> srg names

        Map deobfReversed = readSrg(Util.baseFile(Constants.DIR_MAPPINGS, "packaged.srg"), true)
        // srg names -> MC

        // replace SRG names with MCP names
        def mcSrg = deobfReversed.collectEntries
        { type, Map info ->
            // replace with the checking map
            info = info.collectEntries
            { input, out ->

                if (type == 'FD')
                {
                    def split = PackageFixer.rsplit(input, '/')
                    def name = split[1]

                    if (fields[name])
                        name = fields[name]

                    return [split[0]+'/'+name, out]
                }
                else if (type == 'MD')
                {
                    def split = input.split(' ')
                    def nameSplit = PackageFixer.rsplit(split[0], '/')
                    def name = nameSplit[1]

                    if (methods[name])
                        name = methods[name]

                    return [
                        nameSplit[0]+'/'+name+' '+split[1],
                        out
                    ]
                }
                else
                {
                    return [input, out]
                }
            }

            return [type, info]
        }

        // mcSrg == mcpNames -> mc

        writeSrg(Util.baseFile(Constants.DIR_MAPPINGS, "reobf_mcp.srg"), mcSrg)

        // replace the MC names with the SRG names.
        def reobfSrg = mcSrg.collectEntries { type, info ->
            info = info.collectEntries { input, out ->
                return [input, deobf[type][out]]
            }
            return [type, info]
        }

        writeSrg(Util.baseFile(Constants.DIR_MAPPINGS, "reobf_srg.srg"), reobfSrg)
    }

    def private static readSrg(File input, boolean reverse)
    {
        def out = [PK:[:], CL:[:], MD:[:], FD:[:]]

        input.eachLine
        { line ->
            def split = line.split(' ')

            if (split[0] == 'MD:')
            {
                if (reverse)
                    out.MD.put split[3..4].join(' '), split[1..2].join(' ')
                else
                    out.MD.put split[1..2].join(' '), split[3..4].join(' ')
            }
            else
            {
                if (reverse)
                    out[split[0][0..1]].put split[2], split[1]
                else
                    out[split[0][0..1]].put split[1], split[2]
            }
        }

        return out
    }

    def private static writeSrg(File output, Map map)
    {
        def writer = output.newWriter()

        map.each { type, info ->
            info.each { input, out ->
                writer.writeLine(type+": "+input+" "+out)
            }
        }

        writer.close()
    }
}