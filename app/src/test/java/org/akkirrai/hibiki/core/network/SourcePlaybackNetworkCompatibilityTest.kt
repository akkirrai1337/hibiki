package org.akkirrai.hibiki.core.network

import org.akkirrai.hibiki.core.source.AnimeSourceRegistry
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

class SourcePlaybackNetworkCompatibilityTest {
    @Test
    fun `all declared cleartext playback hosts are allowed by Android network security config`() {
        val declaredHosts = AnimeSourceRegistry.catalog.sources
            .flatMap { it.networkRequirements.cleartextPlaybackHosts }
            .toSet()
        val configuredHosts = DocumentBuilderFactory.newInstance()
            .newDocumentBuilder()
            .parse(File("src/main/res/xml/network_security_config.xml"))
            .getElementsByTagName("domain")
            .let { domains -> (0 until domains.length).map { index -> domains.item(index).textContent.trim() }.toSet() }

        val missingHosts = declaredHosts - configuredHosts
        assertTrue(
            "Android network security config is missing cleartext playback hosts: ${missingHosts.joinToString()}",
            missingHosts.isEmpty(),
        )
    }
}
