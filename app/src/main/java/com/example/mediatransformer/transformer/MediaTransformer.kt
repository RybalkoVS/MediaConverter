package com.example.mediatransformer.transformer

import android.content.Context
import android.net.Uri
import androidx.annotation.CallSuper
import com.example.mediatransformer.utils.FileUtils
import java.io.File

interface MediaTransformer {

    val transformerFileManager: TransformerFileManagerContract
    var transformationChainLength: Int

    fun transform(videoUri: Uri, onResult: (TransformResult) -> Unit)

    fun getOutputFile(inputVideoUri: Uri, context: Context): File {
        return transformerFileManager.getEncodedFile(
            FileUtils.fileFromContentUri(context, inputVideoUri).path
        )
    }

    fun getCurrentProgress(): Double

    fun setTransformationChainSize(filesCount: Int) {
        transformationChainLength = filesCount
    }

    fun cancel()

    @CallSuper
    fun cleanEncodedVideos() {
        transformationChainLength = 0
    }
}