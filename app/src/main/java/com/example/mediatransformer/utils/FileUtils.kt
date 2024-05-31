package com.example.mediatransformer.utils

import android.content.Context
import android.net.Uri
import android.webkit.MimeTypeMap
import com.google.common.io.ByteStreams
import java.io.File
import java.io.FileOutputStream

object FileUtils {

    fun fileFromContentUri(context: Context, contentUri: Uri): File {
        val fileExtension = getFileExtension(contentUri, context)
        val fileName =
            "temporary_file_${contentUri.lastPathSegment}" + if (fileExtension != null) ".$fileExtension" else ""

        val tempFile = File(context.cacheDir, fileName)
        tempFile.createNewFile()

        try {
            val oStream = FileOutputStream(tempFile)
            val inputStream = context.contentResolver.openInputStream(contentUri)

            inputStream?.let {
                ByteStreams.copy(inputStream, oStream)
            }

            oStream.flush()
        } catch (e: Exception) {
            e.printStackTrace()
        }

        return tempFile
    }

    private fun getFileExtension(uri: Uri, context: Context): String? {
        val fileType: String? = context.contentResolver.getType(uri)
        return MimeTypeMap.getSingleton().getMimeTypeFromExtension(fileType)
    }
}