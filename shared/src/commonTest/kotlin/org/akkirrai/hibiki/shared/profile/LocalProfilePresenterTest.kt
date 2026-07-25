package org.akkirrai.hibiki.shared.profile

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlinx.coroutines.test.runTest

class LocalProfilePresenterTest {
    @Test
    fun updatesProfileFields() {
        val presenter = LocalProfilePresenter()

        presenter.updateProfileName("Demo")
        presenter.updateProfileAvatar("content://avatar")

        assertEquals("Demo", presenter.state.value.data.profileName)
        assertEquals("content://avatar", presenter.state.value.data.profileAvatarUri)
    }

    @Test
    fun loadsDataThroughRepository() = runTest {
        val presenter = LocalProfilePresenter()
        val repository = object : LocalProfileDataRepository {
            override suspend fun load(): LocalProfileData = LocalProfileData(profileName = "Loaded")
        }

        presenter.load(repository)

        assertEquals("Loaded", presenter.state.value.data.profileName)
        assertFalse(presenter.state.value.isLoading)
    }
}
