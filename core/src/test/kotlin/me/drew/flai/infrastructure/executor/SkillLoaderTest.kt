package me.drew.flai.infrastructure.executor

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class SkillLoaderTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private lateinit var projectRoot: String
    private lateinit var loader: SkillLoader

    @Before
    fun setUp() {
        projectRoot = tempFolder.root.toPath().toRealPath().toString()
        loader = SkillLoader(projectRoot)
    }

    @Test
    fun `load returns empty list for empty skillPaths`() = runBlocking {
        val result = loader.load(emptyList())
        assertEquals(emptyList<String>(), result)
    }

    @Test
    fun `load returns body of a single file with absolute path`() = runBlocking {
        val file = tempFolder.newFile("skill.md")
        file.writeText("You are an expert.", Charsets.UTF_8)

        val result = loader.load(listOf(file.absolutePath))

        assertEquals(listOf("You are an expert."), result)
    }

    @Test
    fun `load returns body of a single file with relative path resolved from projectRoot`() = runBlocking {
        val file = File(projectRoot, "skill.md")
        file.writeText("Expert instructions.", Charsets.UTF_8)

        val result = loader.load(listOf("skill.md"))

        assertEquals(listOf("Expert instructions."), result)
    }

    @Test
    fun `load returns bodies of two files in declaration order`() = runBlocking {
        val file1 = tempFolder.newFile("skill1.md")
        file1.writeText("Skill one body.", Charsets.UTF_8)
        val file2 = tempFolder.newFile("skill2.md")
        file2.writeText("Skill two body.", Charsets.UTF_8)

        val result = loader.load(listOf(file1.absolutePath, file2.absolutePath))

        assertEquals(listOf("Skill one body.", "Skill two body."), result)
    }

    @Test
    fun `load fails with SkillLoadException when file does not exist and message contains resolved path`() = runBlocking {
        val missingPath = "$projectRoot/nonexistent-skill.md"

        val exception = try {
            loader.load(listOf(missingPath))
            null
        } catch (e: SkillLoadException) {
            e
        }

        assertTrue("Expected SkillLoadException", exception != null)
        assertTrue(
            "Message should contain the resolved path",
            exception!!.message!!.contains("nonexistent-skill.md")
        )
        assertTrue(
            "Message should indicate not found",
            exception.message!!.lowercase().contains("not found")
        )
    }

    @Test
    fun `load fails with SkillLoadException when path resolves to a directory and message is distinct from not-found`() = runBlocking {
        val dir = tempFolder.newFolder("skill-dir")

        val exception = try {
            loader.load(listOf(dir.absolutePath))
            null
        } catch (e: SkillLoadException) {
            e
        }

        assertTrue("Expected SkillLoadException", exception != null)
        assertTrue(
            "Message should contain the directory path",
            exception!!.message!!.contains(dir.name)
        )
        assertTrue(
            "Message should indicate directory (not 'not found')",
            exception.message!!.lowercase().contains("directory") || exception.message!!.lowercase().contains("not a regular file")
        )
    }

    @Test
    fun `load fails on second path after first loads successfully`() = runBlocking {
        val file1 = tempFolder.newFile("first.md")
        file1.writeText("First skill.", Charsets.UTF_8)
        val missingPath = "$projectRoot/missing.md"

        val exception = try {
            loader.load(listOf(file1.absolutePath, missingPath))
            null
        } catch (e: SkillLoadException) {
            e
        }

        assertTrue("Expected SkillLoadException for the second path", exception != null)
        assertTrue(
            "Message should contain the missing path",
            exception!!.message!!.contains("missing.md")
        )
    }

    @Test
    fun `load returns empty string for a file containing only frontmatter`() = runBlocking {
        val file = tempFolder.newFile("frontmatter-only.md")
        file.writeText("---\nname: Only frontmatter\n---\n", Charsets.UTF_8)

        val result = loader.load(listOf(file.absolutePath))

        assertEquals(listOf(""), result)
    }

    // stripFrontmatter tests

    @Test
    fun `stripFrontmatter returns full content when no frontmatter`() {
        val content = "No frontmatter here.\nJust body."
        assertEquals(content, SkillLoader.stripFrontmatter(content))
    }

    @Test
    fun `stripFrontmatter strips frontmatter delimiters and YAML content returns body only`() {
        val content = "---\nname: Test Skill\ndescription: A description\n---\nThis is the body."
        assertEquals("This is the body.", SkillLoader.stripFrontmatter(content))
    }

    @Test
    fun `stripFrontmatter returns full content when opening dash has no closing dash (FR-17)`() {
        val content = "---\nname: No closing delimiter\nThis is treated as body."
        assertEquals(content, SkillLoader.stripFrontmatter(content))
    }

    @Test
    fun `stripFrontmatter returns empty string for frontmatter-only file`() {
        val content = "---\nname: Only frontmatter\n---\n"
        assertEquals("", SkillLoader.stripFrontmatter(content))
    }

    @Test
    fun `stripFrontmatter handles Windows-style CRLF line endings in delimiter lines`() {
        val content = "---\r\nname: Windows\r\n---\r\nBody text."
        assertEquals("Body text.", SkillLoader.stripFrontmatter(content))
    }
}
