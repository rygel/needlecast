package io.github.rygel.needlecast.ui

import io.github.rygel.needlecast.scanner.IS_WINDOWS
import io.github.rygel.needlecast.process.ProcessExecutor
import java.io.File

data class ShellInfo(val displayName: String, val command: String)

object ShellDetector {

    fun detect(): List<ShellInfo> = if (IS_WINDOWS) detectWindows() else detectUnix()

    private fun detectWindows(): List<ShellInfo> {
        val found = mutableListOf<ShellInfo>()

        // PowerShell 7+ (pwsh) — preferred modern shell
        if (onPath("pwsh")) found += ShellInfo("PowerShell 7+ (pwsh)", "pwsh")
        else findFile(
            "C:\\Program Files\\PowerShell",
            "pwsh.exe",
        )?.let { found += ShellInfo("PowerShell 7+ (pwsh)", it) }

        // Windows PowerShell (5.x)
        if (onPath("powershell")) found += ShellInfo("Windows PowerShell 5 (powershell)", "powershell")
        else {
            val fixed = "C:\\Windows\\System32\\WindowsPowerShell\\v1.0\\powershell.exe"
            if (File(fixed).exists()) found += ShellInfo("Windows PowerShell 5 (powershell)", fixed)
        }

        // Git Bash
        if (onPath("bash")) {
            found += ShellInfo("Git Bash (bash)", "bash")
        } else {
            listOf(
                "C:\\Program Files\\Git\\bin\\bash.exe",
                "C:\\Program Files (x86)\\Git\\bin\\bash.exe",
                "C:\\Git\\bin\\bash.exe",
            ).firstOrNull { File(it).exists() }
                ?.let { found += ShellInfo("Git Bash (bash)", it) }
        }

        // WSL
        val wslPath = "C:\\Windows\\System32\\wsl.exe"
        if (File(wslPath).exists()) found += ShellInfo("WSL (wsl)", "wsl")

        // cmd.exe — always present as a fallback
        found += ShellInfo("Command Prompt (cmd.exe)", "cmd.exe")

        return found
    }

    private fun detectUnix(): List<ShellInfo> {
        val etcShells = try {
            File("/etc/shells").readLines()
                .map { it.trim() }
                .filter { it.startsWith("/") && !it.startsWith("#") }
                .filter { File(it).canExecute() }
        } catch (_: Exception) { emptyList() }

        val knownShells = listOf(
            "/bin/bash"      to "Bash",
            "/usr/bin/bash"  to "Bash",
            "/bin/zsh"       to "Zsh",
            "/usr/bin/zsh"   to "Zsh",
            "/usr/bin/fish"  to "Fish",
            "/bin/sh"        to "sh",
            "/bin/ksh"       to "KornShell (ksh)",
            "/bin/tcsh"      to "tcsh",
            "/bin/csh"       to "csh",
        )

        val seen = mutableSetOf<String>()
        val found = mutableListOf<ShellInfo>()

        // Prefer /etc/shells list, matched against our known display names
        for (path in etcShells) {
            val display = knownShells.firstOrNull { (p, _) -> p == path }?.second
                ?: path.substringAfterLast('/')
            if (seen.add(path)) found += ShellInfo("$display ($path)", path)
        }

        // Add any known shells not already found via /etc/shells
        for ((path, display) in knownShells) {
            if (!seen.contains(path) && File(path).canExecute()) {
                seen.add(path)
                found += ShellInfo("$display ($path)", path)
            }
        }

        return found
    }

    private fun onPath(name: String): Boolean = ProcessExecutor.isOnPath(name)

    private fun findFile(dir: String, filename: String): String? =
        File(dir).walkTopDown()
            .maxDepth(2)
            .firstOrNull { it.name.equals(filename, ignoreCase = true) && it.isFile }
            ?.absolutePath
}
