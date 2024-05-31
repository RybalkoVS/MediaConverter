package com.example.mediatransformer

import android.content.ClipData
import android.content.ClipboardManager
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.OpenableColumns
import android.text.format.Formatter
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.example.mediatransformer.data.ConvertedMediaFile
import com.example.mediatransformer.data.MediaFile
import com.example.mediatransformer.transformer.Error
import com.example.mediatransformer.transformer.MediaTransformer
import com.example.mediatransformer.transformer.ffmpeg.FFMPEGTransformer
import com.example.mediatransformer.transformer.media3.Media3Transformer
import com.example.mediatransformer.ui.theme.MediaTransformerTheme
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Locale


private const val MEDIA_COUNT = 5
private const val TAG_TRANSFORM_RESULT = "TRANSFORM RESULT"

class MainActivity : ComponentActivity() {
    private var showErrorState by mutableStateOf(false)
    private var isMediaSelected by mutableStateOf(false)
    private val selectedMedia: MutableList<MediaFile> = mutableListOf()
    private val convertedMedia: MutableList<ConvertedMediaFile> = mutableListOf()
    private val retriever = MediaMetadataRetriever()
    private val pickMedia =
        registerForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(MEDIA_COUNT)) { selectedUris ->
            if (selectedUris.isNotEmpty()) {
                val mediaFiles = mapSelectedMedia(selectedUris)
                selectedMedia.addAll(mediaFiles)
                isMediaSelected = true
            }
        }
    private var isParallelTransformEnabled by mutableStateOf(false)
    private var isFFMPEGTransform by mutableStateOf(false)
    private var isTransforming by mutableStateOf(false)
    private var showResults by mutableStateOf(false)
    private var currentTransformationDuration = 0L
    private var startParallelTransformTime: Long = 0L
    private var globalError: String = ""
    private var ffmpegTransformer: FFMPEGTransformer? = null
    private var media3Transformer: Media3Transformer? = null
    private var selectedTransformer: MediaTransformer? = null
    private var currentProgress: Float by mutableFloatStateOf(0f)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        ffmpegTransformer = FFMPEGTransformer(this)
        media3Transformer = Media3Transformer(this)
        selectedTransformer = media3Transformer

        enableEdgeToEdge()
        setContent {
            MediaTransformerTheme {
                LaunchedEffect(key1 = isTransforming) {
                    while (isTransforming) {
                        currentProgress = selectedTransformer?.getCurrentProgress()?.toFloat() ?: 0F
                        delay(500)
                    }
                }

                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Button(
                        onClick = {
                            pickMedia.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly))
                        }
                    ) {
                        Text("Select Media")
                    }

                    if (isMediaSelected) {
                        PreTransformDialog(mediaList = selectedMedia)
                    }

                    if (isTransforming) {
                        ProgressDialog()
                    }

                    if (showResults) {
                        TransformResultDialog()
                    }

                    if (showErrorState) {
                        GlobalErrorState()
                    }
                }
            }
        }
    }

    private fun mapSelectedMedia(uris: List<Uri>): List<MediaFile> {
        return uris.map {
            contentResolver.query(it, null, null, null, null).use { cursor ->
                val nameIndex = cursor?.getColumnIndex(OpenableColumns.DISPLAY_NAME) ?: 0
                val sizeIndex = cursor?.getColumnIndex(OpenableColumns.SIZE) ?: 0
                cursor?.moveToFirst()
                retriever.setDataSource(this, it)
                MediaFile(
                    name = cursor?.getString(nameIndex).orEmpty(),
                    size = cursor?.getString(sizeIndex).orEmpty().toLong(),
                    duration = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                        ?.toLong() ?: 0L,
                    height = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)
                        ?.toInt() ?: 0,
                    width = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)
                        ?.toInt() ?: 0,
                    uri = it
                )
            }
        }
    }

    @Composable
    private fun PreTransformDialog(mediaList: List<MediaFile>) {
        val coroutineScope = rememberCoroutineScope()

        Dialog(
            onDismissRequest = {
                isMediaSelected = false
                clearSelectedUris()
            }
        ) {
            Column(
                horizontalAlignment = Alignment.Start,
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color.White)
                    .fillMaxWidth()
                    .wrapContentHeight()
                    .padding(horizontal = 16.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Switch(
                        checked = isFFMPEGTransform,
                        onCheckedChange = { checked ->
                            isFFMPEGTransform = checked
                            selectedTransformer =
                                if (isFFMPEGTransform) ffmpegTransformer else media3Transformer
                        }
                    )

                    Spacer(modifier = Modifier.width(4.dp))

                    Text("Use ffmpeg")
                }

                Spacer(modifier = Modifier.height(4.dp))

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Switch(
                        checked = isParallelTransformEnabled,
                        onCheckedChange = { checked ->
                            isParallelTransformEnabled = checked
                        }
                    )

                    Spacer(modifier = Modifier.width(4.dp))

                    Text("Parallel transformation")
                }

                mediaList.forEach {
                    Row(Modifier.wrapContentSize()) {
                        val mediaDataString =
                            """File: ${it.name} Duration: ${getFormattedDuration(it.duration)} Size: ${
                                Formatter.formatFileSize(
                                    this@MainActivity,
                                    it.size
                                )
                            } Resolution: ${it.height}x${it.width}"""
                        Text(mediaDataString)
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                Button(
                    onClick = {
                        coroutineScope.launch {
                            transformVideos()
                        }
                    },
                    modifier = Modifier.align(Alignment.CenterHorizontally)
                ) {
                    Text("Transform")
                }
            }
        }
    }

    @Composable
    private fun ProgressDialog() {
        Dialog(onDismissRequest = { }) {
            Box(
                modifier = Modifier
                    .size(400.dp)
                    .background(Color.White),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("${String.format(Locale.getDefault(), "%.2f", currentProgress * 100)}%")

                    Spacer(modifier = Modifier.height(4.dp))

                    LinearProgressIndicator(currentProgress)

                    Spacer(modifier = Modifier.height(100.dp))

                    Button(onClick = { cancelTransform() }) {
                        Text("Cancel")
                    }
                }
            }
        }
    }

    private fun cancelTransform() {
        selectedTransformer?.cancel()
        onConvertingFinished(withResults = false)
        startParallelTransformTime = 0L
        currentTransformationDuration = 0L
        convertedMedia.clear()
    }

    @Composable
    private fun GlobalErrorState() {
        Dialog(onDismissRequest = { onCloseResultsDialog() }) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(vertical = 4.dp)
                    .background(Color.White)
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.Start
            ) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                    Button(onClick = {
                        shareResults(
                            text = globalError
                        )
                    }) {
                        Text("Copy results")
                    }

                    Spacer(modifier = Modifier.width(4.dp))

                    Button(onClick = { onCloseResultsDialog() }) {
                        Text(text = "Close")
                    }
                }
            }
        }
    }

    @Composable
    private fun TransformResultDialog() {
        val mediaList: List<String> = convertedMedia.map {
            val error =
                if (it.convertError.isNotEmpty()) "\nError: ${it.convertError}" else ""

            "File: ${it.mediaFile.name} \nDuration: ${getFormattedDuration(it.mediaFile.duration)} \nSize: ${
                Formatter.formatFileSize(
                    this@MainActivity,
                    it.mediaFile.size
                )
            } \nResolution: ${it.mediaFile.height}x${it.mediaFile.width} \nProcessing time: ${
                getFormattedDuration(
                    it.convertingDuration
                )
            } $error\n"
        }
        val totalProcessingTime = "Total processing duration:" + getFormattedDuration(
            currentTransformationDuration
        )
        val transformEngine =
            "Transformation engine: " + if (isFFMPEGTransform) "FFMPEG" else "Media3"
        val executionType =
            "Execution type: " + if (isParallelTransformEnabled) "Parallel" else "Sequential"
        val deviceInfo =
            "Device: " + Build.DEVICE + " Api: " + Build.VERSION.SDK_INT + " Android " + Build.VERSION.RELEASE
        val output =
            "$mediaList \n $totalProcessingTime \n $transformEngine \n $executionType \n $deviceInfo"

        Dialog(onDismissRequest = { onCloseResultsDialog() }) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(vertical = 4.dp)
                    .background(Color.White)
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.Start
            ) {
                mediaList.forEach {
                    Text(it)
                }

                Text(totalProcessingTime)

                Spacer(modifier = Modifier.height(4.dp))

                Text(transformEngine)

                Spacer(modifier = Modifier.height(4.dp))

                Text(executionType)

                Spacer(modifier = Modifier.height(4.dp))

                Text(deviceInfo)

                Spacer(modifier = Modifier.weight(1f))

                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                    Button(onClick = {
                        shareResults(
                            text = output
                        )
                    }) {
                        Text("Copy results")
                    }

                    Spacer(modifier = Modifier.width(4.dp))

                    Button(onClick = { onCloseResultsDialog() }) {
                        Text(text = "Close")
                    }
                }
            }
        }
    }

    private fun onCloseResultsDialog() {
        startParallelTransformTime = 0L
        showResults = false
        convertedMedia.clear()
        currentTransformationDuration = 0L
        showErrorState = false
        globalError = ""
    }

    private fun shareResults(text: String) {
        val clipboard: ClipboardManager = getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText(TAG_TRANSFORM_RESULT, text)
        clipboard.setPrimaryClip(clip)
        Toast.makeText(this, "Copied to clipboard", Toast.LENGTH_SHORT).show()
    }

    private fun getFormattedDuration(timeMs: Long): String {
        val totalSeconds = timeMs / 1000

        val seconds = totalSeconds % 60
        val minutes = (totalSeconds / 60) % 60
        val hours = totalSeconds / 3600

        val mFormatBuilder = StringBuilder()
        val mFormatter = java.util.Formatter(mFormatBuilder, Locale.getDefault())

        mFormatBuilder.setLength(0)
        return if (hours > 0) {
            mFormatter.format("%d:%02d:%02d", hours, minutes, seconds).toString()
        } else {
            mFormatter.format("%02d:%02d", minutes, seconds).toString()
        }
    }

    private fun clearSelectedUris() {
        selectedMedia.clear()
    }

    private suspend fun transformVideos() {
        try {
            isTransforming = true

            if (isParallelTransformEnabled) {
                parallelTransform()
            } else {
                selectedTransformer?.setTransformationChainSize(selectedMedia.size)
                sequentialTransform()
            }
        } catch (e: Exception) {
            globalError = e.message.orEmpty()
            showErrorState = true
        }
    }

    private fun sequentialTransform() {
        selectedMedia.reversed().lastOrNull()?.let { mediaFile ->
            selectedTransformer?.transform(mediaFile.uri) {
                currentTransformationDuration += it.duration
                convertedMedia.add(
                    buildConvertedMedia(
                        mediaFile = selectedMedia.removeFirst(),
                        duration = it.duration,
                        error = if (it is Error) it.error else null
                    )
                )
                sequentialTransform()
            }
        } ?: run {
            onConvertingFinished()
        }
    }

    private suspend fun parallelTransform() {
        val checkIfAllVideosAreConverted: (index: Int, duration: Long, error: String?) -> Unit =
            { index, duration, error ->
                convertedMedia.add(buildConvertedMedia(selectedMedia[index], duration, error))
                if (convertedMedia.size == selectedMedia.size) {
                    currentTransformationDuration =
                        System.currentTimeMillis() - startParallelTransformTime
                    onConvertingFinished()
                }
            }

        startParallelTransformTime = System.currentTimeMillis()
        selectedMedia.forEachIndexed { index, mediaFile ->
            coroutineScope {
                launch {
                    selectedTransformer?.transform(mediaFile.uri) {
                        checkIfAllVideosAreConverted(
                            index,
                            it.duration,
                            if (it is Error) it.error else null
                        )
                    }
                }
            }
        }
    }

    private fun buildConvertedMedia(
        mediaFile: MediaFile,
        duration: Long,
        error: String? = null
    ): ConvertedMediaFile {
        return ConvertedMediaFile(
            mediaFile,
            duration,
            error.orEmpty()
        )
    }

    private fun onConvertingFinished(withResults: Boolean = true) {
        clearSelectedUris()
        isMediaSelected = false
        isTransforming = false
        showResults = withResults
        currentProgress = 0f
        selectedTransformer?.cleanEncodedVideos()
    }
}