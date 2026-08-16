package org.akkirrai.beakokit.api

/** Verifies an optional package signature against a host-defined trust policy. */
fun interface SourcePackageSignatureVerifier {
    fun verify(manifest: SourceManifest, artifact: SourcePackageArtifact): Boolean

    companion object {
        /** Safe default until a repository supplies a concrete key and signature policy. */
        val UNSIGNED_ONLY = SourcePackageSignatureVerifier { manifest, _ -> manifest.signature == null }
    }
}

