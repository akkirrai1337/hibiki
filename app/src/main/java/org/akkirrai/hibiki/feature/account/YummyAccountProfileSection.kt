package org.akkirrai.hibiki.feature.account

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.SubcomposeAsyncImage
import org.akkirrai.animeresolver.metadata.YummyProfile
import org.akkirrai.animeresolver.metadata.YummyProfileSex
import org.akkirrai.hibiki.R
import org.akkirrai.hibiki.core.design.component.PosterPlaceholder

@Composable
internal fun ProfileCard(
    profile: YummyProfile,
) {
    val nickname = profile.nickname.ifBlank { stringResource(R.string.yummy_account_profile_fallback) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
                Avatar(
                    avatarUrl = profile.avatarUrl ?: profile.avatars.full ?: profile.avatars.big ?: profile.avatars.small,
                    nickname = nickname,
                    modifier = Modifier.size(84.dp),
                )
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(7.dp),
                ) {
                    Text(
                        text = nickname,
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    MetaWrapRow(
                        items = listOf(
                            stringResource(
                                R.string.yummy_account_profile_registered,
                            ) + ": " + (profile.registerDate?.let(::formatEpochDateCompact)
                                ?: stringResource(R.string.yummy_account_profile_unknown)),
                            stringResource(R.string.yummy_account_profile_birthdate) + ": " +
                                (profile.birthDate?.let(::formatEpochDateCompact)
                                    ?: stringResource(R.string.yummy_account_profile_unknown)),
                            stringResource(R.string.yummy_account_profile_gender) + ": " + profile.sex.toLabel(),
                        ),
                    )
                }
        }
    }
}

@Composable
private fun MetaWrapRow(
    items: List<String>,
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        items.forEach { item ->
            Text(
                text = item,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun Avatar(
    avatarUrl: String?,
    nickname: String,
    modifier: Modifier = Modifier,
) {
    val resolvedUrl = normalizeYummyAssetUrl(avatarUrl)
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(24.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.4f)),
        contentAlignment = Alignment.Center,
    ) {
        if (resolvedUrl == null) {
            DefaultAvatarPlaceholder(nickname)
        } else {
            SubcomposeAsyncImage(
                model = resolvedUrl,
                contentDescription = nickname,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
                loading = {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(MaterialTheme.colorScheme.surfaceContainerHighest),
                    )
                },
                error = {
                    PosterPlaceholder(modifier = Modifier.fillMaxSize()) {
                        DefaultAvatarPlaceholder(nickname)
                    }
                },
            )
        }
    }
}

@Composable
private fun DefaultAvatarPlaceholder(
    nickname: String,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(Color(0xFFF8F8F8), Color(0xFFD6D6D6)),
                ),
            ),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = Icons.Outlined.Person,
            contentDescription = nickname,
            modifier = Modifier.size(40.dp),
            tint = Color(0xFF6D6D6D),
        )
    }
}

@Composable
private fun YummyProfileSex?.toLabel(): String {
    return when (this) {
        YummyProfileSex.Male -> stringResource(R.string.yummy_account_profile_gender_male)
        YummyProfileSex.Female -> stringResource(R.string.yummy_account_profile_gender_female)
        else -> stringResource(R.string.yummy_account_profile_unknown)
    }
}
