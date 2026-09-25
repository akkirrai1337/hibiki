package org.akkirrai.hibiki.core.anilist

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import org.akkirrai.hibiki.MainActivity
import org.akkirrai.hibiki.R

/**
 * The landing point of AniList's redirect (`hibiki://anilist-auth#access_token=...`). It has no UI: it
 * hands the redirect to the repository, which checks the state it issued, and returns to the app.
 */
class AniListAuthActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        handle(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handle(intent)
    }

    private fun handle(intent: Intent) {
        val repository = AniListRepository(this)
        val result = intent.dataString?.let(repository::completeAuthorization)
            ?: AniListAuthorizationResult.InvalidRedirect
        if (result != AniListAuthorizationResult.Connected) {
            Toast.makeText(this, R.string.anilist_auth_failed, Toast.LENGTH_LONG).show()
        }
        repository.close()
        // Back to the app, on whatever screen the sign-in was started from.
        startActivity(
            Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT or Intent.FLAG_ACTIVITY_SINGLE_TOP),
        )
        finish()
    }
}
