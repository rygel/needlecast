package io.github.quicklaunch.ui

import io.github.quicklaunch.scanner.IS_WINDOWS

data class AiCli(
    val name: String,
    val command: String,
    val description: String,
)

val KNOWN_AI_CLIS = listOf(
    AiCli("Claude Code",  "claude",      "Anthropic Claude Code"),
    AiCli("Copilot",      "copilot",     "GitHub Copilot CLI"),
    AiCli("Gemini CLI",   "gemini",      "Google Gemini CLI"),
    AiCli("Aider",        "aider",       "AI pair programming in your terminal"),
    AiCli("OpenAI Codex", "codex",       "OpenAI Codex CLI"),
    AiCli("OpenCode",     "opencode",    "OpenCode AI coding assistant"),
    AiCli("Junie",        "junie",       "JetBrains Junie AI assistant"),
    AiCli("Kilocode",     "kilocode",    "Kilocode AI coding assistant"),
    AiCli("Amazon Q",     "q",           "Amazon Q Developer CLI"),
    AiCli("Goose",        "goose",       "Block's AI developer agent"),
    AiCli("Plandex",      "plandex",     "AI coding engine for complex tasks"),
    AiCli("Amp",          "amp",         "Amp AI coding assistant"),
    AiCli("Cody",         "cody",        "Sourcegraph Cody CLI"),
    AiCli("GPT Engineer", "gpt-engineer","GPT Engineer"),
    AiCli("Mentat",       "mentat",      "Mentat AI coding assistant"),
    AiCli("Continue",     "continue",    "Continue dev CLI"),
)

fun detectAiClis(): List<Pair<AiCli, Boolean>> {
    return KNOWN_AI_CLIS.map { cli ->
        cli to isOnPath(cli.command)
    }
}

private fun isOnPath(command: String): Boolean {
    return try {
        val probe = if (IS_WINDOWS) listOf("where", command) else listOf("which", command)
        ProcessBuilder(probe)
            .redirectErrorStream(true)
            .start()
            .waitFor() == 0
    } catch (_: Exception) {
        false
    }
}
