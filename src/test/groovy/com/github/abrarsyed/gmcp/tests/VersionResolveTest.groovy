package com.github.abrarsyed.gmcp.tests

import org.junit.Assert
import org.junit.Before
import org.junit.Test

import com.github.abrarsyed.gmcp.extensions.GMCPExtension

public class VersionResolveTest
{
    private GMCPExtension ext

    @Before
    public void doBefore()
    {
        ext = new GMCPExtension()
    }

    @Test
    public void test1()
    {
        ext.forgeVersion = "738"
        ext.minecraftVersion = null
        ext.resolveVersion(false)
        Assert.assertEquals("http://files.minecraftforge.net/minecraftforge/minecraftforge-src-1.5.2-7.8.1.738.zip", ext.forgeURL)
    }

    @Test
    public void test2()
    {
        ext.forgeVersion = "latest"
        ext.minecraftVersion = "1.5.1"
        ext.resolveVersion(false)
        Assert.assertEquals("http://files.minecraftforge.net/minecraftforge/minecraftforge-src-1.5.1-7.7.2.682.zip", ext.forgeURL)
    }

    @Test
    public void test3()
    {
        ext.forgeVersion = "recommended-1.5.2"
        ext.minecraftVersion = null
        ext.resolveVersion(false)
        Assert.assertEquals("http://files.minecraftforge.net/minecraftforge/minecraftforge-src-1.5.2-7.8.1.737.zip", ext.forgeURL)
    }
}
