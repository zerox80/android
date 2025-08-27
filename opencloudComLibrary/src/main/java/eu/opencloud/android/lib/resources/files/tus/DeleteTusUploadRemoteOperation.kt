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
import eu.opencloud.android.lib.common.http.methods.nonwebdav.DeleteMethod
import eu.opencloud.android.lib.common.operations.RemoteOperation
import eu.opencloud.android.lib.common.operations.RemoteOperationResult
import eu.opencloud.android.lib.common.operations.RemoteOperationResult.ResultCode
import eu.opencloud.android.lib.common.utils.isOneOf
import timber.log.Timber
import java.net.URL

/**
 * TUS Delete Upload operation (DELETE)
 * Deletes an existing upload resource.
 */
class DeleteTusUploadRemoteOperation(
    private val uploadUrl: String,
) : RemoteOperation<Unit>() {

    override fun run(client: OpenCloudClient): RemoteOperationResult<Unit> =
        try {
            val deleteMethod = DeleteMethod(URL(uploadUrl)).apply {
                setRequestHeader(HttpConstants.TUS_RESUMABLE, HttpConstants.TUS_RESUMABLE_VERSION_1_0_0)
            }

            val status = client.executeHttpMethod(deleteMethod)
            Timber.d("Delete TUS upload - $status${if (!isSuccess(status)) "(FAIL)" else ""}")

            if (isSuccess(status)) RemoteOperationResult<Unit>(ResultCode.OK).apply { data = Unit }
            else RemoteOperationResult<Unit>(deleteMethod)
        } catch (e: Exception) {
            val result = RemoteOperationResult<Unit>(e)
            Timber.e(e, "Delete TUS upload failed")
            result
        }

    private fun isSuccess(status: Int): Boolean =
        status.isOneOf(HttpConstants.HTTP_NO_CONTENT, HttpConstants.HTTP_OK)
}
