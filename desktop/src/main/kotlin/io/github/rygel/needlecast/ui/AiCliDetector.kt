package io.github.rygel.needlecast.ui

import io.github.rygel.needlecast.process.ProcessExecutor

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
    AiCli("APM",          "apm",         "Microsoft Agent Package Manager — manages AI agent skills, prompts & MCP servers"),
)

fun detectAiClis(): List<Pair<AiCli, Boolean>> =
    KNOWN_AI_CLIS.map { cli -> cli to ProcessExecutor.isOnPath(cli.command) }
