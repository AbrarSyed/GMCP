package com.github.abrarsyed.gmcp.tests

import static org.junit.Assert.*

import org.junit.Assert
import org.junit.Test

import com.github.abrarsyed.gmcp.Constants

class ResourceTest
{
    @Test
    void test()
    {
        Assert.assertNotNull("Could not find Formatter config", this.getClass().classLoader.getResource(Constants.REC_FORMAT_CFG))
        Assert.assertNotNull("Could not find patch exe", this.getClass().classLoader.getResource(Constants.REC_PATCH_EXEC))
    }
}
