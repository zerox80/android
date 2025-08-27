package eu.opencloud.android.lib.resources.files.tus

import eu.opencloud.android.lib.common.OpenCloudClient
import eu.opencloud.android.lib.common.http.HttpConstants
import eu.opencloud.android.lib.common.http.methods.nonwebdav.PatchMethod
import eu.opencloud.android.lib.common.network.ChunkFromFileRequestBody
import eu.opencloud.android.lib.common.network.OnDatatransferProgressListener
import eu.opencloud.android.lib.common.operations.OperationCancelledException
import eu.opencloud.android.lib.common.operations.RemoteOperation
import eu.opencloud.android.lib.common.operations.RemoteOperationResult
import eu.opencloud.android.lib.common.operations.RemoteOperationResult.ResultCode
import eu.opencloud.android.lib.common.utils.isOneOf
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import timber.log.Timber
import java.io.File
import java.io.RandomAccessFile
import java.net.URL
import java.nio.channels.FileChannel
import java.util.concurrent.atomic.AtomicBoolean

/**
 * TUS Patch Upload Chunk operation (PATCH)
 * Uploads a chunk to an existing upload resource.
 * Returns the new Upload-Offset in the result data on success.
 */
class PatchTusUploadChunkRemoteOperation(
    private val localPath: String,
    private val uploadUrl: String,
    private val offset: Long,
    private val chunkSize: Long,
) : RemoteOperation<Long>() {

    private val cancellationRequested = AtomicBoolean(false)
    private val dataTransferListeners: MutableSet<OnDatatransferProgressListener> = HashSet()
    private var patchMethod: PatchMethod? = null

    override fun run(client: OpenCloudClient): RemoteOperationResult<Long> =
        try {
            val file = File(localPath)
            RandomAccessFile(file, "r").use { raf ->
                val channel: FileChannel = raf.channel
                val body = ChunkFromFileRequestBody(
                    file = file,
                    contentType = HttpConstants.CONTENT_TYPE_OFFSET_OCTET_STREAM.toMediaTypeOrNull(),
                    channel = channel,
                    chunkSize = chunkSize
                ).also { synchronized(dataTransferListeners) { it.addDatatransferProgressListeners(dataTransferListeners) } }

                body.setOffset(offset)

                if (cancellationRequested.get()) {
                    return RemoteOperationResult<Long>(OperationCancelledException())
                }

                patchMethod = PatchMethod(URL(uploadUrl), body).apply {
                    setRequestHeader(HttpConstants.TUS_RESUMABLE, HttpConstants.TUS_RESUMABLE_VERSION_1_0_0)
                    setRequestHeader(HttpConstants.UPLOAD_OFFSET, offset.toString())
                    setRequestHeader(HttpConstants.CONTENT_TYPE_HEADER, HttpConstants.CONTENT_TYPE_OFFSET_OCTET_STREAM)
                }

                val status = client.executeHttpMethod(patchMethod)
                Timber.d("Patch TUS upload chunk - $status${if (!isSuccess(status)) "(FAIL)" else ""}")

                if (isSuccess(status)) {
                    val newOffset = patchMethod!!.getResponseHeader(HttpConstants.UPLOAD_OFFSET)?.toLongOrNull()
                    if (newOffset != null) RemoteOperationResult<Long>(ResultCode.OK).apply { data = newOffset }
                    else RemoteOperationResult<Long>(patchMethod).apply { data = -1L }
                } else RemoteOperationResult<Long>(patchMethod)
            }
        } catch (e: Exception) {
            val result = if (patchMethod?.isAborted == true) {
                RemoteOperationResult<Long>(OperationCancelledException())
            } else RemoteOperationResult<Long>(e)
            Timber.e(result.exception, "Patch TUS upload chunk failed: ${result.logMessage}")
            result
        }

    fun addDataTransferProgressListener(listener: OnDatatransferProgressListener) {
        synchronized(dataTransferListeners) { dataTransferListeners.add(listener) }
    }

    fun removeDataTransferProgressListener(listener: OnDatatransferProgressListener) {
        synchronized(dataTransferListeners) { dataTransferListeners.remove(listener) }
    }

    fun cancel() {
        synchronized(cancellationRequested) {
            cancellationRequested.set(true)
            patchMethod?.abort()
        }
    }

    private fun isSuccess(status: Int): Boolean =
        status.isOneOf(HttpConstants.HTTP_OK, HttpConstants.HTTP_NO_CONTENT)
}
