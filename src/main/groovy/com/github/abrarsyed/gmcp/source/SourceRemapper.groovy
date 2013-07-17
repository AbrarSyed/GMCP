package com.github.abrarsyed.gmcp.source

import java.util.regex.Matcher

import au.com.bytecode.opencsv.CSVParser
import au.com.bytecode.opencsv.CSVReader

import com.github.abrarsyed.gmcp.Constants


class SourceRemapper
{
    def Map<String, Map<String, String>> methods
    def Map<String, Map<String, String>> fields
    def Map<String, String> params

    final String METHOD_SMALL = /func_[0-9]+_[a-zA-Z_]+/
    final String FIELD_SMALL = /field_[0-9]+_[a-zA-Z_]+/
    final String PARAM = /p_[\w]+_\d+_/
    final String METHOD = /^( {4}|\t)(?:[\w$.\[\]]+ )*(func_[0-9]+_[a-zA-Z_]+)\(/
    final String FIELD = /^( {4}|\t)(?:[\w$.\[\]]+ )*(field_[0-9]+_[a-zA-Z_]+) *(?:=|;)/

    SourceRemapper(Map files)
    {
        def reader = getReader(files['methods'])
        methods = [:]
        reader.readAll().each
        {
            methods[it[0]] = [name: it[1], javadoc: it[3]]
        }


        reader = getReader(files['fields'])
        fields = [:]
        reader.readAll().each
        {
            fields[it[0]] = [name: it[1], javadoc: it[3]]
        }


        reader = getReader(files['params'])
        params = [:]
        reader.readAll().each
        {
            params[it[0]] = it[1]
        }
    }

    private CSVReader getReader(File file)
    {
        return new CSVReader(file.newReader(), CSVParser.DEFAULT_SEPARATOR, CSVParser.DEFAULT_QUOTE_CHARACTER, CSVParser.DEFAULT_ESCAPE_CHARACTER, 1, false)
    }

    private String buildJavadoc(String indent, String javadoc, boolean isMethod)
    {
        StringBuilder builder = new StringBuilder()

        if (javadoc.length() >= 70 || isMethod)
        {
            def list = wrapText(javadoc, 120-(indent.length() + 3))

            builder.with
            {
                append indent
                append "/**"
                append Constants.NEWLINE

                for (line in list)
                {
                    append indent
                    append " * "
                    append line
                    append Constants.NEWLINE
                }

                //append WordUtils.wrap(fakeIndent+" * "+javadoc, 120, Constants.NEWLINE+fakeIndent+" * ", true)
                append indent
                append " */"
                append Constants.NEWLINE
            }
        }
        // one line
        else
        {
            builder.with {
                append indent
                append "/** "
                append javadoc
                append " */"
                append Constants.NEWLINE
            }
        }

        return builder.toString().replace(indent, indent)
    }

    static List wrapText (String text, int len)
    {
        // return empty array for null text
        if (text == null)
            return []

        // return text if len is zero or less
        if (len <= 0)
            return [text]

        // return text if less than length
        if (text.length() <= len)
            return [text]

        def lines = []
        StringBuilder line = new StringBuilder()
        StringBuilder word = new StringBuilder()
        def tempNum

        // each char in array
        for (character in text.toCharArray())
        {
            // its a wordBreaking character.
            if (character == ' ' as char || character == ',' as char || character == '-' as char )
            {
                // add the character to the word
                word.append(character)

                // its a space. set TempNum to 1, otherwise leave it as a wrappable char
                tempNum = Character.isWhitespace(character) ? 1 : 0

                // subtract tempNum from the length of the word
                if ((line.length() + word.length() - tempNum) > len)
                {
                    lines.add(line.toString())
                    line.delete(0, line.length())
                }

                // new word, add it to the next line and clear the word
                line.append(word)
                word.delete(0, word.length())

            }
            // not a linebreak char
            else
            {
                // add it to the word and move on
                word.append(character)
            }
        }

        // handle any extra chars in current word
        if (word.length() > 0)
        {
            if ((line.length() + word.length()) > len)
            {
                lines.add(line.toString())
                line.delete(0, line.length())
            }
            line.append(word)
        }

        // handle extra line
        if (line.length() > 0)
        {
            lines.add(line.toString())
        }

        lines = lines.collect { it.trim() }

        return lines
    }

    def remapFile(File file)
    {
        String text = file.text
        Matcher matcher

        def prevLine = null
        def lines = text.readLines().collect {String line ->

            // check method
            matcher = line =~ METHOD

            if (matcher.find())
            {
                def name = matcher.group(2)

                if (methods[name] && methods[name]['name'])
                {
                    line = line.replace(name, methods[name]['name'])

                    // get javadoc
                    if (methods[name]['javadoc'])
                    {
                        line = buildJavadoc(matcher.group(1) , methods[name]['javadoc'], true)+line

                        if (prevLine && !prevLine.endsWith('{'))
                            line = Constants.NEWLINE + line
                    }
                }
            }

            // check field
            matcher = line =~ FIELD

            if (matcher.find())
            {
                def name = matcher.group(2)

                if (fields[name])
                {
                    line = line.replace(name, fields[name]['name'])

                    // get javadoc
                    if (fields[name]['javadoc'])
                    {
                        line = buildJavadoc(matcher.group(1) , fields[name]['javadoc'], false)+line

                        if (prevLine && !prevLine.endsWith('{'))
                            line = Constants.NEWLINE + line
                    }
                }
            }

            prevLine = line
            return line
        }

        text = lines.join(Constants.NEWLINE) + Constants.NEWLINE

        // FAR all parameters
        text = text.replaceAll(PARAM) { match ->
            params[match] ?: match
        }

        // FAR all methods
        text = text.replaceAll(METHOD_SMALL) { match ->
            methods[match] ? methods[match]['name'] : match
        }

        // FAR all fields
        text = text.replaceAll(FIELD_SMALL) { match ->
            fields[match] ? fields[match]['name'] : match
        }

        // write file
        file.write(text)
    }
}