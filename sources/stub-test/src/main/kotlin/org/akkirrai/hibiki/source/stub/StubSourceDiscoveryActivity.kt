package org.akkirrai.hibiki.source.stub

import android.app.Activity
import android.os.Bundle

/** Never shown; exists only as a manifest anchor for [SourceExtensionContract] discovery. */
class StubSourceDiscoveryActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        finish()
    }
}
