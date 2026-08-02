package org.akkirrai.beakokit.api

/** Repository trust decision applied before a package can be installed. */
class SourcePackageTrustPolicy private constructor(
    private val requiresSignature: Boolean,
    private val signatureVerifier: SourcePackageSignatureVerifier?,
) {
    fun violation(manifest: SourceManifest, artifact: SourcePackageArtifact): String? {
        val signature = manifest.signature
        if (signature == null) {
            return if (requiresSignature) "Package signature is required for this repository" else null
        }
        return if (signatureVerifier?.verify(manifest, artifact) == true) null else "Package signature is not trusted"
    }

    companion object {
        /** Built-in repository whose index/checksum is shipped or otherwise trusted by the host. */
        val TRUSTED_CATALOG = SourcePackageTrustPolicy(requiresSignature = false, signatureVerifier = null)

        /** User-added repository that may install packages only when its verifier accepts them. */
        fun untrusted(signatureVerifier: SourcePackageSignatureVerifier): SourcePackageTrustPolicy =
            SourcePackageTrustPolicy(requiresSignature = true, signatureVerifier = signatureVerifier)
    }
}

