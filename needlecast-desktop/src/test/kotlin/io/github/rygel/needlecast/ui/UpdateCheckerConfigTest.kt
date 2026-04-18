package io.github.rygel.needlecast.ui

import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test

class UpdateCheckerConfigTest {

    @Test
    fun `sparkle updater can be built against unsigned release appcast`() {
        val instance = buildSparkle4jInstance(
            version = "0.6.19",
            intervalHours = 0,
        )

        assertNotNull(instance)
        instance.close()
    }
}
