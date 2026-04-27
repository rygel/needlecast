package io.github.rygel.needlecast.ui.terminal

data class ClaudeUsageData(
    val fiveHourPercent: Double?,
    val fiveHourResetsAt: String?,
    val sevenDayPercent: Double?,
    val sevenDayResetsAt: String?,
    val sevenDaySonnetPercent: Double?,
    val sevenDayOpusPercent: Double?,
)
