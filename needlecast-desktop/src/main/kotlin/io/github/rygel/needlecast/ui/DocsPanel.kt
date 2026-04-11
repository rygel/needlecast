package io.github.rygel.needlecast.ui

import java.awt.BorderLayout
import java.io.File
import java.nio.file.FileVisitResult
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.SimpleFileVisitor
import java.nio.file.attribute.BasicFileAttributes
import javax.swing.JPanel

class DocsPanel : JPanel(BorderLayout()) {

    fun loadProject(path: String?) {
        // TODO: implemented in Task 3
    }

    companion object {
        private val SKIP_DIRS = setOf(".git", "target", "node_modules", "build", ".gradle")

        /** Collects relative paths of *.md files under [root], README variants first. */
        fun collectMarkdownFiles(root: File?): List<String> {
            if (root == null || !root.isDirectory) return emptyList()
            val found = mutableListOf<String>()
            Files.walkFileTree(root.toPath(), object : SimpleFileVisitor<Path>() {
                override fun preVisitDirectory(dir: Path, attrs: BasicFileAttributes): FileVisitResult {
                    val name = dir.fileName?.toString() ?: return FileVisitResult.CONTINUE
                    return if (name in SKIP_DIRS) FileVisitResult.SKIP_SUBTREE
                    else FileVisitResult.CONTINUE
                }
                override fun visitFile(file: Path, attrs: BasicFileAttributes): FileVisitResult {
                    if (file.fileName.toString().endsWith(".md", ignoreCase = true)) {
                        found.add(root.toPath().relativize(file).toString().replace(File.separatorChar, '/'))
                    }
                    return FileVisitResult.CONTINUE
                }
            })
            return found.sortedWith(Comparator { a, b ->
                val aIsReadme = a.substringAfterLast('/').equals("README.md", ignoreCase = true)
                val bIsReadme = b.substringAfterLast('/').equals("README.md", ignoreCase = true)
                when {
                    aIsReadme && !bIsReadme -> -1
                    !aIsReadme && bIsReadme ->  1
                    else -> a.compareTo(b, ignoreCase = true)
                }
            })
        }
    }
}
