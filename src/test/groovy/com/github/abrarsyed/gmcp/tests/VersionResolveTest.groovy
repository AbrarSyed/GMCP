package com.github.abrarsyed.gmcp.tests

import com.github.abrarsyed.gmcp.extensions.GMCPExtension
import org.gradle.api.Project
import org.gradle.testfixtures.ProjectBuilder
import org.junit.Assert
import org.junit.Before
import org.junit.Test

public class VersionResolveTest
{
    private GMCPExtension ext

    @Before
    public void doBefore()
    {
        Project project = ProjectBuilder.builder().build();
        ext = new GMCPExtension(project)
    }

    @Test
    public void test1()
    {
        ext.forgeVersion = "900"
        ext.minecraftVersion = null
        ext.resolveVersion(false)
        Assert.assertEquals("http://files.minecraftforge.net/minecraftforge/minecraftforge-src-1.6.4-9.11.0.900.zip", ext.forgeURL)
    }

    @Test
    public void test2()
    {
        ext.forgeVersion = "latest"
        ext.minecraftVersion = "1.6.3"
        ext.resolveVersion(false)
        Assert.assertEquals("http://files.minecraftforge.net/minecraftforge/minecraftforge-src-1.6.3-9.11.0.878.zip", ext.forgeURL)
    }

    @Test
    public void test3()
    {
        ext.forgeVersion = "recommended-1.6.2"
        ext.minecraftVersion = null
        ext.resolveVersion(false)
        Assert.assertEquals("http://files.minecraftforge.net/minecraftforge/minecraftforge-src-1.6.2-9.10.1.871.zip", ext.forgeURL)
    }
}
