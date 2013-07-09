package com.github.abrarsyed.gmcp.source

import java.util.regex.Pattern

class FMLCleanup
{
	def static final before = /(?m)((case|default).+\r?\n)\r?\n/ // Fixes newline after case before case body
	def static final after = /(?m)\r?\n(\r?\n[ \t]+(case|default))/ // Fixes newline after case body before new case

	public static void updateFile(File f)
	{
		def text = f.text

		text.findAll(before) { match, group, words->
			text = text.replace(match, group)
		}

		text.findAll(after) { match, group, words->
			text = text.replace(match, group)
		}

		text = renameClass(text)

		text = text.replace("\r", "")

		f.write(text)
	}

	// FML RENAMMING STUFF

	private static final Pattern METHOD_REG = ~/^ {4}(\w+\s+\S.*\(.*|static)$/
	private static final Pattern CATCH_REG = ~/catch \((.*)\)$/
	private static final Pattern NOID1 = ~/\(.*\(/
	private static final Pattern NOID2 = ~/\((.+)\)/
	private static final Pattern THROWERS = ~/(}|\);|throws .+?;)$/
	private static final Pattern METHOD_END = ~/^ {4}\}$/


	private static String renameClass(String text)
	{
		def lines = text.readLines()
		def output = ""

		def insideMethod = false
		def method = ""
		def methodVars = []
		def skip = false

		lines.each {
			// if re.search(METHOD_REG, line) and not re.search('=', line) and not re.search(r'\(.*\(', line):
			if (METHOD_REG.matcher(it) && !it.contains("=") && !NOID1.matcher(it))
			{
				// if re.search(r'\(.+\)', line):
				def match = NOID2.matcher(it)
				if (match)
				{
					def group = match.group(1)
					methodVars.addAll(group.split(',').collect {it.trim()})
				}
				// method_variables += [s.strip() for s in re.search(r'\((.+)\)', line).group(1).split(',')]

				method += it+"\n"
				// method += line

				// single line method ?
				skip = true

				// if not re.search(r'(}|\);|throws .+?;)$', line):
				if (!THROWERS.matcher(it))
					insideMethod = true
			}
			else if (METHOD_END.matcher(it))
				insideMethod = false

			if (insideMethod)
			{
				if (skip)
				{
					skip = false
					return
				}

				method += it + "\n"

				def m = CATCH_REG.matcher(it)
				if (m)
					methodVars += m.group(1)
				else
				{
					it.findAll(/[\w$][\w\[\]]+ var\d+/){ match ->
						if (match.startsWith("return") || match.startsWith("throw"))
							return
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
					methodVars.each {
						todo.putAt(it, namer.getName(it.split(" ")[0], it))
					}

					def replace = [:]
					todo.each{ key, val ->
						if (!key.contains(" "))
							return
						else
							replace[key.split(' ')[1]] = val
					}

					replace.sort().reverseEach { key, val ->
						// don't rename already renamed stuff
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
					return
				}

				output += it+"\n"
			}
		}

		return output.substring(0, output.length()-1)
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
		def index

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

			if (type =~ /\[/)
			{
				name = "a"+name
				name = name.replace('[', '').replace(']', '').replace('...', '')
			}

			last[type] = [0, 1, [name]]
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
		last[index][0]++

		def amount = data.size()

		if (amount == 1)
			return data[0] + ( !id && skip_zero ? '' : id)
		else
		{
			def num = (int)id/amount
			return data[id % amount] + ( !num && skip_zero ? '' : num)
		}
	}
}