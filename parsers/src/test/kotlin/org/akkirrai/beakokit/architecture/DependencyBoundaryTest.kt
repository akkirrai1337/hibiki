package org.akkirrai.beakokit.architecture

import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.exists
import kotlin.io.path.extension
import kotlin.io.path.isRegularFile
import kotlin.io.path.readText
import kotlin.test.Test
import kotlin.test.assertTrue

class DependencyBoundaryTest {
    @Test
    fun `BeakoKit main sources do not depend on the legacy resolver namespace`() {
        val sourceRoot = findSourceRoot(
            Path.of("src", "main", "kotlin", "org", "akkirrai", "beakokit"),
            Path.of("parsers", "src", "main", "kotlin", "org", "akkirrai", "beakokit"),
        )
        assertNoLegacyImports(sourceRoot, "BeakoKit")
    }

    @Test
    fun `Hibiki app consumes BeakoKit without legacy resolver imports`() {
        val sourceRoot = findSourceRoot(
            Path.of("app", "src", "main", "java"),
            Path.of("..", "app", "src", "main", "java"),
        )
        assertNoLegacyImports(sourceRoot, "Hibiki app")
    }

    @Test
    fun `legacy resolver namespace has no production sources`() {
        val kotlinRoot = findSourceRoot(
            Path.of("src", "main", "kotlin"),
            Path.of("parsers", "src", "main", "kotlin"),
        )
        val legacyRoot = kotlinRoot.resolve(Path.of("org", "akkirrai", "animeresolver"))
        val legacySources = if (legacyRoot.exists()) Files.walk(legacyRoot).use { paths ->
            paths
                .filter { it.isRegularFile() && it.extension == "kt" }
                .map(kotlinRoot::relativize)
                .toList()
        } else emptyList()

        assertTrue(
            legacySources.isEmpty(),
            "AnimeResolver production sources must not return: $legacySources",
        )
    }

    private fun assertNoLegacyImports(sourceRoot: Path, owner: String) {
        val violations = Files.walk(sourceRoot).use { paths ->
            paths
                .filter { it.extension == "kt" }
                .filter { it.readText().contains("org.akkirrai.animeresolver") }
                .map(sourceRoot::relativize)
                .toList()
        }

        assertTrue(
            violations.isEmpty(),
            "$owner must use the extractable BeakoKit API; legacy namespace imports found in: $violations",
        )
    }

    private fun findSourceRoot(vararg candidates: Path): Path {
        return generateSequence(Path.of("").toAbsolutePath()) { it.parent }
            .map { current -> candidates.map(current::resolve) }
            .flatten()
            .firstOrNull(Path::exists)
            ?: error("Unable to locate source root from ${candidates.toList()}")
    }
}
