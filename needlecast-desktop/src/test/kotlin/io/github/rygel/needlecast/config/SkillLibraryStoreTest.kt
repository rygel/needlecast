package io.github.rygel.needlecast.config

import io.github.rygel.needlecast.model.SkillEntry
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path

class SkillLibraryStoreTest {

    @Test
    fun `loadLibrary reads skill directories`(@TempDir base: Path) {
        val skillsDir = base.resolve("skills")
        val skillDir = skillsDir.resolve("kotlin-java")
        Files.createDirectories(skillDir)
        Files.writeString(skillDir.resolve("SKILL.md"), """
            ---
            name: kotlin-java
            description: Use when writing Kotlin/Java code.
            ---
            # Kotlin & Java
            
            Follow existing patterns.
        """.trimIndent())

        val store = SkillLibraryStore(skillsDir)
        val skills = store.loadLibrary()

        assertEquals(1, skills.size)
        assertEquals("kotlin-java", skills[0].name)
        assertEquals("Use when writing Kotlin/Java code.", skills[0].description)
        assertEquals(skillDir, skills[0].skillDir)
    }

    @Test
    fun `loadLibrary returns empty when dir does not exist`(@TempDir base: Path) {
        val store = SkillLibraryStore(base.resolve("nonexistent"))
        assertTrue(store.loadLibrary().isEmpty())
    }

    @Test
    fun `loadLibrary skips dirs without SKILL md`(@TempDir base: Path) {
        val skillsDir = base.resolve("skills")
        Files.createDirectories(skillsDir.resolve("no-skill-file"))
        Files.createDirectories(skillsDir.resolve("has-skill"))
        Files.writeString(skillsDir.resolve("has-skill/SKILL.md"), """
            ---
            name: has-skill
            description: A skill.
            ---
            Body.
        """.trimIndent())

        val store = SkillLibraryStore(skillsDir)
        val skills = store.loadLibrary()
        assertEquals(1, skills.size)
        assertEquals("has-skill", skills[0].name)
    }

    @Test
    fun `save creates skill directory and SKILL md`(@TempDir base: Path) {
        val skillsDir = base.resolve("skills")
        val store = SkillLibraryStore(skillsDir)
        val entry = SkillEntry(
            name = "cicd",
            description = "Configure CI/CD pipelines.",
            skillDir = skillsDir.resolve("cicd"),
        )
        store.save(entry, "CI/CD instructions here.")

        val file = skillsDir.resolve("cicd/SKILL.md")
        assertTrue(Files.exists(file))
        val content = Files.readString(file)
        assertTrue(content.contains("name: cicd"))
        assertTrue(content.contains("description: Configure CI/CD pipelines."))
        assertTrue(content.contains("CI/CD instructions here."))
    }

    @Test
    fun `delete removes entire skill directory`(@TempDir base: Path) {
        val skillsDir = base.resolve("skills")
        val skillDir = skillsDir.resolve("to-delete")
        Files.createDirectories(skillDir)
        Files.writeString(skillDir.resolve("SKILL.md"), "---\nname: to-delete\ndescription: x\n---\nbody")
        Files.writeString(skillDir.resolve("extra.txt"), "extra")

        val store = SkillLibraryStore(skillsDir)
        store.delete("to-delete")

        assertFalse(Files.exists(skillDir))
    }

    @Test
    fun `deploy creates symlink and undeploy removes it`(@TempDir base: Path) {
        val skillsDir = base.resolve("skills")
        val skillDir = skillsDir.resolve("my-skill")
        Files.createDirectories(skillDir)
        Files.writeString(skillDir.resolve("SKILL.md"), "---\nname: my-skill\ndescription: d\n---\nb")

        val projectDir = base.resolve("project")
        val targetDir = projectDir.resolve(".claude/skills")
        Files.createDirectories(projectDir)

        val store = SkillLibraryStore(skillsDir)
        store.deploy("my-skill", projectDir.toString(), ".claude/skills")

        val link = targetDir.resolve("my-skill")
        assertTrue(Files.exists(link))
        assertTrue(store.isDeployed("my-skill", projectDir.toString(), ".claude/skills"))

        store.undeploy("my-skill", projectDir.toString(), ".claude/skills")
        assertFalse(Files.exists(link))
        assertFalse(store.isDeployed("my-skill", projectDir.toString(), ".claude/skills"))
    }

    @Test
    fun `deployedSkills lists only links pointing to skills dir`(@TempDir base: Path) {
        val skillsDir = base.resolve("skills")
        val skillA = skillsDir.resolve("skill-a")
        val skillB = skillsDir.resolve("skill-b")
        Files.createDirectories(skillA)
        Files.createDirectories(skillB)
        Files.writeString(skillA.resolve("SKILL.md"), "---\nname: skill-a\ndescription: a\n---\nb")
        Files.writeString(skillB.resolve("SKILL.md"), "---\nname: skill-b\ndescription: b\n---\nb")

        val projectDir = base.resolve("project")
        Files.createDirectories(projectDir)

        val store = SkillLibraryStore(skillsDir)
        store.deploy("skill-a", projectDir.toString(), ".claude/skills")
        store.deploy("skill-b", projectDir.toString(), ".claude/skills")

        val deployed = store.deployedSkills(projectDir.toString(), ".claude/skills")
        assertEquals(listOf("skill-a", "skill-b"), deployed.sorted())
    }

    @Test
    fun `deploy is idempotent`(@TempDir base: Path) {
        val skillsDir = base.resolve("skills")
        val skillDir = skillsDir.resolve("s")
        Files.createDirectories(skillDir)
        Files.writeString(skillDir.resolve("SKILL.md"), "---\nname: s\ndescription: d\n---\nb")

        val projectDir = base.resolve("project")
        Files.createDirectories(projectDir)

        val store = SkillLibraryStore(skillsDir)
        store.deploy("s", projectDir.toString(), ".claude/skills")
        store.deploy("s", projectDir.toString(), ".claude/skills")

        val link = projectDir.resolve(".claude/skills/s")
        assertTrue(Files.exists(link))
    }
}
