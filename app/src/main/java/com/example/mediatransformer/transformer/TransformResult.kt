package com.example.mediatransformer.transformer

import java.io.File

sealed interface TransformResult {
    val duration: Long
}

data class Success(
    override val duration: Long,
    val outputFile: File,
) : TransformResult

data class Error(
    override val duration: Long,
    val error: String
) : TransformResult

data class Cancel(
    override val duration: Long,
) : TransformResult