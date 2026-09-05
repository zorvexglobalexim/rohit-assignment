package com.rohit.videoprocessor.viewmodel

import android.net.Uri

/**
 * Status of a Save-to-Gallery or Share action on the current collage, shown
 * as a transient message on [com.rohit.videoprocessor.ui.result.ResultScreen].
 * Share has no persistent "succeeded" state - handing off to the system
 * share sheet *is* the success signal, there's no callback for what the user
 * does inside it - only its failure to even prepare an image is surfaced here.
 */
sealed interface CollageActionState {
    data object Idle : CollageActionState
    data object Working : CollageActionState
    data class SaveSuccess(val uri: Uri) : CollageActionState
    data class Error(val message: String) : CollageActionState
}
