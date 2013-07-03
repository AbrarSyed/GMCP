package com.github.abrarsyed.gmcp

import java.util.HashMap;

class ConfigParser
{
	private String file;
	private final category = /\[(.*?)\]/
	private final property = /\s?(.+?)\s=\s(.+)\s?/
	HashMap<String, HashMap<String, String>> map

	public ConfigParser(File file)
	{
		this.file = file
		read()
	}

	public void read()
	{
		map = new HashMap();

		def match, inMap, currentCat;
		new File(file).eachLine
		{
			// skip it.
			if (it.isEmpty() || it.isAllWhitespace())
				return;

			// category
			if (it ==~ category)
			{
				currentCat = (it =~ category)[0][1];
				map.put(currentCat, new HashMap<String, String>());
				//println currentCat
				return;
			}
			else if (it =~ property)
			{
				match = it =~ property;
				map.get(currentCat).put(match[0][1].trim(), match[0][2].trim())
				//println match[0][1].trim()+"="+match[0][2].trim()
			}
		}
	}

	def getProperty(cat, key)
	{
		map.get(cat).get(key)
	}
}