package io.github.rygel.needlecast

import com.formdev.flatlaf.FlatDarkLaf
import com.formdev.flatlaf.FlatLightLaf
import com.formdev.flatlaf.IntelliJTheme
import com.formdev.flatlaf.intellijthemes.FlatArcIJTheme
import com.formdev.flatlaf.intellijthemes.FlatArcOrangeIJTheme
import com.formdev.flatlaf.intellijthemes.FlatCarbonIJTheme
import com.formdev.flatlaf.intellijthemes.FlatCobalt2IJTheme
import com.formdev.flatlaf.intellijthemes.FlatCyanLightIJTheme
import com.formdev.flatlaf.intellijthemes.FlatDarkPurpleIJTheme
import com.formdev.flatlaf.intellijthemes.FlatDraculaIJTheme
import com.formdev.flatlaf.intellijthemes.FlatGradiantoDeepOceanIJTheme
import com.formdev.flatlaf.intellijthemes.FlatGruvboxDarkHardIJTheme
import com.formdev.flatlaf.intellijthemes.FlatHiberbeeDarkIJTheme
import com.formdev.flatlaf.intellijthemes.FlatMonokaiProIJTheme
import com.formdev.flatlaf.intellijthemes.FlatNordIJTheme
import com.formdev.flatlaf.intellijthemes.FlatOneDarkIJTheme
import com.formdev.flatlaf.intellijthemes.FlatSolarizedDarkIJTheme
import com.formdev.flatlaf.intellijthemes.FlatSolarizedLightIJTheme
import com.formdev.flatlaf.intellijthemes.FlatSpacegrayIJTheme
import com.formdev.flatlaf.intellijthemes.FlatXcodeDarkIJTheme
import com.formdev.flatlaf.intellijthemes.materialthemeuilite.FlatMTAtomOneDarkIJTheme
import com.formdev.flatlaf.intellijthemes.materialthemeuilite.FlatMTAtomOneLightIJTheme
import com.formdev.flatlaf.intellijthemes.materialthemeuilite.FlatMTGitHubIJTheme
import com.formdev.flatlaf.intellijthemes.materialthemeuilite.FlatMTLightOwlIJTheme
import com.formdev.flatlaf.intellijthemes.materialthemeuilite.FlatMTMaterialDeepOceanIJTheme
import com.formdev.flatlaf.intellijthemes.materialthemeuilite.FlatMTMaterialOceanicIJTheme
import com.formdev.flatlaf.intellijthemes.materialthemeuilite.FlatMTMaterialPalenightIJTheme
import com.formdev.flatlaf.intellijthemes.materialthemeuilite.FlatMTMoonlightIJTheme
import com.formdev.flatlaf.intellijthemes.materialthemeuilite.FlatMTNightOwlIJTheme

data class ThemeEntry(
    val displayName: String,
    val dark: Boolean,
    val group: String,
    private val applyFn: () -> Unit,
) {
    fun applyTheme() = applyFn()
}

/**
 * Central registry of all available themes.
 *
 * Bundles:
 * - Base FlatLaf light/dark and system-default
 * - Dark and light themes from [flatlaf-intellij-themes]
 * - Catppuccin flavors (bundled `.theme.json` resources loaded via [IntelliJTheme])
 */
object ThemeRegistry {

    const val GROUP_BASE       = "Base"
    const val GROUP_DARK       = "Dark"
    const val GROUP_LIGHT      = "Light"
    const val GROUP_CATPPUCCIN = "Catppuccin"

    val themes: LinkedHashMap<String, ThemeEntry> = linkedMapOf(
        // ── Base ──────────────────────────────────────────────────────────
        "dark"  to ThemeEntry("Dark",  true,  GROUP_BASE) { FlatDarkLaf.setup()  },
        "light" to ThemeEntry("Light", false, GROUP_BASE) { FlatLightLaf.setup() },

        // ── Dark themes ───────────────────────────────────────────────────
        "dracula"            to ThemeEntry("Dracula",           true, GROUP_DARK) { FlatDraculaIJTheme.setup()            },
        "nord"               to ThemeEntry("Nord",              true, GROUP_DARK) { FlatNordIJTheme.setup()               },
        "one-dark"           to ThemeEntry("One Dark",          true, GROUP_DARK) { FlatOneDarkIJTheme.setup()            },
        "gruvbox-dark"       to ThemeEntry("Gruvbox Dark",      true, GROUP_DARK) { FlatGruvboxDarkHardIJTheme.setup()    },
        "monokai-pro"        to ThemeEntry("Monokai Pro",       true, GROUP_DARK) { FlatMonokaiProIJTheme.setup()         },
        "cobalt2"            to ThemeEntry("Cobalt 2",          true, GROUP_DARK) { FlatCobalt2IJTheme.setup()            },
        "carbon"             to ThemeEntry("Carbon",            true, GROUP_DARK) { FlatCarbonIJTheme.setup()             },
        "dark-purple"        to ThemeEntry("Dark Purple",       true, GROUP_DARK) { FlatDarkPurpleIJTheme.setup()         },
        "spacegray"          to ThemeEntry("Spacegray",         true, GROUP_DARK) { FlatSpacegrayIJTheme.setup()          },
        "hiberbee-dark"      to ThemeEntry("Hiberbee Dark",     true, GROUP_DARK) { FlatHiberbeeDarkIJTheme.setup()       },
        "solarized-dark"     to ThemeEntry("Solarized Dark",    true, GROUP_DARK) { FlatSolarizedDarkIJTheme.setup()      },
        "xcode-dark"         to ThemeEntry("Xcode Dark",        true, GROUP_DARK) { FlatXcodeDarkIJTheme.setup()          },
        "gradiant-deep-ocean" to ThemeEntry("Gradiant Deep Ocean", true, GROUP_DARK) { FlatGradiantoDeepOceanIJTheme.setup() },
        "atom-one-dark"      to ThemeEntry("Atom One Dark",     true, GROUP_DARK) { FlatMTAtomOneDarkIJTheme.setup()      },
        "moonlight"          to ThemeEntry("Moonlight",         true, GROUP_DARK) { FlatMTMoonlightIJTheme.setup()        },
        "night-owl"          to ThemeEntry("Night Owl",         true, GROUP_DARK) { FlatMTNightOwlIJTheme.setup()         },
        "material-deep-ocean" to ThemeEntry("Material Deep Ocean", true, GROUP_DARK) { FlatMTMaterialDeepOceanIJTheme.setup() },
        "material-oceanic"   to ThemeEntry("Material Oceanic",  true, GROUP_DARK) { FlatMTMaterialOceanicIJTheme.setup()  },
        "material-palenight" to ThemeEntry("Material Palenight",true, GROUP_DARK) { FlatMTMaterialPalenightIJTheme.setup()},

        // ── Light themes ──────────────────────────────────────────────────
        "arc"            to ThemeEntry("Arc",             false, GROUP_LIGHT) { FlatArcIJTheme.setup()             },
        "arc-orange"     to ThemeEntry("Arc Orange",      false, GROUP_LIGHT) { FlatArcOrangeIJTheme.setup()       },
        "cyan-light"     to ThemeEntry("Cyan Light",      false, GROUP_LIGHT) { FlatCyanLightIJTheme.setup()       },
        "solarized-light" to ThemeEntry("Solarized Light", false, GROUP_LIGHT) { FlatSolarizedLightIJTheme.setup() },
        "github-light"   to ThemeEntry("GitHub Light",    false, GROUP_LIGHT) { FlatMTGitHubIJTheme.setup()        },
        "atom-one-light" to ThemeEntry("Atom One Light",  false, GROUP_LIGHT) { FlatMTAtomOneLightIJTheme.setup()  },
        "light-owl"      to ThemeEntry("Light Owl",       false, GROUP_LIGHT) { FlatMTLightOwlIJTheme.setup()      },

        // ── Catppuccin ────────────────────────────────────────────────────
        "catppuccin-mocha"     to ThemeEntry("Mocha",     true,  GROUP_CATPPUCCIN) { applyJson("catppuccin-mocha")     },
        "catppuccin-macchiato" to ThemeEntry("Macchiato", true,  GROUP_CATPPUCCIN) { applyJson("catppuccin-macchiato") },
        "catppuccin-frappe"    to ThemeEntry("Frappe",    true,  GROUP_CATPPUCCIN) { applyJson("catppuccin-frappe")    },
        "catppuccin-latte"     to ThemeEntry("Latte",     false, GROUP_CATPPUCCIN) { applyJson("catppuccin-latte")     },
    )

    /**
     * Returns true if the given theme ID produces a dark appearance.
     * "system" is resolved dynamically via [isOsDark].
     */
    fun isDark(id: String): Boolean {
        if (id == "system") return isOsDark()
        return themes[id]?.dark ?: true
    }

    /**
     * Applies the theme with the given ID and returns whether it is dark.
     * Unknown IDs fall back to [FlatLightLaf].
     */
    fun apply(id: String): Boolean {
        if (id == "system") {
            val dark = isOsDark()
            if (dark) FlatDarkLaf.setup() else FlatLightLaf.setup()
            return dark
        }
        val entry = themes[id]
        if (entry != null) {
            entry.applyTheme()
            return entry.dark
        }
        // Unknown theme ID — fall back to dark (safe default)
        FlatDarkLaf.setup()
        return true
    }

    private fun applyJson(name: String) {
        val path = "/themes/$name.theme.json"
        val stream = ThemeRegistry::class.java.getResourceAsStream(path)
            ?: error("Theme resource not found: $path")
        stream.use { IntelliJTheme.setup(it) }
    }
}
