package app.aaps.ui.compose.overview.graphs

import app.aaps.core.interfaces.overview.graph.GraphDataPoint
import app.aaps.core.interfaces.overview.graph.IobGraphData
import kotlin.test.Test
import kotlin.test.assertEquals

class IobGraphPointsTest {

    @Test
    fun `IOB points include the future prediction samples`() {
        val data = IobGraphData(
            iob = listOf(GraphDataPoint(1_000L, 2.0), GraphDataPoint(2_000L, 1.5)),
            predictions = listOf(GraphDataPoint(2_000L, 1.4), GraphDataPoint(3_000L, 1.0))
        )

        assertEquals(
            listOf(
                GraphDataPoint(1_000L, 2.0),
                GraphDataPoint(2_000L, 1.5),
                GraphDataPoint(3_000L, 1.0)
            ),
            data.allPoints()
        )
    }
}
