package me.drew.flai.infrastructure.executor

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException

open class SkillLoader(private val projectRoot: String) {

    open suspend fun load(skillPaths: List<String>): List<String> {
        if (skillPaths.isEmpty()) {
            return emptyList()
        }
        return withContext(Dispatchers.IO) {
            skillPaths.map { path ->
                val file = if (File(path).isAbsolute) File(path) else File(projectRoot, path)
                val resolvedPath = file.absolutePath
                try {
                    if (!file.exists()) {
                        throw SkillLoadException("Skill file not found: $resolvedPath")
                    }
                    if (!file.isFile) {
                        throw SkillLoadException("Skill path is not a regular file (may be a directory): $resolvedPath")
                    }
                    val content = file.readText(Charsets.UTF_8)
                    stripFrontmatter(content)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: SkillLoadException) {
                    throw e
                } catch (e: IOException) {
                    throw SkillLoadException("Cannot read skill file $resolvedPath: ${e.message}", e)
                } catch (e: Exception) {
                    throw SkillLoadException("Cannot read skill file $resolvedPath: ${e.message}", e)
                }
            }
        }
    }

    companion object {
        private val FRONTMATTER_REGEX = Regex(
            """^---\r?\n(.*?\r?\n)?---\r?\n?""",
            setOf(RegexOption.DOT_MATCHES_ALL)
        )

        internal fun stripFrontmatter(content: String): String {
            val match = FRONTMATTER_REGEX.find(content) ?: return content
            return content.substring(match.range.last + 1)
        }
    }
}

class SkillLoadException(message: String, cause: Throwable? = null) : Exception(message, cause)
