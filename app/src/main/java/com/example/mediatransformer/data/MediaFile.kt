package com.example.mediatransformer.data

import android.net.Uri

data class MediaFile(
    val name: String = "",
    val size: Long = 0L,
    val duration: Long = 0L,
    val height: Int = 0,
    val width: Int = 0,
    val uri: Uri = Uri.EMPTY
)

data class ConvertedMediaFile(
    val mediaFile: MediaFile = MediaFile(),
    val convertingDuration: Long = 0L,
    val convertError: String = ""
)