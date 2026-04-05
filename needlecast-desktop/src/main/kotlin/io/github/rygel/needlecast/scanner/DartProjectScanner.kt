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

    override fun scan(directory: ProjectDirectory): DetectedProject? {
        val dir = Path.of(directory.path)
        val pubspec = dir.resolve("pubspec.yaml").toFile()
        if (!pubspec.exists()) return null

        val content = try { pubspec.readText(Charsets.UTF_8) } catch (_: Exception) { "" }
        val isFlutter = "flutter:" in content && "sdk: flutter" in content

        val commands = mutableListOf<CommandDescriptor>()

        if (isFlutter) {
            commands += cmd("flutter run", directory, "flutter", "run")
            commands += cmd("flutter build apk", directory, "flutter", "build", "apk")
            commands += cmd("flutter build ios", directory, "flutter", "build", "ios")
            commands += cmd("flutter build web", directory, "flutter", "build", "web")
            commands += cmd("flutter test", directory, "flutter", "test")
            commands += cmd("flutter pub get", directory, "flutter", "pub", "get")
            commands += cmd("flutter pub upgrade", directory, "flutter", "pub", "upgrade")
            commands += cmd("flutter analyze", directory, "flutter", "analyze")
            commands += cmd("flutter clean", directory, "flutter", "clean")
        } else {
            commands += cmd("dart run", directory, "dart", "run")
            commands += cmd("dart test", directory, "dart", "test")
            commands += cmd("dart compile exe", directory, "dart", "compile", "exe")
            commands += cmd("dart pub get", directory, "dart", "pub", "get")
            commands += cmd("dart pub upgrade", directory, "dart", "pub", "upgrade")
            commands += cmd("dart analyze", directory, "dart", "analyze")
            commands += cmd("dart format .", directory, "dart", "format", ".")
        }

        return DetectedProject(
            directory = directory,
            buildTools = setOf(BuildTool.DART),
            commands = commands,
        )
    }

    private fun cmd(label: String, dir: ProjectDirectory, vararg args: String): CommandDescriptor =
        CommandDescriptor(label, BuildTool.DART,
            if (IS_WINDOWS) listOf("cmd", "/c") + args else args.toList(),
            dir.path)
}
