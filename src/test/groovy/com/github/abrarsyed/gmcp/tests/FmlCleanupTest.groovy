package com.github.abrarsyed.gmcp.tests

import com.github.abrarsyed.gmcp.source.FMLCleanup
import org.junit.Assert
import org.junit.Before
import org.junit.Test

class FmlCleanupTest
{
    def String initial
    def String expected

    @Before
    def void before()
    {
        println new File('.').getAbsoluteFile()
        initial = new File('src/test/resources/CleanupTestPre.txt').text
        expected = new File('src/test/resources/CleanupTestPost.txt').text
    }

    @Test
    def void test()
    {
        initial = FMLCleanup.renameClass(initial)

        def inLines = initial.readLines()
        def outLines = expected.readLines()

        inLines.size().times {
            def passed = outLines[it] == inLines[it]
            if (passed)
            {
                println "PASSED : " + inLines[it]
            }
            else
            {
                println "FAILED!"
                println "EXPECTED : " +outLines[it]
                println "ACTUAL   : " +inLines[it]
            }

            Assert.assertEquals outLines[it], inLines[it]
        }
    }
}
