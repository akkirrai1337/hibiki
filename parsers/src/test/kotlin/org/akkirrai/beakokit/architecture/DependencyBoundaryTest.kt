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
        val sourceRoots = findSourceRoots(
            Path.of("src", "commonMain", "kotlin"),
            Path.of("parsers", "src", "commonMain", "kotlin"),
            Path.of("src", "jvmMain", "kotlin"),
            Path.of("parsers", "src", "jvmMain", "kotlin"),
            Path.of("src", "iosMain", "kotlin"),
            Path.of("parsers", "src", "iosMain", "kotlin"),
        )
        assertNoLegacyImports(sourceRoots, "BeakoKit")
    }

    @Test
    fun `Hibiki app consumes BeakoKit without legacy resolver imports`() {
        val sourceRoots = findSourceRoots(
            Path.of("app", "src", "main", "java"),
            Path.of("..", "app", "src", "main", "java"),
        )
        assertNoLegacyImports(sourceRoots, "Hibiki app")
    }

    @Test
    fun `legacy resolver namespace has no production sources`() {
        val productionRoots = findSourceRoots(
            Path.of("src", "commonMain", "kotlin"),
            Path.of("parsers", "src", "commonMain", "kotlin"),
            Path.of("src", "jvmMain", "kotlin"),
            Path.of("parsers", "src", "jvmMain", "kotlin"),
            Path.of("src", "iosMain", "kotlin"),
            Path.of("parsers", "src", "iosMain", "kotlin"),
        )
        val legacySources = productionRoots.flatMap { kotlinRoot ->
            val legacyRoot = kotlinRoot.resolve(Path.of("org", "akkirrai", "animeresolver"))
            if (legacyRoot.exists()) Files.walk(legacyRoot).use { paths ->
                paths
                    .filter { it.isRegularFile() && it.extension == "kt" }
                    .map(kotlinRoot::relativize)
                    .toList()
            } else emptyList()
        }

        assertTrue(
            legacySources.isEmpty(),
            "AnimeResolver production sources must not return: $legacySources",
        )
    }

    private fun assertNoLegacyImports(sourceRoots: List<Path>, owner: String) {
        val violations = sourceRoots.flatMap { sourceRoot ->
            Files.walk(sourceRoot).use { paths ->
                paths
                    .filter { it.extension == "kt" }
                    .filter { it.readText().contains("org.akkirrai.animeresolver") }
                    .map(sourceRoot::relativize)
                    .toList()
            }
        }

        assertTrue(
            violations.isEmpty(),
            "$owner must use the extractable BeakoKit API; legacy namespace imports found in: $violations",
        )
    }

    private fun findSourceRoots(vararg candidates: Path): List<Path> {
        val roots = generateSequence(Path.of("").toAbsolutePath()) { it.parent }
            .map { current -> candidates.map(current::resolve) }
            .flatten()
            .filter(Path::exists)
            .distinct()
            .toList()
        return roots.ifEmpty {
            error("Unable to locate source roots from ${candidates.toList()}")
        }
    }
}
