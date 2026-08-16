package org.akkirrai.hibiki.source.aniliberty

import android.app.Activity
import android.os.Bundle

/** Never shown; exists only as a manifest anchor for source-extension discovery. */
class AniLibertyDiscoveryActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        finish()
    }
}
