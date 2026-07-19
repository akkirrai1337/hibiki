package org.akkirrai.beakokit.architecture

import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.exists
import kotlin.io.path.extension
import kotlin.io.path.readText
import kotlin.test.Test
import kotlin.test.assertTrue

class DependencyBoundaryTest {
    @Test
    fun `BeakoKit main sources do not depend on the legacy resolver namespace`() {
        val sourceRoot = findSourceRoot()
        val violations = Files.walk(sourceRoot).use { paths ->
            paths
                .filter { it.extension == "kt" }
                .filter { it.readText().contains("org.akkirrai.animeresolver") }
                .map(sourceRoot::relativize)
                .toList()
        }

        assertTrue(
            violations.isEmpty(),
            "BeakoKit must remain extractable; legacy namespace imports found in: $violations",
        )
    }

    private fun findSourceRoot(): Path {
        val relativeRoot = Path.of("src", "main", "kotlin", "org", "akkirrai", "beakokit")
        val repositoryRelativeRoot = Path.of("parsers").resolve(relativeRoot)
        return generateSequence(Path.of("").toAbsolutePath()) { it.parent }
            .map { current ->
                listOf(current.resolve(relativeRoot), current.resolve(repositoryRelativeRoot))
            }
            .flatten()
            .firstOrNull(Path::exists)
            ?: error("Unable to locate BeakoKit source root")
    }
}
