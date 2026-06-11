package io.github.rygel.needlecast.ui

import io.github.rygel.needlecast.model.BuildTool
import io.github.rygel.needlecast.model.CommandDescriptor
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.condition.EnabledIf

class EditCommandDialogTest {
    companion object {
        @JvmStatic
        fun isHeadful(): Boolean = !java.awt.GraphicsEnvironment.isHeadless()
    }

    @Test
    @EnabledIf("isHeadful")
    fun `dialog initializes with null result`() {
        val cmd = CommandDescriptor("build", BuildTool.MAVEN, listOf("mvn", "clean"), "/project")
        val dialog = EditCommandDialog(null, cmd)

        assertNull(dialog.result)

        dialog.dispose()
    }

    @Test
    @EnabledIf("isHeadful")
    fun `result is null when dialog is not shown`() {
        val cmd = CommandDescriptor("test", BuildTool.GRADLE, listOf("gradle", "test"), "/project")
        val dialog = EditCommandDialog(null, cmd)

        assertNull(dialog.result)

        dialog.dispose()
    }

    @Test
    fun `toHtmlLabel escapes ampersand`() {
        val result = "foo&bar".toHtmlLabel()
        assertEquals("<html>foo&amp;bar</html>", result)
    }

    @Test
    fun `toHtmlLabel escapes angle brackets`() {
        val result = "a<b>c".toHtmlLabel()
        assertEquals("<html>a&lt;b&gt;c</html>", result)
    }

    @Test
    fun `toHtmlLabel wraps plain text in html`() {
        val result = "hello".toHtmlLabel()
        assertEquals("<html>hello</html>", result)
    }
}
