package com.github.abrarsyed.gmcp.tasks

import au.com.bytecode.opencsv.CSVParser
import au.com.bytecode.opencsv.CSVReader
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.TaskAction

class GenReobfSrgTask extends CachedTask
{
    @InputFile
    def methodsCSV

    @InputFile
    def fieldsCSV

    @InputFile
    def inSrg

    @OutputFile
    @CachedTask.Cached
    def outMcpSrg

    @OutputFile
    @CachedTask.Cached
    def outObfSrg

    @TaskAction
    def createReobfSrg()
    {
        methodsCSV = project.file(methodsCSV)
        fieldsCSV = project.file(fieldsCSV)
        inSrg = project.file(inSrg)

        def reader = new CSVReader(methodsCSV.newReader(), CSVParser.DEFAULT_SEPARATOR, CSVParser.DEFAULT_QUOTE_CHARACTER, CSVParser.DEFAULT_ESCAPE_CHARACTER, 1, false)
        def methods = [:]
        reader.readAll().each {
            methods[it[0]] = it[1]
        }

        reader = new CSVReader(fieldsCSV.newReader(), CSVParser.DEFAULT_SEPARATOR, CSVParser.DEFAULT_QUOTE_CHARACTER, CSVParser.DEFAULT_ESCAPE_CHARACTER, 1, false)
        def fields = [:]
        reader.readAll().each {
            fields[it[0]] = it[1]
        }

        Map deobf = readSrg(inSrg, false)
        // MC -> srg names

        Map deobfReversed = readSrg(inSrg, true)
        // srg names -> MC

        // replace SRG names with MCP names
        def mcSrg = deobfReversed.collectEntries { type, Map info ->
            // replace with the checking map
            info = info.collectEntries { input, out ->

                if (type == 'FD')
                {
                    def split = MergeMappingsTask.rsplit(input, '/')
                    def name = split[1]

                    if (fields[name])
                    {
                        name = fields[name]
                    }

                    return [split[0] + '/' + name, out]
                }
                else if (type == 'MD')
                {
                    def split = input.split(' ')
                    def nameSplit = MergeMappingsTask.rsplit(split[0], '/')
                    def name = nameSplit[1]

                    if (methods[name])
                    {
                        name = methods[name]
                    }

                    return [
                            nameSplit[0] + '/' + name + ' ' + split[1],
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

        writeSrg(outMcpSrg, mcSrg)

        // replace the MC names with the SRG names.
        def reobfSrg = mcSrg.collectEntries { type, info ->
            info = info.collectEntries { input, out ->
                return [input, deobf[type][out]]
            }
            return [type, info]
        }

        writeSrg(outObfSrg, reobfSrg)
    }

    def private static readSrg(File input, boolean reverse)
    {
        def out = [PK: [:], CL: [:], MD: [:], FD: [:]]

        input.eachLine
                { line ->
                    def split = line.split(' ')

                    if (split[0] == 'MD:')
                    {
                        if (reverse)
                        {
                            out.MD.put split[3..4].join(' '), split[1..2].join(' ')
                        }
                        else
                        {
                            out.MD.put split[1..2].join(' '), split[3..4].join(' ')
                        }
                    }
                    else
                    {
                        if (reverse)
                        {
                            out[split[0][0..1]].put split[2], split[1]
                        }
                        else
                        {
                            out[split[0][0..1]].put split[1], split[2]
                        }
                    }
                }

        return out
    }

    def private static writeSrg(output, Map map)
    {
        def writer = output.newWriter()

        map.each { type, info ->
            info.each { input, out ->
                writer.writeLine(type + ": " + input + " " + out)
            }
        }

        writer.close()
    }

    public File getMethodsCSV()
    {
        if (methodsCSV instanceof File)
        {
            return (File) methodsCSV
        }
        else
        {
            methodsCSV = getProject().file(methodsCSV);
            return (File) methodsCSV;
        }
    }

    public File getFieldsCSV()
    {
        if (fieldsCSV instanceof File)
        {
            return (File) fieldsCSV
        }
        else
        {
            fieldsCSV = getProject().file(fieldsCSV);
            return (File) fieldsCSV;
        }
    }

    public File getInSrg()
    {
        if (inSrg instanceof File)
        {
            return (File) inSrg
        }
        else
        {
            inSrg = getProject().file(inSrg);
            return (File) inSrg;
        }
    }

    public File getOutObfSrg()
    {
        if (outObfSrg instanceof File)
        {
            return (File) outObfSrg
        }
        else
        {
            outObfSrg = getProject().file(outObfSrg);
            return (File) outObfSrg;
        }
    }

    public File getOutMcpSrg()
    {
        if (outMcpSrg instanceof File)
        {
            return (File) outMcpSrg
        }
        else
        {
            outMcpSrg = getProject().file(outMcpSrg);
            return (File) outMcpSrg;
        }
    }
}
