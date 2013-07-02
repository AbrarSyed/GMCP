package com.github.abrarsyed.gmcp.tests

import org.junit.Assert
import org.junit.Before
import org.junit.Test

import com.github.abrarsyed.gmcp.GMCPExtension

public class VersionResolveTest
{
	GMCPExtension ext

	@Before
	public void doBefore()
	{
		ext = new GMCPExtension()
		System.out.println(ext)
	}

	@Test
	public void test()
	{
		ext.setForgeVersion("738")
		Assert.assertEquals("http://files.minecraftforge.net/minecraftforge/minecraftforge-src-1.5.2-7.8.1.738.zip", ext.getForgeURL())

		ext.setForgeVersion("latest")
		ext.setMinecraftVersion("1.5.1")
		Assert.assertEquals("http://files.minecraftforge.net/minecraftforge/minecraftforge-src-1.5.1-7.7.2.682.zip", ext.getForgeURL())
	}

	private boolean getResolved()
	{
		return ext.resolved
	}
}
