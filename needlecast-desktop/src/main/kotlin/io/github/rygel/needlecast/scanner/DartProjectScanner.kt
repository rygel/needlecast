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

        val buildTool = if (isFlutter) BuildTool.FLUTTER else BuildTool.PUB
        val commands = mutableListOf<CommandDescriptor>()

        if (isFlutter) {
            commands += cmd("flutter run", directory, buildTool, "flutter", "run")
            commands += cmd("flutter build apk", directory, buildTool, "flutter", "build", "apk")
            commands += cmd("flutter build ios", directory, buildTool, "flutter", "build", "ios")
            commands += cmd("flutter build web", directory, buildTool, "flutter", "build", "web")
            commands += cmd("flutter test", directory, buildTool, "flutter", "test")
            commands += cmd("flutter pub get", directory, buildTool, "flutter", "pub", "get")
            commands += cmd("flutter pub upgrade", directory, buildTool, "flutter", "pub", "upgrade")
            commands += cmd("flutter analyze", directory, buildTool, "flutter", "analyze")
            commands += cmd("flutter clean", directory, buildTool, "flutter", "clean")
        } else {
            commands += cmd("dart run", directory, buildTool, "dart", "run")
            commands += cmd("dart test", directory, buildTool, "dart", "test")
            commands += cmd("dart compile exe", directory, buildTool, "dart", "compile", "exe")
            commands += cmd("dart pub get", directory, buildTool, "dart", "pub", "get")
            commands += cmd("dart pub upgrade", directory, buildTool, "dart", "pub", "upgrade")
            commands += cmd("dart analyze", directory, buildTool, "dart", "analyze")
            commands += cmd("dart format .", directory, buildTool, "dart", "format", ".")
        }

        return DetectedProject(
            directory = directory,
            buildTools = setOf(buildTool),
            commands = commands,
        )
    }

    private fun cmd(label: String, dir: ProjectDirectory, tool: BuildTool, vararg args: String): CommandDescriptor =
        CommandDescriptor(label, tool,
            if (IS_WINDOWS) listOf("cmd", "/c") + args else args.toList(),
            dir.path)
}
