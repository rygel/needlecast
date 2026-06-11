package io.github.rygel.needlecast.ui.explorer

import io.github.rygel.needlecast.AppContext
import io.github.rygel.needlecast.config.JsonConfigStore
import io.github.rygel.needlecast.config.PromptLibraryStore
import io.github.rygel.needlecast.config.SkillLibraryStore
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.awt.Toolkit
import java.awt.datatransfer.DataFlavor
import java.io.File
import java.nio.file.Path
import javax.swing.JPanel

class ExplorerFileOpsTest {
    @TempDir
    lateinit var tempDir: Path

    private fun makeContext(): AppContext {
        val configStore = JsonConfigStore(tempDir.resolve("config.json"))
        val promptStore =
            PromptLibraryStore(
                tempDir.resolve("prompts"),
                tempDir.resolve("commands"),
            )
        val skillStore = SkillLibraryStore(tempDir.resolve("skills"))
        return AppContext(
            configStore = configStore,
            promptLibraryStore = promptStore,
            skillLibraryStore = skillStore,
        )
    }

    @Test
    fun `createFolder creates directory`() {
        val folder = File(tempDir.toFile(), "new-folder")
        assertTrue(folder.mkdir())
        assertTrue(folder.isDirectory)
    }

    @Test
    fun `createFolder rejects duplicate`() {
        val folder = File(tempDir.toFile(), "existing")
        assertTrue(folder.mkdir())
        assertFalse(folder.mkdir())
    }

    @Test
    fun `createFile creates new file`() {
        val file = File(tempDir.toFile(), "notes.txt")
        assertTrue(file.createNewFile())
        assertTrue(file.isFile)
    }

    @Test
    fun `renameEntry renames file`() {
        val original = File(tempDir.toFile(), "old.txt")
        original.createNewFile()
        val renamed = File(tempDir.toFile(), "new.txt")
        assertTrue(original.renameTo(renamed))
        assertFalse(original.exists())
        assertTrue(renamed.exists())
    }

    @Test
    fun `renameEntry renames directory`() {
        val original = File(tempDir.toFile(), "old-dir")
        original.mkdir()
        File(original, "child.txt").createNewFile()
        val renamed = File(tempDir.toFile(), "new-dir")
        assertTrue(original.renameTo(renamed))
        assertFalse(original.exists())
        assertTrue(renamed.isDirectory)
        assertTrue(File(renamed, "child.txt").exists())
    }

    @Test
    fun `renameEntry rejects blank name`() {
        val original = File(tempDir.toFile(), "file.txt")
        original.createNewFile()
        val dest = File(original.parentFile, "")
        assertFalse(original.renameTo(dest))
        assertTrue(original.exists())
    }

    @Test
    fun `deleteEntry deletes file`() {
        val file = File(tempDir.toFile(), "to-delete.txt")
        file.createNewFile()
        assertTrue(file.deleteRecursively())
        assertFalse(file.exists())
    }

    @Test
    fun `deleteEntry deletes directory recursively`() {
        val dir = File(tempDir.toFile(), "dir")
        dir.mkdir()
        File(dir, "child.txt").createNewFile()
        File(dir, "subdir").mkdir()
        File(dir, "subdir/nested.txt").createNewFile()
        assertTrue(dir.deleteRecursively())
        assertFalse(dir.exists())
    }

    @Test
    fun `copyPath puts absolute path on clipboard`() {
        val ctx = makeContext()
        val file = File(tempDir.toFile(), "clip-test.txt")
        file.createNewFile()
        val callbacks =
            ExplorerCallbacks(
                navigateTo = {},
                navigateUp = {},
                openFileInTab = {},
                reloadDirectory = {},
                currentDir = { tempDir.toFile() },
            )
        val ops = ExplorerFileOps(ctx, callbacks, JPanel())
        ops.copyPath(file)
        val clipboard = Toolkit.getDefaultToolkit().systemClipboard
        val contents = clipboard.getData(DataFlavor.stringFlavor) as String
        assertEquals(file.absolutePath, contents)
    }
}
