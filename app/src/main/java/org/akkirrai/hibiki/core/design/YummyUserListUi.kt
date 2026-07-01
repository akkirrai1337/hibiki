package org.akkirrai.hibiki.core.design

import androidx.compose.ui.graphics.Color
import org.akkirrai.animeresolver.metadata.YummyUserList

const val YUMMY_FAVORITE_LIST_ID = 4

fun YummyUserList?.yummyListLabel(): String {
    return when (this) {
        YummyUserList.Watching -> "Смотрю"
        YummyUserList.Planned -> "В планах"
        YummyUserList.Completed -> "Просмотрено"
        YummyUserList.Dropped -> "Брошено"
        YummyUserList.OnHold -> "Отложено"
        null -> "Не указано"
    }
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
