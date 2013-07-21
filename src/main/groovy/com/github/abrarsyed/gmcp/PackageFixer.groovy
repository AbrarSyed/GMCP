package com.github.abrarsyed.gmcp

import au.com.bytecode.opencsv.CSVParser
import au.com.bytecode.opencsv.CSVReader


public class PackageFixer
{

	def packages
	public static final String SIG_PATTERN = /([\[ZBCSIJFDV]|L([\w\\/]+);)/
	public static final String PACK_PATTERN = /net\\minecraft\\src\\\w+/

	public PackageFixer(file)
	{
		def reader = new CSVReader(new FileReader(file), CSVParser.DEFAULT_SEPARATOR, CSVParser.DEFAULT_QUOTE_CHARACTER, CSVParser.DEFAULT_ESCAPE_CHARACTER, 1, false)
		packages = [:]
		reader.readAll().each
		{
			packages[it[0]] = it[1]
		}
	}

	def fixSRG(File inSRG, File outSRG)
	{
		if (!outSRG.exists())
		{
			outSRG.getParentFile().mkdirs()
			outSRG.createNewFile()
		}

		def outText = new StringBuilder()

		inSRG.text.readLines().each
		{
			List<String> sections = it.split(" ")
			def oldSections = sections
			def line = it

			switch(sections[0])
			{
				case "CL:": // class decleration
					sections[2] = repackageClass(sections[2])
					break

				case "FD:":
					def split = rsplit(sections[2], "/")
					split[0] = repackageClass(split[0])
					sections[2] = split.join("/")
					break

				case "MD:":
					def split = rsplit(sections[3], "/")
					split[0] = repackageClass(split[0])
					sections[3] = split.join("/")
					sections[4] = repackageSig(sections[4])
					break
			}

			line = sections.join(" ")
			outText.append(line).append("\n")
		}

		outSRG.write(outText.toString())
	}

	def fixExceptor(File inExc, File outExc)
	{
		if (!outExc.exists())
		{
			outExc.getParentFile().mkdirs()
			outExc.createNewFile()
		}

		def outText = new StringBuilder()

		inExc.text.readLines().each
		{
			def cls = it.split(/\./)[0] 			// get classname
			def named = rsplit(it, "=")[1] 			// get method name
			it = it.substring(cls.size() + 1, it.size() - named.size()) 			// get param names
			def methName = it.split(/\(/)[0] 			// method get name and sig

			def temp = named.split(/\|/) as List

			// temp[0] is the exceptions
			if (temp[0])
			{
				def excs = temp[0].split(",")
				excs = excs.collect { repackageClass(it) }

				if (temp.size() < 2)
					temp.add("")

				named = excs.join(",") + "|" + temp[1]
			}

			def sig = repackageSig(it.substring(methName.length()))
			cls = repackageClass(cls)

			outText.append(cls + "." + methName + sig + "=" + named + "\n")
		}

		outExc.write(outText.toString())
	}

	def fixPatch(File patch)
	{
		def text = patch.text

		text.findAll(PACK_PATTERN)
		{ match ->
			def cls = repackageClass(match.replace('\\', '/')).replace('/', '\\')
			text = text.replace(match, cls)
		}

		text.replaceAll(/(\r\n|\n|\r)/, "\n")

		patch.write(text)
	}

	private String repackageClass(String input)
	{
		if (input.startsWith("net/minecraft/src"))
		{
			def className = input.substring(18)
			if (packages[className])
				return packages[className]+"/"+className
		}

		return input
	}

	private String repackageSig(String sig)
	{
		def split = rsplit(sig, ")")
		def params = split[0]
		def ret = split[1]

		def out = "("

		// add in changed parameters
		def match = params =~ SIG_PATTERN
		while (match.find())
		{
			if (match.group().length() > 1)
			{
				def tempTwo = match.group(2)
				out += "L"+repackageClass(match.group(2))+";"
			}
			else
				out += match.group()
		}

		out += ")"

		match = ret =~ SIG_PATTERN
		while (match.find())
		{
			if (match.group().length() > 1)
			{
				def tempTwo = match.group(2)
				out += "L"+repackageClass(match.group(2))+";"
			}
			else
				out += match.group()
		}

		return out
	}

	public static rsplit(String input, String splitter)
	{
		def index = input.lastIndexOf(splitter)

		if (index == -1)
			return input

		def pieceOne = input.substring(0, index)
		def pieceTwo = input.substring(index+1)
		return [pieceOne, pieceTwo]
	}

}