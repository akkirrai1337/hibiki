package org.akkirrai.hibiki.core.source.extension.repository

import kotlinx.serialization.Serializable

const val SOURCE_REPOSITORY_INDEX_URL =
    "https://raw.githubusercontent.com/akkirrai1337/hibiki-sources/main/repository/index.json"

@Serializable
data class SourceRepositoryIndex(
    val schemaVersion: Int,
    val sources: List<SourceRepositoryEntry>,
)

@Serializable
data class SourceRepositoryEntry(
    val id: String,
    val name: String,
    val packageName: String,
    val version: String,
    val versionCode: Int,
    val contractVersion: Int,
    val apkUrl: String,
    val sha256: String,
    val sizeBytes: Long,
)
