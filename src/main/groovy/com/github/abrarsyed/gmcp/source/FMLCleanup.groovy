package com.github.abrarsyed.gmcp.source

import java.util.regex.Pattern

import com.github.abrarsyed.gmcp.Constants

class FMLCleanup
{
    def static final before = /(?m)((case|default).+(?:\r\n|\r|\n))(?:\r\n|\r|\n)/ // Fixes newline after case before case body
    def static final after = /(?m)(?:\r\n|\r|\n)((?:\r\n|\r|\n)[ \t]+(case|default))/ // Fixes newline after case body before new case

    public static String updateFile(String text)
    {
        text = text.replaceAll(before) { match, group, words->
            return group
        }

        text = text.replaceAll(after) { match, group, words->
            return group
        }

        text = renameClass(text)

        return text
    }

    // FML RENAMMING STUFF

    private static final Pattern METHOD_REG = ~/^ {4}(\w+\s+\S.*\(.*|static)$/
    private static final Pattern CATCH_REG = ~/catch \((.*)\)$/
    private static final Pattern NESTED_PERINTH = ~/\(.*\(/
    private static final Pattern METHOD_PARAMS = ~/\((.+)\)/
    private static final Pattern METHOD_DEC_END = ~/(}|\);|throws .+?;)$/
    private static final Pattern METHOD_END = ~/^ {4}\}$/
    private static final String  NEWLINE = Constants.NEWLINE

    private static final COMPARATOR = [ compare: {a,b->  a == b? 0: a.size()<b.size()? -1: 1 } ] as Comparator


    private static String renameClass(String text)
    {
        def lines = text.readLines()
        def output = ""

        def insideMethod = false
        def method = ""
        def methodVars = []
        def skip = false

        for (line in lines)
        {
            // if re.search(METHOD_REG, line) and not re.search('=', line) and not re.search(r'\(.*\(', line):
            if (METHOD_REG.matcher(line) && !line.contains("=") && !NESTED_PERINTH.matcher(line))
            {
                // if re.search(r'\(.+\)', line):
                def match = METHOD_PARAMS.matcher(line)
                if (match)
                {
                    // method_variables += [s.strip() for s in re.search(r'\((.+)\)', line).group(1).split(',')]
                    def group = match.group(1)
                    methodVars.addAll(group.split(',').collect {it.trim()})
                }

                method += line + NEWLINE
                // method += line

                // single line method ?
                skip = true

                // if not re.search(r'(}|\);|throws .+?;)$', line):
                if (!METHOD_DEC_END.matcher(line))
                    insideMethod = true
            }

            //elif re.search(r'^ {%s}}$' % indent, line):
            else if (METHOD_END.matcher(line))
            {
                //inside_method = False
                insideMethod = false
            }

            if (insideMethod)
            {
                if (skip)
                {
                    skip = false
                    continue
                }

                method += line + NEWLINE

                def m = CATCH_REG.matcher(line)

                if (m)
                {
                    methodVars += m.group(1)
                }
                else
                {
                    line.findAll(/(?i)[a-z_$][a-z0-9_\[\]]+ var\d+/) { match ->
                        if (!match.startsWith("return") && !match.startsWith("throw"))
                            methodVars += match
                    }
                }
            }
            else
            {

                if (method)
                {
                    def namer = new FMLCleanup()
                    def todo = [:]

                    //todo = map(lambda x: [x, namer.get_name(x.split(' ')[0], x)], method_variables)
                    methodVars.each {
                        todo[it] = namer.getName(it.split(" ")[0], it)
                    }

                    def replace = new TreeMap(COMPARATOR)

                    todo.each{ key, val ->
                        if (key.contains(" "))
                            replace[key.split(' ')[1]] = val
                    }

                    // closure changes the sort, to sort by the return value of the closure.
                    replace.reverseEach { key, val ->
                        // don't rename already renamed stuff
                        // only rename stuff with var##
                        if (key ==~ /var\d+/)
                            method = method.replace(key, val)
                    }

                    output += method

                    // clear methods
                    methodVars.clear()
                    method = ''
                }

                if (skip)
                {
                    skip = false
                    continue
                }

                output += line + NEWLINE
            }
        }

        return output
    }

    def last, remap

    private FMLCleanup()
    {
        last = [
            'byte':     [0, 0, ['b']],
            'char':     [0, 0, ['c']],
            'short':    [1, 0, ['short']],
            'int':      [0, 1, ['i', 'j', 'k', 'l']],
            'boolean':  [0, 1, ['flag']],
            'double':   [0, 0, ['d']],
            'float':    [0, 1, ['f']],
            'File':     [1, 1, ['file']],
            'String':   [0, 1, ['s']],
            'Class':    [0, 1, ['oclass']],
            'Long':     [0, 1, ['olong']],
            'Byte':     [0, 1, ['obyte']],
            'Short':    [0, 1, ['oshort']],
            'Boolean':  [0, 1, ['obool']],
            'Package':  [0, 1, ['opackage']]]

        remap = [
            'long': 'int',
        ]
    }

    private String getName(String type, String var)
    {

        def index = null

        if (last.containsKey(type))
            index = type
        else if (remap.containsKey(type))
            index = remap[type]

        if (!index && (type =~ /^[A-Z]/ || type =~ /(\[|\.\.\.)/))
        {
            type = type.replace('...', '[]')

            while(type.contains("[][]"))
                type = type.replaceAll(/\[\]\[\]/, "[]")

            def name = type.toLowerCase()
            def skip = 1

            if (type =~ /\[/)
            {
                skip = 1
                name = "a"+name
                name = name.replace('[', '').replace(']', '').replace('...', '')
            }

            last[type] = [0, skip, [name]]
            index = type
        }

        if (!index)
        {
            println "NO DATA FOR TYPE $type $var"
            return type
        }

        def id = last[index][0]
        def skip_zero = last[index][1]
        def data = last[index][2]
        last[index][0] += 1

        def amount = data.size()

        if (amount == 1)
        {
            //return data[0] + ('' if ((not id) and skip_zero) else ('%d' % id))
            return data[0] + ( !id && skip_zero ? '' : id)
        }
        else
        {
            def num = (int)id/(int)amount
            return data[id % amount] + ( !num && skip_zero ? '' : num)
        }
    }
}