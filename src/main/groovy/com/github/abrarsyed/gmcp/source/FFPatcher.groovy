package com.github.abrarsyed.gmcp.source

import com.google.code.regexp.Matcher
import com.google.code.regexp.Pattern
import com.google.common.base.Strings

class FFPatcher
{
    static final MODIFIERS = /public|protected|private|static|abstract|final|native|synchronized|transient|volatile|strict/
    static final Map<String, String> REG = [

        // Remove trailing whitespace
        trailing : /(?m)[ \t]+$/,

        //Remove repeated blank lines
        newlines: /(?m)^(\r\n|\r|\n){2,}/,

        modifiers: /(/ + MODIFIERS + /) /,
        list : /, /,

        // modifiers, type, name, implements, body, end
        enum_class: /(?m)^((?:(?:/ + MODIFIERS + /) )*)(enum) ([\w$]+)(?: implements ([\w$.]+(?:, [\w$.]+)*))? \{(?:\r\n|\r|\n)((?:.*(?:\r\n|\n|\r))*?)(\})/,

        // name, body, end
        enum_entries: /(?m)^ +([\w$]+)\("(?:[\w$]+)", [0-9]+(?:, (.*?))?\)((?:;|,)(?:\r\n|\n|\r)+)/,

        empty_super: /(?m)^ +super\(\);(\r\n|\n|\r)/,

        // strip trailing 0 from doubles and floats to fix decompile differences on OSX
        // 0.0010D => 0.001D
        // value, type
        trailingzero: /([0-9]+\.[0-9]*[1-9])0+([DdFfEe])/,
    ]

    static final Map<String, String> REG_FORMAT = [
        // modifiers, params, throws, empty, body, end
        constructor : /(?m)^ +(?<modifiers>(?:(?:/ + MODIFIERS + /) )*)%s\((?<parameters>.*?)\)(?: throws (?<throws>[\w$.]+(?:, [\w$.]+)*))? \{(?:(?<empty>\}(?:\r\n|\r|\n)+)|(?:(?<body>(?:\r\n|\r|\n)(?:.*?(?:\r\n|\r|\n))*?)(?<end> {3}\}(?:\r\n|\r|\n)+)))/,

        enumVals: /(?m)^ +\/\/ [$]FF: synthetic field(\r\n|\n|\r) +private static final %s\[\] [\w\$]+ = new %s\[\]\{.*?\};(\r\n|\n|\r)/,
    ]

    def static processDir(File dir)
    {
        dir.eachFile {
            if (it.isDirectory())
                processDir(it)
            else if (it.getPath().endsWith(".java"))
                processFile(it)
        }
    }

    def static processFile(File file)
    {
        def classname = file.getName().split(/\./)[0]
        def text = file.text


        text = text.replaceAll(REG["trailing"], "")

        text = text.replaceAll(REG["enum_class"])
                // modifiers, type, name, implements, body, end
        { match, modifiers, type, name, inters, body, end->

            if (classname != name)
            {
                throw new RuntimeException("ERROR PARSING ENUM !!!!! Class Name != File Name")
            }

            def mods = modifiers.findAll(REG['modifiers'])
            if (modifiers && !mods)
            {
                throw new RuntimeException("ERROR PARSING ENUM !!!!! no modifiers!")
            }

            def interfaces = []
            if (inters)
            {
                interfaces = inters.split(REG['list'])
            }

            return processEnum(classname, type, mods, interfaces, body, end)
        }

        text = text.replaceAll(REG["empty_super"], "")
        text = text.replaceAll(REG["trailingzero"], "")
        text = text.replaceAll(REG["newlines"], "\n")

        text = text.replaceAll(/(\r\n|\r|\n)/, "\n")
        text = text.replaceAll("(\r\n|\r|\n)", "\n")

        file.write(text)
    }

    def static processEnum(classname, classtype, List modifiers, List interfaces, String body, end)
    {

        body = body.replaceAll(REG["enum_entries"])
        { list ->
            // 0 = all
            // 1 = name
            // 2 = matchBody
            // 3 = end

            // defaults, if matchBody isnt there
            def entryBody = ''

            // if the body IS there
            if (list[2])
            {
                entryBody = list[2]
            }

            return '   ' + list[1] + "(" + entryBody + ")" + list[3]
        }

        def valuesRegex = String.format(REG_FORMAT['enumVals'], classname, classname)
        body = body.replaceAll(valuesRegex, "")

        //        constructor_regex = re.compile(_REGEXP_STR['constructor'] % re.escape(class_name), re.MULTILINE)
        //        body = constructor_regex.sub(constructor_match, body)
        def conRegex = String.format(REG_FORMAT['constructor'], classname)
        Matcher match = Pattern.compile(conRegex).matcher(body)
        // process constructors
        while (match.find())
        {
            // check modifiers
            def mods = match.group('modifiers').findAll(REG['modifiers'])
            if (match.group('modifiers') &&  mods.isEmpty())
            {
                throw new RuntimeException("ERROR PARSING ENUM CONSTRUCTOR! !!!!! no modifiers!")
            }

            def params = []
            if (match.group('parameters'))
            {
                params = match.group('parameters').split(REG['list']) as List
            }

            def exc = []
            if (match.group('throws'))
            {
                exc = match.group('throws').split(REG['list'])
            }

            def methodBody, methodEnd
            if (!Strings.isNullOrEmpty(match.group('empty')))
            {
                methodBody = ''
                methodEnd = match.group('empty')
            }
            else
            {
                methodBody = match.group('body')
                methodEnd = match.group('end')
            }

            body = body.replace(match.group(), processConstructor(classname, mods, params, exc, methodBody, methodEnd))
        }

        // rebuild enum
        def out = ''

        if (!modifiers.isEmpty())
        {
            out += modifiers.join(' ')
        }

        out += classtype + ' ' + classname

        if (!interfaces.isEmpty())
        {
            out += ' implements '+interfaces.join(', ')
        }

        out += "{ \n" + body + end

        return out
    }

    def static processConstructor(classname, List<String> mods, List<String> params, List<String> exc, methodBody, methodEnd)
    {
        if (params.size() >= 2)
        {
            // special case?
            if (params[0].startsWith('String ') && params[1].startsWith('int '))
            {
                params = params.subList(2, params.size())

                // empty constructor
                if (Strings.isNullOrEmpty(methodBody) && params.isEmpty())
                {
                    return ''
                }
            }
            else
            {
                throw new RuntimeException("invalid initial parameters in enum")
            }
            // ERROR
        }
        else
        {
            throw new RuntimeException("not enough parameters in enum")
        }

        // rebuild constructor

        def out = '   '
        if (mods)
        {
            out += mods.join(' ')
        }

        out += classname + "(${params.join(', ')})"

        if (exc)
        {
            out += " throws ${exc.join(', ')}"
        }

        out += " {" + methodBody + methodEnd

        return out
    }
}