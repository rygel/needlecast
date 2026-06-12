package io.github.rygel.needlecast.scanner

import io.github.rygel.needlecast.model.BuildTool
import io.github.rygel.needlecast.model.CommandDescriptor
import io.github.rygel.needlecast.model.DetectedProject
import io.github.rygel.needlecast.model.ProjectDirectory
import java.nio.file.Path

/**
 * Detects Dart/Flutter projects via `pubspec.yaml`.
 *
 * Distinguishes Flutter from pure Dart by checking for the `flutter`
 * dependency in pubspec.yaml.
 */
class DartProjectScanner : ProjectScanner {
    private val logger = org.slf4j.LoggerFactory.getLogger(DartProjectScanner::class.java)

    override fun scan(directory: ProjectDirectory): DetectedProject? {
        val dir = Path.of(directory.path)
        val pubspec = dir.resolve("pubspec.yaml").toFile()
        if (!pubspec.exists()) return null

        val content =
            try {
                pubspec.readText(Charsets.UTF_8)
            } catch (e: Exception) {
                logger.warn("Failed to read {}", pubspec.name, e)
                ""
            }
        val isFlutter = "flutter:" in content && "sdk: flutter" in content

        val buildTool = if (isFlutter) BuildTool.FLUTTER else BuildTool.PUB
        val commands = mutableListOf<CommandDescriptor>()

        if (isFlutter) {
            commands += scannerCmd("flutter run", directory, buildTool, "flutter", "run")
            commands += scannerCmd("flutter build apk", directory, buildTool, "flutter", "build", "apk")
            commands += scannerCmd("flutter build ios", directory, buildTool, "flutter", "build", "ios")
            commands += scannerCmd("flutter build web", directory, buildTool, "flutter", "build", "web")
            commands += scannerCmd("flutter test", directory, buildTool, "flutter", "test")
            commands += scannerCmd("flutter pub get", directory, buildTool, "flutter", "pub", "get")
            commands += scannerCmd("flutter pub upgrade", directory, buildTool, "flutter", "pub", "upgrade")
            commands += scannerCmd("flutter analyze", directory, buildTool, "flutter", "analyze")
            commands += scannerCmd("flutter clean", directory, buildTool, "flutter", "clean")
        } else {
            commands += scannerCmd("dart run", directory, buildTool, "dart", "run")
            commands += scannerCmd("dart test", directory, buildTool, "dart", "test")
            commands += scannerCmd("dart compile exe", directory, buildTool, "dart", "compile", "exe")
            commands += scannerCmd("dart pub get", directory, buildTool, "dart", "pub", "get")
            commands += scannerCmd("dart pub upgrade", directory, buildTool, "dart", "pub", "upgrade")
            commands += scannerCmd("dart analyze", directory, buildTool, "dart", "analyze")
            commands += scannerCmd("dart format .", directory, buildTool, "dart", "format", ".")
        }

        return DetectedProject(
            directory = directory,
            buildTools = setOf(buildTool),
            commands = commands,
        )
    }
}
