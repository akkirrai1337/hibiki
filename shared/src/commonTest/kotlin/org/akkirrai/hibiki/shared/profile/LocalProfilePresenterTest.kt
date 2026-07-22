package org.akkirrai.hibiki.shared.profile

import kotlin.test.Test
import kotlin.test.assertEquals

class LocalProfilePresenterTest {
    @Test
    fun updatesProfileFields() {
        val presenter = LocalProfilePresenter()

        presenter.updateProfileName("Demo")
        presenter.updateProfileAvatar("content://avatar")

        assertEquals("Demo", presenter.state.value.data.profileName)
        assertEquals("content://avatar", presenter.state.value.data.profileAvatarUri)
    }
}
