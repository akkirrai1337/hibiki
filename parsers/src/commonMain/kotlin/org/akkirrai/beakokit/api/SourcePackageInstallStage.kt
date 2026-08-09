package org.akkirrai.beakokit.api

/** Observable, host-owned checkpoints of one transactional package installation. */
enum class SourcePackageInstallStage {
    DOWNLOADING,
    VERIFYING_ARTIFACT,
    EXTRACTING,
    VALIDATING_PACKAGE,
    INITIALIZING_RUNTIME,
    ACTIVATING,
}
