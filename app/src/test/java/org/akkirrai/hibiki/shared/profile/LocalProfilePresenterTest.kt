package org.akkirrai.hibiki.shared.profile

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class LocalProfilePresenterTest {
    @Test
    fun loadPublishesSharedDataAndClearsLoadingState() = runTest {
        val presenter = LocalProfilePresenter()
        val expected = LocalProfileData(profileName = "Vadim")

        presenter.load(object : LocalProfileDataRepository {
            override suspend fun load(libraryEntries: List<org.akkirrai.hibiki.shared.library.LibraryEntry>?): LocalProfileData = expected
        })

        assertEquals(expected, presenter.state.value.data)
        assertFalse(presenter.state.value.isLoading)
    }

    @Test
    fun failedLoadDoesNotLeavePresenterLoading() = runTest {
        val presenter = LocalProfilePresenter()

        runCatching {
            presenter.load(object : LocalProfileDataRepository {
                override suspend fun load(libraryEntries: List<org.akkirrai.hibiki.shared.library.LibraryEntry>?): LocalProfileData = error("load failed")
            })
        }

        assertFalse(presenter.state.value.isLoading)
    }

    @Test
    fun profileMutationsUpdateSharedState() {
        val presenter = LocalProfilePresenter()

        presenter.updateProfileName("hibiki")
        presenter.updateProfileAvatar("content://avatar")

        assertEquals("hibiki", presenter.state.value.data.profileName)
        assertEquals("content://avatar", presenter.state.value.data.profileAvatarUri)
        assertFalse(presenter.state.value.isLoading)
    }
}
