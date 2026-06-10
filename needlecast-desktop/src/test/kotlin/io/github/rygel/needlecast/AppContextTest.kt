package io.github.rygel.needlecast

import io.github.rygel.needlecast.config.JsonConfigStore
import io.github.rygel.needlecast.config.PromptLibraryStore
import io.github.rygel.needlecast.config.SkillLibraryStore
import io.github.rygel.needlecast.model.AppConfig
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import java.util.Locale

class AppContextTest {
    @Test
    fun `disposeAll calls dispose on every registered disposable`(
        @TempDir dir: Path,
    ) {
        val ctx = newTestContext(dir)
        val disposed = mutableListOf<String>()
        ctx.register(
            object : Disposable {
                override fun dispose() {
                    disposed.add("a")
                }
            },
        )
        ctx.register(
            object : Disposable {
                override fun dispose() {
                    disposed.add("b")
                }
            },
        )
        ctx.register(
            object : Disposable {
                override fun dispose() {
                    disposed.add("c")
                }
            },
        )

        ctx.disposeAll()

        assertEquals(listOf("a", "b", "c"), disposed)
    }

    @Test
    fun `disposeAll clears the disposables list`(
        @TempDir dir: Path,
    ) {
        val ctx = newTestContext(dir)
        ctx.register(
            object : Disposable {
                override fun dispose() {}
            },
        )
        ctx.disposeAll()

        var secondCallCount = 0
        ctx.register(
            object : Disposable {
                override fun dispose() {
                    secondCallCount++
                }
            },
        )
        ctx.disposeAll()

        assertEquals(1, secondCallCount)
    }

    @Test
    fun `addConfigListener fires on updateConfig`(
        @TempDir dir: Path,
    ) {
        val ctx = newTestContext(dir)
        var receivedConfig: AppConfig? = null
        ctx.addConfigListener { receivedConfig = it }

        val updated = ctx.config.copy(theme = "test-theme")
        ctx.updateConfig(updated)

        assertNotNull(receivedConfig)
        assertEquals("test-theme", receivedConfig!!.theme)
    }

    @Test
    fun `updateConfig updates the config property`(
        @TempDir dir: Path,
    ) {
        val ctx = newTestContext(dir)
        val before = ctx.config.theme

        ctx.updateConfig(ctx.config.copy(theme = "brand-new-theme"))

        assertEquals("brand-new-theme", ctx.config.theme)
        assertNotEquals(before, ctx.config.theme)
    }

    @Test
    fun `multiple config listeners all fire`(
        @TempDir dir: Path,
    ) {
        val ctx = newTestContext(dir)
        var count1 = 0
        var count2 = 0
        ctx.addConfigListener { count1++ }
        ctx.addConfigListener { count2++ }

        ctx.updateConfig(ctx.config.copy(theme = "fire-test"))

        assertEquals(1, count1)
        assertEquals(1, count2)
    }

    @Test
    fun `switchLocale updates config language`(
        @TempDir dir: Path,
    ) {
        val ctx = newTestContext(dir)

        ctx.switchLocale(Locale.forLanguageTag("es"))

        assertEquals("es", ctx.config.language)
    }

    @Test
    fun `switchLocale updates config and i18n locale`(
        @TempDir dir: Path,
    ) {
        val ctx = newTestContext(dir)

        ctx.switchLocale(Locale.forLanguageTag("de"))

        assertEquals("de", ctx.config.language)
        assertEquals(Locale.forLanguageTag("de"), ctx.i18n.getLocale())
    }

    @Test
    fun `reloadTheme does not throw`(
        @TempDir dir: Path,
    ) {
        val ctx = newTestContext(dir)

        ctx.reloadTheme()
    }

    @Test
    fun `saveConfig persists config`(
        @TempDir dir: Path,
    ) {
        val ctx = newTestContext(dir)
        ctx.updateConfig(ctx.config.copy(theme = "save-test"))
        ctx.saveConfig()

        val ctx2 = newTestContext(dir)
        assertEquals("save-test", ctx2.config.theme)
    }

    @Test
    fun `config is loaded from store on construction`(
        @TempDir dir: Path,
    ) {
        val ctx1 = newTestContext(dir)
        ctx1.updateConfig(ctx1.config.copy(theme = "loaded-theme"))

        val ctx2 = newTestContext(dir)

        assertEquals("loaded-theme", ctx2.config.theme)
    }

    @Test
    fun `i18n is initialized from config language`(
        @TempDir dir: Path,
    ) {
        val ctx = newTestContext(dir)

        assertEquals(Locale.forLanguageTag(ctx.config.language), ctx.i18n.getLocale())
    }

    @Test
    fun `register after disposeAll only disposes newly registered items`(
        @TempDir dir: Path,
    ) {
        val ctx = newTestContext(dir)
        var firstCount = 0
        var secondCount = 0
        ctx.register(
            object : Disposable {
                override fun dispose() {
                    firstCount++
                }
            },
        )
        ctx.disposeAll()
        ctx.register(
            object : Disposable {
                override fun dispose() {
                    secondCount++
                }
            },
        )

        ctx.disposeAll()

        assertEquals(1, firstCount)
        assertEquals(1, secondCount)
    }

    private fun newTestContext(dir: Path): AppContext {
        val configStore = JsonConfigStore(dir.resolve("config.json"))
        val promptStore =
            PromptLibraryStore(
                dir.resolve("prompts"),
                dir.resolve("commands"),
            )
        val skillStore = SkillLibraryStore(dir.resolve("skills"))
        return AppContext(
            configStore = configStore,
            promptLibraryStore = promptStore,
            skillLibraryStore = skillStore,
        )
    }
}
