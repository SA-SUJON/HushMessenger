package app.hushmessenger.patches

import java.nio.file.Files
import java.nio.file.Path
import java.time.LocalDateTime
import kotlin.test.Test
import kotlin.test.assertEquals

class SourceIndexTest {
    @Test
    fun `source timestamp is accepted by Manager LocalDateTime serializer`() {
        val index = Files.readString(Path.of("../patches-bundle.json"))
        val timestamps = Regex("\"created_at\"\\s*:\\s*\"([^\"]+)\"").findAll(index).toList()
        assertEquals(1, timestamps.size)
        // Manager treats this local date-time as UTC later. A trailing Z fails parsing.
        LocalDateTime.parse(timestamps.single().groupValues[1])
    }
}
