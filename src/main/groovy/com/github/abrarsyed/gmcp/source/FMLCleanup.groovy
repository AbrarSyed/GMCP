package com.github.abrarsyed.gmcp.source

import java.util.regex.Pattern

import com.github.abrarsyed.gmcp.Constants

class FMLCleanup
{
    def static final before = /(?m)((case|default).+(?:\r\n|\r|\n))(?:\r\n|\r|\n)/ // Fixes newline after case before case body
    def static final after = /(?m)(?:\r\n|\r|\n)((?:\r\n|\r|\n)[ \t]+(case|default))/ // Fixes newline after case body before new case

    private static final Pattern VAR_PLUS_PLUS = Pattern.compile("(\\w+) *\\+ \\+");
    private static final Pattern PLUS_PLUS_VAR = Pattern.compile("([^\\+])\\+ \\+ *(\\w+)");
    private static final Pattern VAR_MINUS_MINUS = Pattern.compile("(\\w+) *\\- \\-");
    private static final Pattern MINUS_MINUS_VAR = Pattern.compile("([^\\-])\\- \\- *(\\w+)");

    private static final Pattern GTHAN_GTHAN_GTHAN = Pattern.compile(" > > > "); //Need to happen first to avoid conflict with >>
    private static final Pattern GTHAN_GTHAN_EQUALS = Pattern.compile(" > > = "); //Need to happen first to avoid conflict with >> and >=

    private static final Pattern EQUALS_EQUALS = Pattern.compile(" = = ");
    private static final Pattern PLUS_EQUALS = Pattern.compile(" \\+ = ");
    private static final Pattern MINUS_EQUALS = Pattern.compile(" \\- = ");
    private static final Pattern AND_EQUALS = Pattern.compile(" & = ");
    private static final Pattern BANG_EQUALS = Pattern.compile(" ! = ");
    private static final Pattern AND_AND = Pattern.compile(" & & ");
    private static final Pattern OR_OR = Pattern.compile(" \\| \\| ");
    private static final Pattern PLUS_PLUS = Pattern.compile(" \\+ \\+ ");
    private static final Pattern MINUS_MINUS = Pattern.compile(" \\- \\- ");
    private static final Pattern GTHAN_GTHAN = Pattern.compile(" > > ");
    private static final Pattern LTHAN_LTHAN = Pattern.compile(" < < ");
    private static final Pattern PERCENT_EQUALS = Pattern.compile(" % = ");
    private static final Pattern GTHAN_EQUALS = Pattern.compile(" > = ");
    private static final Pattern LTHAN_EQUALS = Pattern.compile(" < = ");
    private static final Pattern SLACH_EQUALS = Pattern.compile(" / = ");
    private static final Pattern TIMES_EQUALS = Pattern.compile(" \\* = ");
    private static final Pattern POWER_EQUALS = Pattern.compile(" \\^ = ");
    private static final Pattern OR_EQUALS = Pattern.compile(" \\| = ");

    private static final Pattern TERRINARY_ONE = Pattern.compile(' *\\? *(?=(?:(?:(?:[^"\\\\]+|\\\\.)*+"){2})*+(?:[^\"\\\\]+|\\\\.)*+$)(?=\\S)');
    private static final Pattern TERRINARY_TWO = Pattern.compile(' *: *(?=(?:(?:(?:[^"\\\\]+|\\\\.)*+"){2})*+(?:[^\"\\\\]+|\\\\.)*+$)(?=\\S)');
    private static final Pattern WEIRDNESS = Pattern.compile("\\}\\) :");
    private static final Pattern CHARARRAYFIX = Pattern.compile("' *([?:]) *'");

    private static final Pattern SLACH_PARENTHESIS = Pattern.compile("/\\((?=(?:(?:[^\"]*+\"){2})*+[^\"]*+\\z)");
    private static final Pattern TIMES_PARENTHESIS = Pattern.compile("\\*\\((?=(?:(?:[^\"]*+\"){2})*+[^\"]*+\\z)");
    private static final Pattern POWER_PARENTHESIS = Pattern.compile("\\^\\((?=(?:(?:[^\"]*+\"){2})*+[^\"]*+\\z)");

    private static final Pattern RETURN_NOSPACE = Pattern.compile('return([^\\s;])(?=(?:(?:(?:[^"\\\\]+|\\\\.)*+"){2})*+(?:[^"\\\\]+|\\\\.)*+$)');

    private static final Pattern BEFORE = Pattern.compile("(?m)((case|default).+(?:\\r\\n|\\r|\\n))(?:\\r\\n|\\r|\\n)"); // Fixes newline after case before case body
    private static final Pattern AFTER = Pattern.compile("(?m)(?:\\r\\n|\\r|\\n)((?:\\r\\n|\\r|\\n)[ \\t]+(case|default))"); // Fixes newline after case body before new case

    public static String updateFile(String text)
    {
        text = VAR_PLUS_PLUS.matcher(text).replaceAll('$1++');
        text = PLUS_PLUS_VAR.matcher(text).replaceAll('$1++$2');
        text = VAR_MINUS_MINUS.matcher(text).replaceAll('$1--');
        text = MINUS_MINUS_VAR.matcher(text).replaceAll('$1--$2');

        text = GTHAN_GTHAN_GTHAN.matcher(text).replaceAll(" >>> ");
        text = GTHAN_GTHAN_EQUALS.matcher(text).replaceAll(" >>= ");

        text = EQUALS_EQUALS.matcher(text).replaceAll(" == ");
        text = PLUS_EQUALS.matcher(text).replaceAll(" += ");
        text = MINUS_EQUALS.matcher(text).replaceAll(" -= ");
        text = AND_EQUALS.matcher(text).replaceAll(" &= ");
        text = BANG_EQUALS.matcher(text).replaceAll(" != ");
        text = AND_AND.matcher(text).replaceAll(" && ");
        text = OR_OR.matcher(text).replaceAll(" || ");
        text = PLUS_PLUS.matcher(text).replaceAll(" ++ ");
        text = MINUS_MINUS.matcher(text).replaceAll(" -- ");
        text = GTHAN_GTHAN.matcher(text).replaceAll(" >> ");
        text = LTHAN_LTHAN.matcher(text).replaceAll(" << ");
        text = PERCENT_EQUALS.matcher(text).replaceAll(" %= ");
        text = GTHAN_EQUALS.matcher(text).replaceAll(" >= ");
        text = LTHAN_EQUALS.matcher(text).replaceAll(" <= ");
        text = SLACH_EQUALS.matcher(text).replaceAll(" /= ");
        text = TIMES_EQUALS.matcher(text).replaceAll(" *= ");
        text = POWER_EQUALS.matcher(text).replaceAll(" ^= ");
        text = OR_EQUALS.matcher(text).replaceAll(" |= ");

        text = SLACH_PARENTHESIS.matcher(text).replaceAll("/ (");
        text = TIMES_PARENTHESIS.matcher(text).replaceAll("* (");
        text = POWER_PARENTHESIS.matcher(text).replaceAll("^ (");

        text = TERRINARY_ONE.matcher(text).replaceAll(" ? ");
        text = TERRINARY_TWO.matcher(text).replaceAll(" : ");
        text = CHARARRAYFIX.matcher(text).replaceAll('\'$1\'');
        text = WEIRDNESS.matcher(text).replaceAll("}):");

        text = RETURN_NOSPACE.matcher(text).replaceAll('return $1');

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