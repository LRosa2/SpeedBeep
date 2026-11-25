package io.github.lrosa2.speedbeep.core.road

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import org.junit.jupiter.params.provider.ValueSource

class RoadClassifierTest {

    private fun tags(vararg pairs: Pair<String, String>) = mapOf(*pairs)

    @ParameterizedTest(name = "maxspeed \"{0}\" is {1} km/h")
    @CsvSource(
        "50, 50",
        "'80 km/h', 80",
        "CH:urban, 50",
        "ch:rural, 80",
        "CH:trunk, 100",
        "CH:motorway, 120",
        "CH:zone30, 30",
        "CH:30, 30",
        "CH:zone20, 20",
        "walk, 20",
        "'30 mph', 48",
        "'60;80', 60",
    )
    fun `parses usable limits`(raw: String, expected: Int) {
        assertEquals(expected, RoadClassifier.parseMaxspeed(raw))
    }

    @ParameterizedTest(name = "maxspeed \"{0}\" has no usable limit")
    @ValueSource(strings = ["none", "signals", "variable", "fast", "", "  "])
    fun `values without a usable limit give null`(raw: String) {
        assertNull(RoadClassifier.parseMaxspeed(raw))
    }

    @Test
    fun `missing value gives null`() {
        assertNull(RoadClassifier.parseMaxspeed(null))
    }

    @Test
    fun `motorway and trunk without a limit are fast`() {
        for (highway in listOf("motorway", "motorway_link", "trunk", "trunk_link")) {
            assertEquals(RoadClass.FAST, RoadClassifier.classify(tags("highway" to highway)), highway)
        }
    }

    @Test
    fun `an explicit limit wins over the road type`() {
        assertEquals(RoadClass.TOWN, RoadClassifier.classify(tags("highway" to "trunk", "maxspeed" to "50")))
        assertEquals(RoadClass.FAST, RoadClassifier.classify(tags("highway" to "residential", "maxspeed" to "60")))
        assertEquals(RoadClass.FAST, RoadClassifier.classify(tags("highway" to "primary", "maxspeed" to "80")))
    }

    @Test
    fun `other roads without any speed information are town (known gap)`() {
        for (highway in listOf("primary", "secondary", "tertiary", "unclassified", "residential", "living_street", "road")) {
            assertEquals(RoadClass.TOWN, RoadClassifier.classify(tags("highway" to highway)), highway)
        }
    }

    @Test
    fun `fallback tags give the limit when maxspeed is missing`() {
        assertEquals(RoadClass.FAST, RoadClassifier.classify(tags("highway" to "secondary", "source:maxspeed" to "CH:rural")))
        assertEquals(RoadClass.FAST, RoadClassifier.classify(tags("highway" to "secondary", "maxspeed:type" to "CH:rural")))
        assertEquals(RoadClass.TOWN, RoadClassifier.classify(tags("highway" to "residential", "zone:maxspeed" to "CH:30")))
    }

    @Test
    fun `maxspeed wins over the type tag`() {
        assertEquals(50, RoadClassifier.effectiveLimit(tags("maxspeed" to "50", "maxspeed:type" to "CH:rural")))
    }

    @Test
    fun `the lower of the forward and backward limits wins`() {
        assertEquals(50, RoadClassifier.effectiveLimit(tags("maxspeed:forward" to "80", "maxspeed:backward" to "50")))
        assertEquals(
            RoadClass.TOWN,
            RoadClassifier.classify(tags("highway" to "primary", "maxspeed:forward" to "80", "maxspeed:backward" to "50")),
        )
    }

    @Test
    fun `a limit for one direction only never raises the limit of the whole road`() {
        // A lone value says nothing about the other side, so above 50 it is ignored (D10).
        assertNull(RoadClassifier.effectiveLimit(tags("maxspeed:forward" to "80")))
        assertEquals(
            RoadClass.TOWN,
            RoadClassifier.classify(tags("highway" to "secondary", "maxspeed:forward" to "80", "source:maxspeed" to "CH:urban")),
        )
    }

    @Test
    fun `a lower limit for one direction always counts`() {
        assertEquals(RoadClass.TOWN, RoadClassifier.classify(tags("highway" to "trunk", "maxspeed:forward" to "50")))
        assertEquals(
            RoadClass.TOWN,
            RoadClassifier.classify(tags("highway" to "secondary", "maxspeed:backward" to "30", "source:maxspeed" to "CH:rural")),
        )
        assertEquals(50, RoadClassifier.effectiveLimit(tags("maxspeed" to "80", "maxspeed:backward" to "50")))
    }

    @Test
    fun `a variable motorway limit counts as no limit, so the motorway stays fast`() {
        assertEquals(RoadClass.FAST, RoadClassifier.classify(tags("highway" to "motorway", "maxspeed" to "signals")))
    }

    @Test
    fun `non-drivable ways are left out`() {
        for (highway in listOf("service", "track", "path", "footway", "cycleway", "pedestrian", "construction")) {
            assertNull(RoadClassifier.classify(tags("highway" to highway)), highway)
        }
        assertNull(RoadClassifier.classify(tags("name" to "no highway tag")))
    }

    @Test
    fun `areas and closed roads are left out unless cars are allowed`() {
        assertNull(RoadClassifier.classify(tags("highway" to "residential", "area" to "yes")))
        assertNull(RoadClassifier.classify(tags("highway" to "residential", "access" to "no")))
        assertNull(RoadClassifier.classify(tags("highway" to "residential", "motor_vehicle" to "no")))
        assertEquals(
            RoadClass.TOWN,
            RoadClassifier.classify(tags("highway" to "residential", "access" to "no", "motorcar" to "yes")),
        )
    }
}
