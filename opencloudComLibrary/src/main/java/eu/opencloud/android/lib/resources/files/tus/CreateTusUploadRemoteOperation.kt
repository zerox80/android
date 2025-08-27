package eu.opencloud.android.lib.resources.files.tus

import android.util.Base64
import eu.opencloud.android.lib.common.OpenCloudClient
import eu.opencloud.android.lib.common.http.HttpConstants
import eu.opencloud.android.lib.common.http.methods.nonwebdav.HeadMethod
import eu.opencloud.android.lib.common.http.methods.nonwebdav.PostMethod
import eu.opencloud.android.lib.common.network.ChunkFromFileRequestBody
import eu.opencloud.android.lib.common.network.WebdavUtils
import eu.opencloud.android.lib.common.operations.RemoteOperation
import eu.opencloud.android.lib.common.operations.RemoteOperationResult
import eu.opencloud.android.lib.common.operations.RemoteOperationResult.ResultCode
import eu.opencloud.android.lib.common.utils.isOneOf
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import timber.log.Timber
import java.io.File
import java.io.FileInputStream
import java.io.RandomAccessFile
import java.net.URL
import java.nio.channels.FileChannel
import java.security.MessageDigest

/**
 * TUS Create Upload operation (POST)
 * Creates a new upload resource directly to the target file path (iOS SDK style).
 * Returns the Location URL in the result data.
 */
class CreateTusUploadRemoteOperation(
    private val file: File,
    private val remotePath: String,
    private val mimetype: String,
    private val metadata: Map<String, String>,
    private val useCreationWithUpload: Boolean,
    private val firstChunkSize: Long?,
    private val tusUrl: String,
    private val collectionUrlOverride: String? = null,
) : RemoteOperation<String>() {

    override fun run(client: OpenCloudClient): RemoteOperationResult<String> {
        return try {
            // Determine TUS endpoint URL based on provided parameters
            val targetFileUrl = when {
                // 1. Use collectionUrlOverride if provided (for TUS support checks)
                !collectionUrlOverride.isNullOrBlank() -> collectionUrlOverride
                
                // 2. Use tusUrl if provided and not empty (for existing upload sessions)
                !tusUrl.isNullOrBlank() -> tusUrl
                
                // 3. Construct TUS endpoint URL to parent directory (server expects directory, not file)
                else -> {
                    val parentDir = remotePath.substringBeforeLast('/', "/")
                    buildString {
                        append(client.baseUri.toString().trimEnd('/'))
                        append("/remote.php/webdav")
                        append(WebdavUtils.encodePath(parentDir))
                        if (!endsWith("/")) append("/")
                    }
                }
            }

            Timber.d("TUS Creation URL: %s", targetFileUrl)
            
            // TUS Upload-Metadata (decoded for debugging)
            val filename = remotePath.substringAfterLast('/')
            val mtime = (file.lastModified() / 1000).toString()
            val checksum = "sha1 " + computeSha1(file)
            Timber.d("TUS Metadata: filename='%s', mtime='%s', checksum='%s'", filename, mtime, checksum)
            
            // Check if target file already exists (which might cause HTTP 412)
            Timber.d("TUS: Checking if target file exists at %s", targetFileUrl)
            try {
                val headMethod = HeadMethod(URL(targetFileUrl))
                val headStatus = client.executeHttpMethod(headMethod)
                if (headStatus == 200) {
                    Timber.w("TUS: Target file already exists (HEAD returned 200) - this might cause HTTP 412")
                } else {
                    Timber.d("TUS: Target file does not exist (HEAD returned %d)", headStatus)
                }
            } catch (e: Exception) {
                Timber.d("TUS: HEAD request failed: %s", e.message)
            }

            // Prepare request body first
            val postBody: RequestBody = if (useCreationWithUpload && (firstChunkSize ?: 0L) > 0L) {
                // creation-with-upload: include first chunk
                RandomAccessFile(file, "r").use { raf ->
                    val channel: FileChannel = raf.channel
                    ChunkFromFileRequestBody(
                        file = file,
                        contentType = HttpConstants.CONTENT_TYPE_OFFSET_OCTET_STREAM.toMediaTypeOrNull(),
                        channel = channel,
                        chunkSize = firstChunkSize!!
                    )
                }
            } else {
                // creation only: empty body
                ByteArray(0).toRequestBody(null)
            }

            val postMethod = PostMethod(URL(targetFileUrl), postBody)

            // Set TUS headers
            postMethod.setRequestHeader(HttpConstants.TUS_RESUMABLE, "1.0.0")
            postMethod.setRequestHeader(HttpConstants.UPLOAD_LENGTH, file.length().toString())
            postMethod.setRequestHeader(HttpConstants.CONTENT_TYPE_HEADER, HttpConstants.CONTENT_TYPE_OFFSET_OCTET_STREAM)
            
            // Set TUS-Extension header to indicate which extensions we want to use
            val extensions = if (useCreationWithUpload && (firstChunkSize ?: 0L) > 0L) {
                "creation,creation-with-upload"
            } else {
                "creation"
            }
            postMethod.setRequestHeader(HttpConstants.TUS_EXTENSION, extensions)

            // Prepare Upload-Metadata like iOS SDK
            val allMetadata = mutableMapOf<String, String>()
            allMetadata["filename"] = remotePath.substringAfterLast('/')
            allMetadata["mtime"] = (file.lastModified() / 1000).toString()
            allMetadata["checksum"] = "sha1 " + computeSha1(file)
            
            postMethod.setRequestHeader(HttpConstants.UPLOAD_METADATA, encodeTusMetadata(allMetadata))

            // Set Upload-Offset for creation-with-upload
            if (useCreationWithUpload && (firstChunkSize ?: 0L) > 0L) {
                postMethod.setRequestHeader(HttpConstants.UPLOAD_OFFSET, "0")
            }

            val status = client.executeHttpMethod(postMethod)
            Timber.d("TUS Creation [%s] - %d%s", targetFileUrl, status, if (!isSuccess(status)) " (FAIL)" else "")
            
            // Debug logging for troubleshooting
            if (status == 412) {
                Timber.w("HTTP 412 Precondition Failed - Request headers:")
                Timber.w("  Tus-Resumable: %s", postMethod.getRequestHeader(HttpConstants.TUS_RESUMABLE))
                Timber.w("  Tus-Extension: %s", postMethod.getRequestHeader(HttpConstants.TUS_EXTENSION))
                Timber.w("  Upload-Length: %s", postMethod.getRequestHeader(HttpConstants.UPLOAD_LENGTH))
                Timber.w("  Upload-Metadata: %s", postMethod.getRequestHeader(HttpConstants.UPLOAD_METADATA))
                Timber.w("  Content-Type: %s", postMethod.getRequestHeader(HttpConstants.CONTENT_TYPE_HEADER))
                Timber.w("  Content-Length: %d", postBody.contentLength())
                if (useCreationWithUpload && (firstChunkSize ?: 0L) > 0L) {
                    Timber.w("  Upload-Offset: %s", postMethod.getRequestHeader(HttpConstants.UPLOAD_OFFSET))
                }
            }

            if (isSuccess(status)) {
                val locationHeader = postMethod.getResponseHeader(HttpConstants.LOCATION_HEADER)
                    ?: postMethod.getResponseHeader(HttpConstants.LOCATION_HEADER_LOWER)
                
                val base = URL(postMethod.getFinalUrl().toString())
                val resolved = resolveLocationToAbsolute(locationHeader, base)

                if (resolved != null) {
                    Timber.d("TUS upload resource created: %s", resolved)
                    RemoteOperationResult<String>(ResultCode.OK).apply { data = resolved }
                } else {
                    Timber.e("Location header is missing in TUS creation response")
                    RemoteOperationResult<String>(IllegalStateException("Location header missing")).apply { 
                        data = ""
                    }
                }
            } else {
                Timber.w("TUS creation failed with status: %d", status)
                RemoteOperationResult<String>(postMethod).apply { data = "" }
            }
        } catch (e: Exception) {
            val result = RemoteOperationResult<String>(e)
            Timber.e(e, "TUS creation operation failed")
            result
        }
    }

    private fun isSuccess(status: Int) = 
        status.isOneOf(HttpConstants.HTTP_CREATED, HttpConstants.HTTP_OK)

    private fun encodeTusMetadata(metadata: Map<String, String>): String =
        metadata.entries.joinToString(",") { (key, value) ->
            val encoded = Base64.encodeToString(value.toByteArray(Charsets.UTF_8), Base64.NO_WRAP)
            "$key $encoded"
        }

    private fun resolveLocationToAbsolute(location: String?, base: URL): String? {
        if (location.isNullOrBlank()) return null
        return try {
            URL(base, location).toString()
        } catch (e: Exception) {
            Timber.w(e, "Failed to resolve Location header: %s", location)
            null
        }
    }

    private fun computeSha1(file: File): String {
        val digest = MessageDigest.getInstance("SHA-1")
        FileInputStream(file).use { fis ->
            val buffer = ByteArray(8192)
            var bytesRead: Int
            while (fis.read(buffer).also { bytesRead = it } != -1) {
                digest.update(buffer, 0, bytesRead)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }
}
