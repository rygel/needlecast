package io.github.rygel.needlecast.service

import io.github.rygel.needlecast.model.BuildTool
import io.github.rygel.needlecast.model.DocCategory
import io.github.rygel.needlecast.model.DocTarget

object DocRegistry {

    private val all: List<DocTarget> = listOf(
        // ── Maven ────────────────────────────────────────────────────────────
        DocTarget("Javadoc",        "target/site/apidocs/index.html",     BuildTool.MAVEN, DocCategory.API_DOCS,      "mvn javadoc:javadoc"),
        DocTarget("Test Javadoc",   "target/site/testapidocs/index.html", BuildTool.MAVEN, DocCategory.API_DOCS,      "mvn javadoc:test-javadoc"),
        DocTarget("Surefire Report","target/site/surefire-report.html",   BuildTool.MAVEN, DocCategory.TEST_REPORTS,  "mvn surefire-report:report"),
        DocTarget("JaCoCo Coverage","target/site/jacoco/index.html",      BuildTool.MAVEN, DocCategory.COVERAGE,      "mvn jacoco:report"),
        DocTarget("Maven Site",     "target/site/index.html",             BuildTool.MAVEN, DocCategory.SITE,          "mvn site"),

        // ── Gradle ───────────────────────────────────────────────────────────
        DocTarget("Javadoc",        "build/docs/javadoc/index.html",                  BuildTool.GRADLE, DocCategory.API_DOCS,     "./gradlew javadoc"),
        DocTarget("Groovydoc",      "build/docs/groovydoc/index.html",                BuildTool.GRADLE, DocCategory.API_DOCS,     "./gradlew groovydoc"),
        DocTarget("Dokka HTML",     "build/docs/dokka/html/index.html",               BuildTool.GRADLE, DocCategory.API_DOCS,     "./gradlew dokkaHtml"),
        DocTarget("Dokka Javadoc",  "build/docs/dokka/javadoc/index.html",            BuildTool.GRADLE, DocCategory.API_DOCS,     "./gradlew dokkaJavadoc"),
        DocTarget("JaCoCo Coverage","build/reports/jacoco/test/html/index.html",      BuildTool.GRADLE, DocCategory.COVERAGE,     "./gradlew jacocoTestReport"),
        DocTarget("Test Results",   "build/reports/tests/test/index.html",            BuildTool.GRADLE, DocCategory.TEST_REPORTS, "./gradlew test"),

        // ── npm / Node ───────────────────────────────────────────────────────
        DocTarget("TypeDoc",          "docs/index.html",          BuildTool.NPM, DocCategory.API_DOCS, "npx typedoc"),  // same path as DocC; no collision unless project has both NPM and SPM
        DocTarget("JSDoc",            "out/index.html",           BuildTool.NPM, DocCategory.API_DOCS, "npx jsdoc"),
        DocTarget("JSDoc (alt)",      "jsdoc/index.html",         BuildTool.NPM, DocCategory.API_DOCS, "npx jsdoc"),
        DocTarget("documentation.js", "documentation/index.html", BuildTool.NPM, DocCategory.API_DOCS, "npx documentation build"),

        // ── Rust ─────────────────────────────────────────────────────────────
        DocTarget("rustdoc", "target/doc/index.html", BuildTool.CARGO, DocCategory.API_DOCS, "cargo doc"),

        // ── Python (UV / Poetry / pip) ───────────────────────────────────────
        DocTarget("Sphinx", "docs/_build/html/index.html", BuildTool.UV,     DocCategory.API_DOCS, "make -C docs html"),
        DocTarget("MkDocs", "site/index.html",             BuildTool.UV,     DocCategory.SITE,     "mkdocs build"),
        DocTarget("pdoc",   "html/index.html",             BuildTool.UV,     DocCategory.API_DOCS, "pdoc --html ."),  // best-effort; actual output is html/<package>/
        DocTarget("Sphinx", "docs/_build/html/index.html", BuildTool.POETRY, DocCategory.API_DOCS, "make -C docs html"),
        DocTarget("MkDocs", "site/index.html",             BuildTool.POETRY, DocCategory.SITE,     "mkdocs build"),
        DocTarget("pdoc",   "html/index.html",             BuildTool.POETRY, DocCategory.API_DOCS, "pdoc --html ."),  // best-effort; actual output is html/<package>/
        DocTarget("Sphinx", "docs/_build/html/index.html", BuildTool.PIP,    DocCategory.API_DOCS, "make -C docs html"),
        DocTarget("MkDocs", "site/index.html",             BuildTool.PIP,    DocCategory.SITE,     "mkdocs build"),
        DocTarget("pdoc",   "html/index.html",             BuildTool.PIP,    DocCategory.API_DOCS, "pdoc --html ."),  // best-effort; actual output is html/<package>/

        // ── Elixir ───────────────────────────────────────────────────────────
        DocTarget("ExDoc", "doc/index.html", BuildTool.MIX, DocCategory.API_DOCS, "mix docs"),  // same path as YARD; no collision unless project has both Mix and Bundler

        // ── Scala ────────────────────────────────────────────────────────────
        DocTarget("Scaladoc (2.x)", "target/scala-2.13/api/index.html", BuildTool.SBT, DocCategory.API_DOCS, "sbt doc"),
        DocTarget("Scaladoc (3.x)", "target/scala-3/api/index.html",    BuildTool.SBT, DocCategory.API_DOCS, "sbt doc"),

        // ── Ruby ─────────────────────────────────────────────────────────────
        DocTarget("YARD", "doc/index.html",   BuildTool.BUNDLER, DocCategory.API_DOCS, "yard doc"),  // same path as ExDoc; no collision unless project has both Bundler and Mix
        DocTarget("RDoc", "rdoc/index.html",  BuildTool.BUNDLER, DocCategory.API_DOCS, "rdoc"),

        // ── PHP ──────────────────────────────────────────────────────────────
        DocTarget("phpDocumentor", "docs/api/index.html", BuildTool.COMPOSER, DocCategory.API_DOCS, "phpdoc"),

        // ── Dart / Flutter ───────────────────────────────────────────────────
        DocTarget("dartdoc", "doc/api/index.html", BuildTool.PUB,     DocCategory.API_DOCS, "dart doc"),
        DocTarget("dartdoc", "doc/api/index.html", BuildTool.FLUTTER, DocCategory.API_DOCS, "dart doc"),

        // ── Swift ────────────────────────────────────────────────────────────
        DocTarget("DocC", "docs/index.html", BuildTool.SPM, DocCategory.API_DOCS, "swift package generate-documentation"),  // same path as TypeDoc; no collision unless project has both SPM and NPM

        // ── C / C++ ──────────────────────────────────────────────────────────
        DocTarget("Doxygen",         "docs/html/index.html",       BuildTool.CMAKE, DocCategory.API_DOCS, "doxygen"),
        DocTarget("Doxygen (build/)", "build/docs/html/index.html", BuildTool.CMAKE, DocCategory.API_DOCS, "doxygen"),
        DocTarget("Doxygen",         "docs/html/index.html",       BuildTool.MAKE,  DocCategory.API_DOCS, "doxygen"),
        DocTarget("Doxygen (build/)", "build/docs/html/index.html", BuildTool.MAKE,  DocCategory.API_DOCS, "doxygen"),

        // ── .NET ─────────────────────────────────────────────────────────────
        DocTarget("DocFX", "_site/index.html", BuildTool.DOTNET, DocCategory.SITE, "docfx build"),
    )

    fun targetsFor(buildTools: Set<BuildTool>): List<DocTarget> =
        all.filter { it.buildTool in buildTools }
}
