package io.github.rygel.needlecast.ui

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path

class MakeRelativeIfPossibleTest {
    @Test
    fun `child of base is expressed relative to base`(
        @TempDir dir: Path,
    ) {
        val base = dir.toString()
        val child = dir.resolve("sub").toString()
        assertEquals("sub", makeRelativeIfPossible(child, base))
    }

    @Test
    fun `grandchild of base is expressed with nested relative path`(
        @TempDir dir: Path,
    ) {
        val base = dir.toString()
        val child = dir.resolve("sub/deeper").toString()
        assertEquals("sub/deeper", makeRelativeIfPossible(child, base))
    }

    @Test
    fun `sibling of base returns absolute path because relative would escape base`(
        @TempDir dir: Path,
    ) {
        val base = dir.resolve("a").toString()
        val sibling = dir.resolve("b").toString()
        assertEquals(sibling, makeRelativeIfPossible(sibling, base))
    }

    @Test
    fun `path above base returns absolute path`(
        @TempDir dir: Path,
    ) {
        val base = dir.resolve("a/b").toString()
        val outside = dir.resolve("c").toString()
        assertEquals(outside, makeRelativeIfPossible(outside, base))
    }

    @Test
    fun `base equals absolute returns empty relative path`(
        @TempDir dir: Path,
    ) {
        val same = dir.toString()
        assertEquals("", makeRelativeIfPossible(same, same))
    }

    @Test
    fun `separators are normalized to forward slashes on output`(
        @TempDir dir: Path,
    ) {
        val base = dir.toString()
        val child = dir.resolve("sub/dir/file.txt").toString()
        val result = makeRelativeIfPossible(child, base)
        assertEquals("sub/dir/file.txt", result)
        assert(!result.contains('\\')) { "expected forward slashes only, got: $result" }
    }
}
