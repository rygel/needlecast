package io.github.rygel.needlecast.scanner

import io.github.rygel.needlecast.model.BuildTool
import io.github.rygel.needlecast.model.CommandDescriptor
import io.github.rygel.needlecast.model.DetectedProject
import io.github.rygel.needlecast.model.ProjectDirectory
import java.io.File

class ScriptDirectoryScanner : ProjectScanner {

    override fun scan(directory: ProjectDirectory): DetectedProject? {
        val root = File(directory.path)

        val candidates = buildList {
            add(File(root, "scripts"))
            add(File(root, "bin"))
            for (extra in directory.extraScanDirs) {
                val f = File(extra)
                add(if (f.isAbsolute) f else File(root, extra))
            }
        }.distinctBy { it.canonicalPath }.filter { it.isDirectory }

        val commands = candidates.flatMap { dir ->
            dir.listFiles()
                ?.filter { it.isFile }
                ?.mapNotNull { file ->
                    val interpreter = interpreterFor(file.name) ?: return@mapNotNull null
                    val rel = root.toPath().relativize(file.toPath()).toString()
                    val label = if (rel.startsWith("..")) file.canonicalPath
                                else rel.replace(File.separatorChar, '/')
                    CommandDescriptor(
                        label            = label,
                        buildTool        = BuildTool.SCRIPT,
                        argv             = interpreter + listOf(file.canonicalPath),
                        workingDirectory = directory.path,
                    )
                }
                ?: emptyList()
        }

        if (commands.isEmpty()) return null

        return DetectedProject(
            directory  = directory,
            buildTools = setOf(BuildTool.SCRIPT),
            commands   = commands,
        )
    }

    private fun interpreterFor(filename: String): List<String>? {
        val ext = filename.substringAfterLast('.', "")
        return when (ext) {
            "sh", "bash" -> listOf("bash")
            "zsh"        -> listOf("zsh")
            "fish"       -> listOf("fish")
            "py"         -> listOf("python3")
            "rb"         -> listOf("ruby")
            "js"         -> listOf("node")
            "ts"         -> listOf("npx", "ts-node")
            "pl"         -> listOf("perl")
            "php"        -> listOf("php")
            "ps1"        -> listOf("pwsh")
            else         -> null
        }
    }
}
