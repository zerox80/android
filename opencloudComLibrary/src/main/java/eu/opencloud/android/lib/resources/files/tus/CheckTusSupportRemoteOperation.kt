/* openCloud Android Library is available under MIT license
 * Copyright (C) 2025 ownCloud GmbH.
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND,
 * EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF
 * MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND
 * NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS
 * BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN
 * ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN
 * CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package eu.opencloud.android.lib.resources.files.tus

import eu.opencloud.android.lib.common.OpenCloudClient
import eu.opencloud.android.lib.common.http.HttpConstants
import eu.opencloud.android.lib.common.http.methods.nonwebdav.OptionsMethod
import eu.opencloud.android.lib.common.operations.RemoteOperation
import eu.opencloud.android.lib.common.operations.RemoteOperationResult
import eu.opencloud.android.lib.common.operations.RemoteOperationResult.ResultCode
import eu.opencloud.android.lib.common.utils.isOneOf
import timber.log.Timber
import java.net.URL

/**
 * TUS capability detection (OPTIONS on uploads collection)
 * Returns true when the server advertises Tus-Version 1.0.0 and the 'creation' extension.
 */
class CheckTusSupportRemoteOperation(
    private val collectionUrlOverride: String? = null,
) : RemoteOperation<Boolean>() {

    override fun run(client: OpenCloudClient): RemoteOperationResult<Boolean> =
        try {
            val initial = (collectionUrlOverride ?: client.uploadsWebDavUri.toString()).trim()
            val withSlash = if (initial.endsWith("/")) initial else "$initial/"
            val root = run {
                val idx = initial.indexOf("/uploads/")
                if (idx > 0) initial.substring(0, idx + "/uploads/".length) else withSlash
            }
            val altInitial = initial.replace("/remote.php/dav/", "/dav/")
            val altWithSlash = if (altInitial.endsWith("/")) altInitial else "$altInitial/"
            val altRoot = run {
                val idxDav = altInitial.indexOf("/uploads/")
                if (idxDav > 0) altInitial.substring(0, idxDav + "/uploads/".length) else altWithSlash
            }

            val candidates = linkedSetOf(initial, withSlash, root, altInitial, altWithSlash, altRoot)
            var supported = false
            var lastResult: RemoteOperationResult<Boolean>? = null

            loop@ for (endpoint in candidates) {
                val options = OptionsMethod(URL(endpoint)).apply {
                    setRequestHeader(HttpConstants.TUS_RESUMABLE, HttpConstants.TUS_RESUMABLE_VERSION_1_0_0)
                }
                val status = client.executeHttpMethod(options)
                Timber.d("TUS OPTIONS %s - %d", endpoint, status)
                if (isSuccess(status)) {
                    val version = options.getResponseHeader(HttpConstants.TUS_VERSION) ?: ""
                    val extensions = options.getResponseHeader(HttpConstants.TUS_EXTENSION) ?: ""
                    supported = version.contains(HttpConstants.TUS_RESUMABLE_VERSION_1_0_0) &&
                        extensions.split(',').map { it.trim() }.any { it.equals("creation", ignoreCase = true) || it.equals("creation-with-upload", ignoreCase = true) }
                    Timber.d("TUS supported (headers) at %s: %s, version: %s, extensions: %s", endpoint, supported, version, extensions)
                    if (supported) {
                        lastResult = RemoteOperationResult<Boolean>(ResultCode.OK).apply { data = true }
                        break@loop
                    }

                    // Fallback probe on this endpoint: try POST with minimal test
                    Timber.d("TUS headers inconclusive at %s, probing with POST test", endpoint)
                    val tempFile = java.io.File.createTempFile("tus_probe", ".tmp")
                    tempFile.writeText("test")
                    val probe = CreateTusUploadRemoteOperation(
                        file = tempFile,
                        remotePath = "/tus_probe_test.tmp",
                        mimetype = "text/plain",
                        metadata = emptyMap(),
                        useCreationWithUpload = false,
                        firstChunkSize = null,
                        tusUrl = "",
                        collectionUrlOverride = endpoint,
                    ).execute(client)
                    tempFile.delete()
                    if (probe.isSuccess && !probe.data.isNullOrBlank()) {
                        supported = true
                        // Cleanup
                        try { DeleteTusUploadRemoteOperation(probe.data!!).execute(client) } catch (ignored: Exception) { Timber.w(ignored) }
                        lastResult = RemoteOperationResult<Boolean>(ResultCode.OK).apply { data = true }
                        break@loop
                    }
                } else {
                    lastResult = RemoteOperationResult<Boolean>(options).apply { data = false }
                }
            }

            lastResult ?: RemoteOperationResult<Boolean>(ResultCode.OK).apply { data = supported }
        } catch (e: Exception) {
            val result = RemoteOperationResult<Boolean>(e)
            Timber.w(e, "TUS detection failed, assuming unsupported")
            result.apply { data = false }
        }

    private fun isSuccess(status: Int) =
        status.isOneOf(HttpConstants.HTTP_NO_CONTENT, HttpConstants.HTTP_OK)
}
