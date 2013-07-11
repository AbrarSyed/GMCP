package com.github.abrarsyed.gmcp.source


public class MCPCleanup
{
    private static String cleanFile(String text)
    {
        text = stripComments(text)

        text = fixImports(text)

        text = cleanup(text)
        
        text = GLConstantFixer.fixOGL(text)

        text
    }

	private static final REGEXP_COMMENTS = [
		comments: /(?ms)\/\/.*?$|\/\*.*?\*\/|\'(?:\\.|[^\\\'])*\'|"(?:\\.|[^\\"])*"/,
		trailing: /(?m)[ \t]+$/,
		newlines: /(?m)^\n{2,}/
	]

	private static String stripComments(String text)
	{
		text.findAll(REGEXP_COMMENTS["comments"])
		{ match ->
			if (match.startsWith("/"))
				text = text.replace(match, "")
		}

		text = text.replaceAll(REGEXP_COMMENTS["trailing"], "")
		text = text.replaceAll(REGEXP_COMMENTS["newlines"], "\n")

		return text
	}

	private static final REGEXP_CLEANUP = [
		header: /^\s+/, // Remove extra whitespace at start of file

		footer: /\s+$/, // Remove extra whitespace at end of file

		trailing: /(?m)[ \t]+$/, // Remove trailing whitespace

		'package': /(?m)^package ([\w.]+);$/, // find package --- in quots since its a special word

		'import': /(?m)^import (?:([\w.]*?)\.)?(?:[\w]+);\n/, // package and class.

		//newlines: /(?m)^\n{2,}/, // remove repeated blank lines   ?? JDT?
        
        ifstarts: /(?m)(^(?![\s{}]*$).+(?:\r\n|\r|\n))((?:[ \t]+)if.*)/,  // add new line before IF statements

		// close up blanks in code like:
		// {
		//
		//     private
		blockstarts: /(?m)(?<=\{)\s+(?=\n[ \t]*\S)/,

		// close up blanks in code like:
		//     }
		//
		// }
		blockends: /(?m)(?<=[;}])\s+(?=\n\s*})/,

		// Remove GL comments and surrounding whitespace
		gl: /\s*\/\*\s*GL_[^*]+\*\/\s*/,

		// convert unicode character constants back to integers
		unicode: /'\\u([0-9a-fA-F]{4})'/,

		// strip out Character.valueof
		charval: /Character\.valueOf\(('.')\)/,

		// 1.7976...E+308D to Double.MAX_VALUE
		maxD: /1\.7976[0-9]*[Ee]\+308[Dd]/,

		// 3.1415...D to Math.PI
		piD: /3\.1415[0-9]*[Dd]/,

		// 3.1415...F to (float)Math.PI
		piF: /3\.1415[0-9]*[Ff]/,

		// 6.2831...D to (Math.PI * 2D)
		'2piD': /6\.2831[0-9]*[Dd]/,

		// 6.2831...F to ((float)Math.PI * 2F)
		'2piF': /6\.2831[0-9]*[Ff]/,

		// 1.5707...D to (Math.PI / 2D)
		pi2D: /1\.5707[0-9]*[Dd]/,

		// 1.5707...F to ((float)Math.PI / 2F)
		pi2F: /1\.5707[0-9]*[Ff]/,

		// 4.7123...D to (Math.PI * 3D / 2D)
		'3pi2D': /4\.7123[0-9]*[Dd]/,

		// 4.7123...F to ((float)Math.PI * 3F / 2F)
		'3pi2F': /4\.7123[0-9]*[Ff]/,

		// 0.7853...D to (Math.PI / 4D)
		pi4D: /0\.7853[0-9]*[Dd]/,

		// 0.7853...F to ((float)Math.PI / 4F)
		pi4F: /0\.7853[0-9]*[Ff]/,

		// 0.6283...D to (Math.PI / 5D)
		pi5D: /0\.6283[0-9]*[Dd]/,

		// 0.6283...F to ((float)Math.PI / 5F)
		pi5F: /0\.6283[0-9]*[Ff]/,

		// 57.295...D to (180D / Math.PI)
		'180piD': /57\.295[0-9]*[Dd]/,

		// 57.295...F to (180F / (float)Math.PI)
		'180piF': /57\.295[0-9]*[Ff]/,

		// 0.6981...D to (Math.PI * 2D / 9D)
		'2pi9D': /0\.6981[0-9]*[Dd]/,

		// 0.6981...F to ((float)Math.PI * 2F / 9F)
		'2pi9F': /0\.6981[0-9]*[Ff]/,

		// 0.3141...D to (Math.PI / 10D)
		pi10D: /0\.3141[0-9]*[Dd]/,

		// 0.3141...F to ((float)Math.PI / 10F)
		pi10F: /0\.3141[0-9]*[Ff]/,

		// 1.2566...D to (Math.PI * 2D / 5D)
		'2pi5D': /1\.2566[0-9]*[Dd]/,

		// 1.2566...F to ((float)Math.PI 2F / 5F)
		'2pi5F': /1\.2566[0-9]*[Ff]/,

		// 0.21991...D to (Math.PI * 7D / 100D)
		'7pi100D': /0\.21991[0-9]*[Dd]/,

		// 0.21991...F to ((float)Math.PI * 7F / 100F)
		'7pi100F': /0\.21991[0-9]*[Ff]/,

		// 5.8119...D to (Math.PI * 185D / 100D)
		'185pi100D': /5\.8119[0-9]*[Dd]/,

		// 5.8119...F to ((float)Math.PI * 185F / 100F)
		'185pi100F': /0\.8119[0-9]*[Ff]/,
	]

	private static String cleanup(String text)
	{
		// simple replacements
		text = text.replaceAll(REGEXP_CLEANUP['header'], "")
		text = text.replaceAll(REGEXP_CLEANUP['footer'], "")
		text = text.replaceAll(REGEXP_CLEANUP['trailing'], "")
		//text = text.replaceAll(REGEXP_CLEANUP['newlines'], "\n")
        text = text.replaceAll(REGEXP_CLEANUP['ifstarts'], '$1\n$2')
		text = text.replaceAll(REGEXP_CLEANUP['blockstarts'], "")
		text = text.replaceAll(REGEXP_CLEANUP['blockends'], "")
		text = text.replaceAll(REGEXP_CLEANUP['gl'], "")
		text = text.replaceAll(REGEXP_CLEANUP['maxD'], "Double.MAX_VALUE")

		// unicode chars
		text.findAll(REGEXP_CLEANUP['unicode']) { match, val ->
			def value = Integer.parseInt(val, 16)
			// work around the replace('\u00a7', '$') call in MinecraftServer and a couple of '\u0000'
			if (value > 255)
				text = text.replace(match, ''+value)
		}

		// charval.. its stupid.
		text = text.replaceAll(REGEXP_CLEANUP['charval'], '$1')   // TESTING NEEDED

		//		 pi?   true
		text = text.replaceAll(REGEXP_CLEANUP['piD'], 'Math.PI')
		text = text.replaceAll(REGEXP_CLEANUP['piF'], '(float)Math.PI')
		text = text.replaceAll(REGEXP_CLEANUP['2piD'], '(Math.PI * 2D)')
		text = text.replaceAll(REGEXP_CLEANUP['2piF'], '((float)Math.PI * 2F)')
		text = text.replaceAll(REGEXP_CLEANUP['pi2D'], '(Math.PI / 2D)')
		text = text.replaceAll(REGEXP_CLEANUP['pi2F'], '((float)Math.PI / 2F)')
		text = text.replaceAll(REGEXP_CLEANUP['3pi2D'], '(Math.PI * 3D / 2D)')
		text = text.replaceAll(REGEXP_CLEANUP['3pi2F'], '((float)Math.PI * 3F / 2F)')
		text = text.replaceAll(REGEXP_CLEANUP['pi4D'], '(Math.PI / 4D)')
		text = text.replaceAll(REGEXP_CLEANUP['pi4F'], '((float)Math.PI / 4F)')
		text = text.replaceAll(REGEXP_CLEANUP['pi5D'], '(Math.PI / 5D)')
		text = text.replaceAll(REGEXP_CLEANUP['pi5F'], '((float)Math.PI / 5F)')
		text = text.replaceAll(REGEXP_CLEANUP['180piD'], '(180D / Math.PI)')
		text = text.replaceAll(REGEXP_CLEANUP['180piF'], '(180F / (float)Math.PI)')
		text = text.replaceAll(REGEXP_CLEANUP['2pi9D'], '(Math.PI * 2D / 9D)')
		text = text.replaceAll(REGEXP_CLEANUP['2pi9F'], '((float)Math.PI * 2F / 9F)')
		text = text.replaceAll(REGEXP_CLEANUP['pi10D'], '(Math.PI / 10D)')
		text = text.replaceAll(REGEXP_CLEANUP['pi10F'], '((float)Math.PI / 10F)')
		text = text.replaceAll(REGEXP_CLEANUP['2pi5D'], '(Math.PI * 2D / 5D)')
		text = text.replaceAll(REGEXP_CLEANUP['2pi5F'], '((float)Math.PI * 2F / 5F)')
		text = text.replaceAll(REGEXP_CLEANUP['7pi100D'], '(Math.PI * 7D / 100D)')
		text = text.replaceAll(REGEXP_CLEANUP['7pi100F'], '((float)Math.PI * 7F / 100F)')
		text = text.replaceAll(REGEXP_CLEANUP['185pi100D'], '(Math.PI * 185D / 100D)')
		text = text.replaceAll(REGEXP_CLEANUP['185pi100F'], '((float)Math.PI * 185F / 100F)')

		return text
	}

	private static String fixImports(String text)
	{
		def match = text =~ REGEXP_CLEANUP['package']

		if (match)
		{
			// it had better have a package....
			def pack = match[0][1]

			text.findAll(REGEXP_CLEANUP['import']) { full, found ->
				if (found == pack)
					text = text.replace(full, '')
			}
		}

		return text
	}
}
