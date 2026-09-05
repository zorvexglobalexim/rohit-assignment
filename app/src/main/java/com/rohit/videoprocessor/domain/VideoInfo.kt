package com.rohit.videoprocessor.domain

import android.net.Uri

data class VideoInfo(
    val uri: Uri,
    val displayName: String,
    val sizeBytes: Long,
    val mimeType: String?,
    /** Best-effort - null if metadata couldn't be read yet/at all; never blocks video selection. */
    val durationMs: Long? = null,
)
