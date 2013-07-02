package com.github.abrarsyed.gmcp.tests

import org.gradle.api.Project
import org.gradle.testfixtures.ProjectBuilder
import org.junit.Assert
import org.junit.Before
import org.junit.Test

public class VersionResolveTest
{
	Project project

	@Before
	public void doBefore()
	{
		project = ProjectBuilder.builder().build()
		project.apply plugin: "gmcp"
	}

	@Test
	public void test()
	{
		project.minecraft.forgeVersion = "738"
		Assert.assertEquals("http://files.minecraftforge.net/minecraftforge/minecraftforge-src-1.5.2-7.8.1.738.zip", project.minecraft.forgeURL)

		project.minecraft.forgeVersion = "latest"
		project.minecraft.minecraftVersion = "1.5.1"
		Assert.assertEquals("http://files.minecraftforge.net/minecraftforge/minecraftforge-src-1.5.1-7.7.2.682.zip", project.minecraft.forgeURL)
	}
}
