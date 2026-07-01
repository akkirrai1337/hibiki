package org.akkirrai.hibiki.core.design

import android.content.res.Resources
import androidx.annotation.StringRes
import androidx.compose.ui.graphics.Color
import org.akkirrai.animeresolver.metadata.YummyUserList
import org.akkirrai.hibiki.R

const val YUMMY_FAVORITE_LIST_ID = 4

@StringRes
fun YummyUserList?.yummyListLabelRes(): Int {
    return when (this) {
        YummyUserList.Watching -> R.string.library_category_watching
        YummyUserList.Planned -> R.string.library_category_planned
        YummyUserList.Completed -> R.string.library_category_completed
        YummyUserList.Dropped -> R.string.library_category_dropped
        YummyUserList.OnHold -> R.string.library_category_on_hold
        null -> R.string.yummy_account_profile_unknown
    }
}

fun YummyUserList?.yummyListLabel(resources: Resources): String {
    return resources.getString(yummyListLabelRes())
}

fun YummyUserList?.yummyListColor(): Color {
    return when (this) {
        YummyUserList.Watching -> Color(0xFF3DDC84)
        YummyUserList.Planned -> Color(0xFF5DA9FF)
        YummyUserList.Completed -> Color(0xFFFFB84D)
        YummyUserList.Dropped -> Color(0xFFFF6B6B)
        YummyUserList.OnHold -> Color(0xFFC593FF)
        null -> Color(0xFF9EA4B2)
    }
}

fun yummyFavoriteListColor(): Color = Color(0xFFD65CFF)
