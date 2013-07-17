package com.github.abrarsyed.gmcp.tests

import org.junit.Assert
import org.junit.Before
import org.junit.Test

import au.com.bytecode.opencsv.CSVParser
import au.com.bytecode.opencsv.CSVReader


class FieldCSVTest
{
    private static final File CSV = new File("src/test/resources/fields.csv")

    private static final Map OUTPUT = [
        'field_100000_e' : [name: 'spawnChances', javadoc: 'Chances for slimes to spawn in swamps for every moon phase.'],
        'field_100001_o' : [name: 'doneBtn', javadoc: '"Done" button for the GUI.'],
        'field_100002_d' : [name: 'cancelBtn', javadoc:''],
        'field_70138_W'  : [name: 'stepHeight', javadoc: 'How high this entity can step up when running into a block to try to get over it (currently make note the entity will always step up this amount and not just the amount needed)'],
        'field_70139_V'  : [name: 'ySize', javadoc:''],
        'field_70140_Q'  : [name: 'distanceWalkedModified', javadoc: 'The distance walked multiplied by 0.6'],
        'field_70141_P'  : [name: 'prevDistanceWalkedModified', javadoc: 'The previous ticks distance walked multiplied by 0.6'],
        'field_70142_S'  : [name: 'lastTickPosX', javadoc: 'The entity\'s X coordinate at the previous tick, used to calculate position during rendering routines'],
        'field_70143_R'  : [name: 'fallDistance', javadoc:''],
        'field_70158_ak' : [name: 'ignoreFrustumCheck', javadoc: 'Render entity even if it is outside the camera frustum. Only true in EntityFish for now. Used in RenderGlobal: render if ignoreFrustumCheck or in frustum.'],
        'field_70159_w'  : [name: 'motionX', javadoc: 'Entity motion X'],
        'field_70160_al' : [name: 'isAirBorne', javadoc:''],
        'field_70161_v'  : [name: 'posZ', javadoc: 'Entity position Z'],
        'field_70162_ai' : [name: 'chunkCoordY', javadoc:''],
        'field_70163_u'  : [name: 'posY', javadoc: 'Entity position Y'],
        'field_76234_F'  : [name: 'materialMapColor', javadoc: 'The color index used to draw the blocks of this material on maps.'],
        'field_76235_G'  : [name: 'canBurn', javadoc: 'Bool defining if the block can burn or not.']
    ]

    @Before
    public void before()
    {
        println ''
        println 'EXPECTED --------------------------------------------'
        println OUTPUT
        println ''
    }

    @Test
    public void testLib()
    {
        def reader = new CSVReader(CSV.newReader(), CSVParser.DEFAULT_SEPARATOR, CSVParser.DEFAULT_QUOTE_CHARACTER, CSVParser.DEFAULT_ESCAPE_CHARACTER, 1, false)
        def fields = [:]

        reader.readAll().each
        {
            fields[it[0]] = [name: it[1], javadoc: it[3]]
        }

        println ''
        println 'LIB --------------------------------------------'
        println fields
        println ''
        assertMaps(OUTPUT, fields)
    }

    private assertMaps(Map expected, Map actual)
    {
        expected.each
        {  key, val ->

            if (val instanceof Map)
                assertMaps(val, actual[key])
            else
                Assert.assertEquals(val, actual[key])
        }
    }
}
