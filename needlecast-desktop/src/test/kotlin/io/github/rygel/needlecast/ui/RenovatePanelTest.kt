package io.github.rygel.needlecast.ui

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.nio.file.Path

class RenovatePanelTest {
    @TempDir
    lateinit var tmpDir: Path

    private fun writeReport(json: String): File {
        val file = tmpDir.resolve("renovate-report.json").toFile()
        file.writeText(json)
        return file
    }

    @Test
    fun `parseRenovateReport returns empty list for missing packageFiles`() {
        val report = writeReport("""{"repositories":{"local":{}}}""")
        assertTrue(parseRenovateReport(report).isEmpty())
    }

    @Test
    fun `parseRenovateReport returns empty list for empty report`() {
        val report = writeReport("""{}""")
        assertTrue(parseRenovateReport(report).isEmpty())
    }

    @Test
    fun `parseRenovateReport parses single maven dependency`() {
        val json = """
        {
          "repositories": {
            "local": {
              "packageFiles": {
                "maven": [
                  {
                    "packageFile": "pom.xml",
                    "deps": [
                      {
                        "depName": "com.google.guava:guava",
                        "currentValue": "32.1.2-jre",
                        "updates": [
                          { "newValue": "33.0.0-jre", "updateType": "major" }
                        ]
                      }
                    ]
                  }
                ]
              }
            }
          }
        }
        """
        val result = parseRenovateReport(writeReport(json))
        assertEquals(1, result.size)
        assertEquals("maven", result[0].manager)
        assertEquals("pom.xml", result[0].packageFile)
        assertEquals("com.google.guava:guava", result[0].depName)
        assertEquals("32.1.2-jre", result[0].currentValue)
        assertEquals("33.0.0-jre", result[0].newValue)
        assertEquals("major", result[0].updateType)
    }

    @Test
    fun `parseRenovateReport falls back to currentVersion when currentValue is absent`() {
        val json = """
        {
          "repositories": {
            "local": {
              "packageFiles": {
                "npm": [
                  {
                    "packageFile": "package.json",
                    "deps": [
                      {
                        "depName": "lodash",
                        "currentVersion": "4.17.20",
                        "updates": [
                          { "newVersion": "4.17.21", "updateType": "patch" }
                        ]
                      }
                    ]
                  }
                ]
              }
            }
          }
        }
        """
        val result = parseRenovateReport(writeReport(json))
        assertEquals(1, result.size)
        assertEquals("4.17.20", result[0].currentValue)
        assertEquals("4.17.21", result[0].newValue)
    }

    @Test
    fun `parseRenovateReport sorts by update type then dep name`() {
        val json = """
        {
          "repositories": {
            "local": {
              "packageFiles": {
                "maven": [
                  {
                    "packageFile": "pom.xml",
                    "deps": [
                      {
                        "depName": "org.example:zeta",
                        "currentValue": "1.0",
                        "updates": [{ "newValue": "1.1", "updateType": "minor" }]
                      },
                      {
                        "depName": "org.example:alpha",
                        "currentValue": "2.0",
                        "updates": [{ "newValue": "3.0", "updateType": "major" }]
                      },
                      {
                        "depName": "org.example:beta",
                        "currentValue": "1.0",
                        "updates": [{ "newValue": "1.0.1", "updateType": "patch" }]
                      }
                    ]
                  }
                ]
              }
            }
          }
        }
        """
        val result = parseRenovateReport(writeReport(json))
        assertEquals(3, result.size)
        assertEquals("major", result[0].updateType)
        assertEquals("org.example:alpha", result[0].depName)
        assertEquals("minor", result[1].updateType)
        assertEquals("org.example:zeta", result[1].depName)
        assertEquals("patch", result[2].updateType)
    }

    @Test
    fun `parseRenovateReport handles multiple updates per dependency`() {
        val json = """
        {
          "repositories": {
            "local": {
              "packageFiles": {
                "maven": [
                  {
                    "packageFile": "pom.xml",
                    "deps": [
                      {
                        "depName": "junit:junit",
                        "currentValue": "4.12",
                        "updates": [
                          { "newValue": "4.13.2", "updateType": "patch" },
                          { "newValue": "5.0.0", "updateType": "major" }
                        ]
                      }
                    ]
                  }
                ]
              }
            }
          }
        }
        """
        val result = parseRenovateReport(writeReport(json))
        assertEquals(2, result.size)
        assertEquals("major", result[0].updateType)
        assertEquals("5.0.0", result[0].newValue)
        assertEquals("patch", result[1].updateType)
        assertEquals("4.13.2", result[1].newValue)
    }

    @Test
    fun `parseRenovateReport reads sharedVariableName and fileReplacePosition`() {
        val json = """
        {
          "repositories": {
            "local": {
              "packageFiles": {
                "maven": [
                  {
                    "packageFile": "pom.xml",
                    "deps": [
                      {
                        "depName": "com.fasterxml.jackson.core:jackson-databind",
                        "currentValue": "2.17.2",
                        "sharedVariableName": "jackson.version",
                        "fileReplacePosition": 1234,
                        "replaceString": "2.17.2",
                        "autoReplaceStringTemplate": "{{depName}}:{{newValue}}",
                        "updates": [
                          { "newValue": "2.18.0", "updateType": "minor", "newDigest": "sha256:abc123" }
                        ]
                      }
                    ]
                  }
                ]
              }
            }
          }
        }
        """
        val result = parseRenovateReport(writeReport(json))
        assertEquals(1, result.size)
        assertEquals("jackson.version", result[0].sharedVariableName)
        assertEquals(1234, result[0].fileReplacePosition)
        assertEquals("2.17.2", result[0].replaceString)
        assertEquals("{{depName}}:{{newValue}}", result[0].autoReplaceTemplate)
        assertEquals("sha256:abc123", result[0].newDigest)
    }

    @Test
    fun `parseRenovateReport skips deps with no updates`() {
        val json = """
        {
          "repositories": {
            "local": {
              "packageFiles": {
                "maven": [
                  {
                    "packageFile": "pom.xml",
                    "deps": [
                      {
                        "depName": "up-to-date-dep",
                        "currentValue": "1.0",
                        "updates": []
                      }
                    ]
                  }
                ]
              }
            }
          }
        }
        """
        val result = parseRenovateReport(writeReport(json))
        assertTrue(result.isEmpty())
    }

    @Test
    fun `parseRenovateReport handles multiple managers`() {
        val json = """
        {
          "repositories": {
            "local": {
              "packageFiles": {
                "maven": [
                  {
                    "packageFile": "pom.xml",
                    "deps": [
                      {
                        "depName": "org.example:lib",
                        "currentValue": "1.0",
                        "updates": [{ "newValue": "2.0", "updateType": "major" }]
                      }
                    ]
                  }
                ],
                "npm": [
                  {
                    "packageFile": "package.json",
                    "deps": [
                      {
                        "depName": "express",
                        "currentValue": "4.18.0",
                        "updates": [{ "newValue": "4.19.0", "updateType": "minor" }]
                      }
                    ]
                  }
                ]
              }
            }
          }
        }
        """
        val result = parseRenovateReport(writeReport(json))
        assertEquals(2, result.size)
        val managers = result.map { it.manager }.toSet()
        assertTrue(managers.contains("maven"))
        assertTrue(managers.contains("npm"))
    }

    @Test
    fun `buildRenovateReplacements for shared Maven property deduplicates`() {
        val updates =
            listOf(
                RenovateDepUpdate(
                    manager = "maven",
                    packageFile = "pom.xml",
                    depName = "com.fasterxml.jackson.core:jackson-databind",
                    currentValue = "2.17.2",
                    newValue = "2.18.0",
                    updateType = "minor",
                    sharedVariableName = "jackson.version",
                    fileReplacePosition = -1,
                    replaceString = "",
                    autoReplaceTemplate = "",
                    newDigest = "",
                ),
                RenovateDepUpdate(
                    manager = "maven",
                    packageFile = "pom.xml",
                    depName = "com.fasterxml.jackson.core:jackson-core",
                    currentValue = "2.17.2",
                    newValue = "2.18.0",
                    updateType = "minor",
                    sharedVariableName = "jackson.version",
                    fileReplacePosition = -1,
                    replaceString = "",
                    autoReplaceTemplate = "",
                    newDigest = "",
                ),
            )
        val result = buildRenovateReplacements("/project", updates)
        assertEquals(1, result.size)
        val replacements = result.values.first()
        assertEquals(1, replacements.size)
        assertEquals(">2.17.2</jackson.version>" to ">2.18.0</jackson.version>", replacements[0])
    }

    @Test
    fun `buildRenovateReplacements for Dockerfile with digest pinning`() {
        val update =
            RenovateDepUpdate(
                manager = "dockerfile",
                packageFile = "Dockerfile",
                depName = "maven",
                currentValue = "3.9-temurin",
                newValue = "3.9-eclipse-temurin",
                updateType = "patch",
                sharedVariableName = "",
                fileReplacePosition = -1,
                replaceString = "maven:3.9-temurin",
                autoReplaceTemplate = "{{depName}}:{{newValue}}@{{newDigest}}",
                newDigest = "sha256:abcd1234",
            )
        val result = buildRenovateReplacements("/project", listOf(update))
        val replacements = result.values.first()
        assertEquals(1, replacements.size)
        assertEquals("maven:3.9-temurin" to "maven:3.9-eclipse-temurin@sha256:abcd1234", replacements[0])
    }

    @Test
    fun `buildRenovateReplacements for Dockerfile without digest does simple substitution`() {
        val update =
            RenovateDepUpdate(
                manager = "dockerfile",
                packageFile = "Dockerfile",
                depName = "node",
                currentValue = "18-alpine",
                newValue = "20-alpine",
                updateType = "major",
                sharedVariableName = "",
                fileReplacePosition = -1,
                replaceString = "node:18-alpine",
                autoReplaceTemplate = "",
                newDigest = "",
            )
        val result = buildRenovateReplacements("/project", listOf(update))
        val replacements = result.values.first()
        assertEquals("node:18-alpine" to "node:20-alpine", replacements[0])
    }

    @Test
    fun `buildRenovateReplacements skips Dockerfile update when newValue is question mark`() {
        val update =
            RenovateDepUpdate(
                manager = "dockerfile",
                packageFile = "Dockerfile",
                depName = "mystery",
                currentValue = "1.0",
                newValue = "?",
                updateType = "unknown",
                sharedVariableName = "",
                fileReplacePosition = -1,
                replaceString = "mystery:1.0",
                autoReplaceTemplate = "",
                newDigest = "",
            )
        val result = buildRenovateReplacements("/project", listOf(update))
        assertTrue(result.isEmpty())
    }

    @Test
    fun `buildRenovateReplacements for direct version replacement`() {
        val update =
            RenovateDepUpdate(
                manager = "maven",
                packageFile = "pom.xml",
                depName = "org.example:lib",
                currentValue = "1.0.0",
                newValue = "2.0.0",
                updateType = "major",
                sharedVariableName = "",
                fileReplacePosition = -1,
                replaceString = "",
                autoReplaceTemplate = "",
                newDigest = "",
            )
        val result = buildRenovateReplacements("/project", listOf(update))
        val replacements = result.values.first()
        assertEquals(">1.0.0<" to ">2.0.0<", replacements[0])
    }

    @Test
    fun `buildRenovateReplacements skips when newValue is unknown`() {
        val update =
            RenovateDepUpdate(
                manager = "maven",
                packageFile = "pom.xml",
                depName = "org.example:lib",
                currentValue = "1.0.0",
                newValue = "?",
                updateType = "unknown",
                sharedVariableName = "",
                fileReplacePosition = -1,
                replaceString = "",
                autoReplaceTemplate = "",
                newDigest = "",
            )
        val result = buildRenovateReplacements("/project", listOf(update))
        assertTrue(result.isEmpty())
    }

    @Test
    fun `buildRenovateReplacements groups by file path`() {
        val updates =
            listOf(
                RenovateDepUpdate(
                    manager = "maven",
                    packageFile = "pom.xml",
                    depName = "org.example:a",
                    currentValue = "1.0",
                    newValue = "2.0",
                    updateType = "major",
                    sharedVariableName = "",
                    fileReplacePosition = -1,
                    replaceString = "",
                    autoReplaceTemplate = "",
                    newDigest = "",
                ),
                RenovateDepUpdate(
                    manager = "maven",
                    packageFile = "pom.xml",
                    depName = "org.example:b",
                    currentValue = "3.0",
                    newValue = "4.0",
                    updateType = "major",
                    sharedVariableName = "",
                    fileReplacePosition = -1,
                    replaceString = "",
                    autoReplaceTemplate = "",
                    newDigest = "",
                ),
            )
        val result = buildRenovateReplacements("/project", updates)
        assertEquals(1, result.size)
        assertEquals(2, result.values.first().size)
    }

    @Test
    fun `buildRenovateReplacements skips Dockerfile when same version`() {
        val update =
            RenovateDepUpdate(
                manager = "dockerfile",
                packageFile = "Dockerfile",
                depName = "node",
                currentValue = "18",
                newValue = "18",
                updateType = "pin",
                sharedVariableName = "",
                fileReplacePosition = -1,
                replaceString = "node:18",
                autoReplaceTemplate = "",
                newDigest = "",
            )
        val result = buildRenovateReplacements("/project", listOf(update))
        assertTrue(result.isEmpty())
    }
}
