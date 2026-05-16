package com.nearlink.messenger.worker

import android.content.Context
import android.net.Uri
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.nearlink.messenger.data.local.dao.MessageDao
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.io.File

/**
 * 文件分片上传/下载 Worker。
 */
@HiltWorker
class FileChunkWorker @AssistedInject constructor(
    @Assisted ctx: Context,
    @Assisted params: WorkerParameters,
    private val messageDao: MessageDao,
) : CoroutineWorker(ctx, params) {
    override suspend fun doWork(): Result {
        val messageId = inputData.getString(KEY_MESSAGE_ID) ?: return Result.failure()
        val localUri = inputData.getString(KEY_LOCAL_URI) ?: return Result.failure()
        val outFile = File(applicationContext.cacheDir, "attachments/$messageId")
        outFile.parentFile?.mkdirs()
        applicationContext.contentResolver.openInputStream(Uri.parse(localUri))?.use { input ->
            outFile.outputStream().use { output -> input.copyTo(output) }
        } ?: return Result.retry()
        messageDao.setAttachmentLocalUri(messageId, outFile.absolutePath, System.currentTimeMillis())
        return Result.success()
    }

    companion object {
        const val KEY_MESSAGE_ID = "message_id"
        const val KEY_LOCAL_URI = "local_uri"
    }
}
