package com.example.mediatransformer.transformer.media3

import android.content.Context
import android.media.MediaCodecInfo.CodecProfileLevel.AVCProfileMain
import android.net.Uri
import androidx.annotation.OptIn
import androidx.media3.common.Format.NO_VALUE
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.UnstableApi
import androidx.media3.transformer.Composition
import androidx.media3.transformer.DefaultEncoderFactory
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.ProgressHolder
import androidx.media3.transformer.Transformer
import androidx.media3.transformer.VideoEncoderSettings
import com.example.mediatransformer.transformer.Error
import com.example.mediatransformer.transformer.MediaTransformer
import com.example.mediatransformer.transformer.Success
import com.example.mediatransformer.transformer.TransformResult
import com.example.mediatransformer.transformer.TransformerFileManager
import com.example.mediatransformer.transformer.TransformerFileManagerContract
import java.io.File

@OptIn(UnstableApi::class)
class Media3Transformer(
    private val context: Context,
    override val transformerFileManager: TransformerFileManagerContract = TransformerFileManager(
        context
    )
) : MediaTransformer {

    private val currentTransformations: MutableMap<String, Transformer> = mutableMapOf()
    private val progressStates: MutableMap<Transformer, ProgressHolder> = mutableMapOf()
    override var transformationChainLength: Int = 0

    override fun transform(
        videoUri: Uri,
        onResult: (TransformResult) -> Unit
    ) {
        val outputVideoUrl: File = getOutputFile(videoUri, context)
        val editedMedia = MediaItem.Builder().setUri(videoUri).build()
        val transformStart = System.currentTimeMillis()
        val progressHolder = ProgressHolder()
        val transformation = Transformer.Builder(context)
            .setVideoMimeType(MimeTypes.VIDEO_H265)
            .setAudioMimeType(MimeTypes.AUDIO_AAC)
            .setEncoderFactory(
                DefaultEncoderFactory
                    .Builder(context)
                    .setRequestedVideoEncoderSettings(
                        VideoEncoderSettings.Builder()
                            .setEncodingProfileLevel(AVCProfileMain, NO_VALUE)
                            .build()
                    )
                    .build()
            )
            .build()

        transformation.addListener(object : Transformer.Listener {

            override fun onCompleted(composition: Composition, exportResult: ExportResult) {
                super.onCompleted(composition, exportResult)

                onResult(
                    Success(
                        duration = System.currentTimeMillis() - transformStart,
                        outputFile = outputVideoUrl
                    )
                )
            }

            override fun onError(
                composition: Composition,
                exportResult: ExportResult,
                exportException: ExportException
            ) {
                onResult(
                    Error(
                        duration = System.currentTimeMillis() - transformStart,
                        error = exportException.message.orEmpty()
                    )
                )
            }
        })

        currentTransformations[outputVideoUrl.path] = transformation
        progressStates[transformation] = progressHolder
        transformation.start(editedMedia, outputVideoUrl.path)
    }

    override fun cancel() {
        currentTransformations.forEach {
            it.value.cancel()
        }
        cleanEncodedVideos()
    }

    override fun cleanEncodedVideos() {
        super.cleanEncodedVideos()

        currentTransformations.forEach {
            transformerFileManager.cleanEncodedCache(File(it.key))
        }
        currentTransformations.clear()
        progressStates.clear()
    }

    override fun getCurrentProgress(): Double {
        currentTransformations.forEach { transformers ->
            progressStates[transformers.value]?.let {
                transformers.value.getProgress(it)
            }
        }
        var currentProgress = 0.0
        progressStates.forEach {
            currentProgress += it.value.progress
        }
        val totalTransformationsCount =
            if (transformationChainLength > 0) transformationChainLength else progressStates.size
        return currentProgress / totalTransformationsCount / 100
    }
}