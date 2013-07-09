package com.github.abrarsyed.gmcp.source

import au.com.bytecode.opencsv.CSVParser
import au.com.bytecode.opencsv.CSVReader

class SourceRemapper
{
	def Map methods
	def Map fields
	def Map params

	final String METHOD_SMALL = /func_[0-9]+_[a-zA-Z_]+/
	final String FIELD_SMALL = /field_[0-9]+_[a-zA-Z_]+/
	final String PARAM = /p_[\w]+_\d+_/
	final String METHOD = /(?m)^((?: |\t)*)(?:\w+ )*(/+METHOD_SMALL+/)\(/  // captures indent and name
	final String FIELD = /(?m)^((?: |\t)*)(?:\w+ )*(/+FIELD_SMALL+/) *(?:=|;)/ // capures indent and name

	SourceRemapper(files)
	{
		def reader = getReader(files["methods"])
		methods = [:]
		reader.readAll().each
		{
			methods[it[0]] = [name:it[1], javadoc:it[3]]
		}

		reader = getReader(files["fields"])
		fields = [:]
		reader.readAll().each
		{
			fields[it[0]] = [name:it[1], javadoc:it[3]]
		}

		reader = getReader(files["params"])
		params = [:]
		reader.readAll().each
		{
			params[it[0]] = it[1]
		}
	}

	private CSVReader getReader(File file)
	{
		return new CSVReader(new FileReader(file), CSVParser.DEFAULT_SEPARATOR, CSVParser.DEFAULT_QUOTE_CHARACTER, CSVParser.DEFAULT_ESCAPE_CHARACTER, 1, false)
	}

	private buildJavadoc(indent, javadoc)
	{
		def out = indent+"/**\n"
		out += indent+" * "+javadoc+"\n"
		out += indent+" */\n"
	}

	def remapFile(File file)
	{
		def text = file.text
		def newline, matcher

		// search methods to javadoc
		text.findAll(METHOD) { match, indent, name ->

			if (methods[name])
			{
				// rename
				newline = match.replaceAll(name, methods[name]['name'])

				// get javadoc
				if (methods[name]['javadoc'])
				{
					newline = buildJavadoc(indent, methods[name]['javadoc'])+newline
				}

				// replace method in-file
				text = text.replace(match, newline)
			}
		}

		// search for fields to javadoc
		text.findAll(FIELD) { match, indent, name ->

			if (fields[name])
			{
				// rename
				newline = match.replaceAll(name, fields[name]['name'])

				// get javadoc
				if (fields[name]['javadoc'])
				{
					newline = buildJavadoc(indent, fields[name]['javadoc'])+newline
				}

				// replace method in-file
				text = text.replace(match, newline)
			}
		}

		// FAR all parameters
		matcher = text =~ PARAM
		while(matcher.find())
		{
			if (params[matcher.group()])
				text = text.replace(matcher.group(), params[matcher.group()])
		}

		// FAR all methods
		matcher = text =~ METHOD_SMALL
		while(matcher.find())
		{
			if (methods[matcher.group()])
				text = text.replace(matcher.group(), methods[matcher.group()]['name'])
		}

		// FAR all fields
		matcher = text =~ FIELD_SMALL
		while(matcher.find())
		{
			if (fields[matcher.group()])
				text = text.replace(matcher.group(), fields[matcher.group()]['name'])
		}

		// write file
		file.write(text)
	}
}