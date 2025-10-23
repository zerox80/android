/* openCloud Android Library is available under MIT license
 *
 *   @author Abel García de Prada
 *
 *   Copyright (C) 2020 ownCloud GmbH.
 *
 *   Permission is hereby granted, free of charge, to any person obtaining a copy
 *   of this software and associated documentation files (the "Software"), to deal
 *   in the Software without restriction, including without limitation the rights
 *   to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 *   copies of the Software, and to permit persons to whom the Software is
 *   furnished to do so, subject to the following conditions:
 *
 *   The above copyright notice and this permission notice shall be included in
 *   all copies or substantial portions of the Software.
 *
 *   THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND,
 *   EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF
 *   MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND
 *   NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS
 *   BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN
 *   ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN
 *   CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 *   THE SOFTWARE.
 *
 */
package eu.opencloud.android.lib.resources.oauth

import eu.opencloud.android.lib.common.OpenCloudClient
import eu.opencloud.android.lib.common.http.HttpConstants.AUTHORIZATION_HEADER
import eu.opencloud.android.lib.common.http.HttpConstants.HTTP_OK
import eu.opencloud.android.lib.common.http.methods.nonwebdav.PostMethod
import eu.opencloud.android.lib.common.operations.RemoteOperation
import eu.opencloud.android.lib.common.operations.RemoteOperationResult
import eu.opencloud.android.lib.resources.oauth.params.TokenRequestParams
import eu.opencloud.android.lib.resources.oauth.responses.TokenResponse
import com.squareup.moshi.JsonAdapter
import com.squareup.moshi.Moshi
import timber.log.Timber
import java.net.URL

/**
 * Perform token request
 *
 * @author Abel García de Prada
 */
class TokenRequestRemoteOperation(
    private val tokenRequestParams: TokenRequestParams
) : RemoteOperation<TokenResponse>() {

    override fun run(client: OpenCloudClient): RemoteOperationResult<TokenResponse> {
        try {
            val requestBody = tokenRequestParams.toRequestBody()

            val postMethod = PostMethod(URL(tokenRequestParams.tokenEndpoint), requestBody)

            tokenRequestParams.clientAuth?.takeIf { it.isNotBlank() }?.let { clientAuth ->
                postMethod.addRequestHeader(AUTHORIZATION_HEADER, clientAuth)
            }

            val status = client.executeHttpMethod(postMethod)

            val responseBody = postMethod.getResponseBodyAsString()

            return if (status == HTTP_OK && responseBody != null) {
                Timber.d("Successful response $responseBody")

                // Parse the response
                val moshi: Moshi = Moshi.Builder().build()
                val jsonAdapter: JsonAdapter<TokenResponse> = moshi.adapter(TokenResponse::class.java)
                val tokenResponse: TokenResponse? = jsonAdapter.fromJson(responseBody)
                Timber.d("Get tokens completed and parsed to $tokenResponse")

                RemoteOperationResult<TokenResponse>(RemoteOperationResult.ResultCode.OK).apply {
                    data = tokenResponse
                }

            } else {
                Timber.e("Failed response while getting tokens from the server status code: $status; response message: $responseBody")
                RemoteOperationResult<TokenResponse>(postMethod)
            }

        } catch (e: Exception) {
            Timber.e(e, "Exception while getting tokens")
            return RemoteOperationResult<TokenResponse>(e)

        }
    }
}
