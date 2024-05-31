package com.example.mediatransformer.transformer.ffmpeg

import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.util.Log
import com.arthenica.ffmpegkit.FFmpegKit
import com.arthenica.ffmpegkit.FFmpegKitConfig
import com.arthenica.ffmpegkit.FFmpegSession
import com.arthenica.ffmpegkit.ReturnCode
import com.example.mediatransformer.transformer.Cancel
import com.example.mediatransformer.transformer.Error
import com.example.mediatransformer.transformer.MediaTransformer
import com.example.mediatransformer.transformer.Success
import com.example.mediatransformer.transformer.TransformResult
import com.example.mediatransformer.transformer.TransformerFileManager
import com.example.mediatransformer.transformer.TransformerFileManagerContract
import com.example.mediatransformer.utils.FileUtils
import java.io.File

private const val FFMPEG_LOG_TAG = "FFMPEG_LOG"

class FFMPEGTransformer(
    private val context: Context,
    override val transformerFileManager: TransformerFileManagerContract = TransformerFileManager(
        context
    )
) : MediaTransformer {

    private val filesInProgress: MutableMap<String, Long> = mutableMapOf()
    private val progressStates: MutableMap<Long, Double> = mutableMapOf()
    override var transformationChainLength = 0

    override fun transform(videoUri: Uri, onResult: (TransformResult) -> Unit) {
        val inputFile = FileUtils.fileFromContentUri(context, videoUri)
        val outputFile = getOutputFile(videoUri, context)
        val convertCommand = buildConverterCommand(
            inputPath = inputFile.path,
            outputPath = outputFile.path
        )
        val totalFrames = MediaMetadataRetriever().let { retriever ->
            retriever.setDataSource(inputFile.path)
            retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_FRAME_COUNT)
        }?.toInt() ?: 0

        FFmpegKit.executeAsync(convertCommand, { session ->
            onResult(mapTransformResult(session, outputFile))
        }, { log ->
            Log.i(FFMPEG_LOG_TAG, log.message)
        }, { stats ->
            filesInProgress[outputFile.path] = stats.sessionId
            progressStates[stats.sessionId] = stats.videoFrameNumber.toDouble() / totalFrames
        })
    }

    private fun buildConverterCommand(inputPath: String, outputPath: String): String {
        return "-y -i $inputPath -c:v libx265 -c:a aac $outputPath.mp4"
    }

    private fun mapTransformResult(session: FFmpegSession, outputFile: File): TransformResult {
        return when (session.returnCode.value) {
            ReturnCode.SUCCESS -> {
                Success(
                    duration = session.duration,
                    outputFile = outputFile
                )
            }

            ReturnCode.CANCEL -> {
                Cancel(
                    duration = session.duration
                )
            }

            else -> {
                Error(
                    duration = session.duration,
                    error = session.failStackTrace
                )
            }
        }
    }

    override fun cancel() {
        FFmpegKit.cancel()
        FFmpegKitConfig.clearSessions()
        cleanEncodedVideos()
    }

    override fun cleanEncodedVideos() {
        super.cleanEncodedVideos()

        filesInProgress.forEach {
            transformerFileManager.cleanEncodedCache(File(it.key))
        }
        filesInProgress.clear()
        progressStates.clear()
    }

    override fun getCurrentProgress(): Double {
        var currentProgress = 0.0
        progressStates.forEach {
            currentProgress += it.value
        }
        val totalTransformationsCount =
            if (transformationChainLength > 0) transformationChainLength else progressStates.size
        return currentProgress / totalTransformationsCount
    }
}