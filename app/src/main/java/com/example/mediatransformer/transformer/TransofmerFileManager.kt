package com.example.mediatransformer.transformer

import android.content.Context
import java.io.File

private const val ENCODED_FOLDER_NAME = "encoded_files"

interface TransformerFileManagerContract {

    fun getEncodedFile(inputVideoUrl: String): File

    fun cleanEncodedCache(cacheFile: File)
}

class TransformerFileManager(private val context: Context) : TransformerFileManagerContract {
    override fun getEncodedFile(inputVideoUrl: String): File {
        val inputVideoFile = File(inputVideoUrl)
        require(inputVideoFile.exists()) { "File doesn't exists" }
        val encodedFolder = File(context.cacheDir, ENCODED_FOLDER_NAME).apply {
            if (!exists()) {
                mkdir()
            }
        }
        return File(encodedFolder.path, inputVideoFile.name)
    }

    override fun cleanEncodedCache(cacheFile: File) {
        if (cacheFile.exists()) {
            cacheFile.delete()
        }
    }
}