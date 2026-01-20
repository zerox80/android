/**
 * openCloud Android client application
 *
 * @author Abel García de Prada
 * Copyright (C) 2021 ownCloud GmbH.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License version 2,
 * as published by the Free Software Foundation.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package eu.opencloud.android.data.oauth.datasources.implementation

import eu.opencloud.android.data.ClientManager
import eu.opencloud.android.data.oauth.OC_REMOTE_CLIENT_REGISTRATION_RESPONSE
import eu.opencloud.android.data.oauth.OC_REMOTE_OIDC_DISCOVERY_RESPONSE
import eu.opencloud.android.data.oauth.OC_REMOTE_TOKEN_RESPONSE
import eu.opencloud.android.lib.common.OpenCloudClient
import eu.opencloud.android.lib.common.operations.RemoteOperationResult
import eu.opencloud.android.lib.resources.oauth.responses.ClientRegistrationResponse
import eu.opencloud.android.lib.resources.oauth.responses.OIDCDiscoveryResponse
import eu.opencloud.android.lib.resources.oauth.responses.TokenResponse
import eu.opencloud.android.lib.resources.oauth.services.OIDCService
import eu.opencloud.android.testutil.OC_SECURE_BASE_URL
import eu.opencloud.android.testutil.oauth.OC_CLIENT_REGISTRATION
import eu.opencloud.android.testutil.oauth.OC_CLIENT_REGISTRATION_REQUEST
import eu.opencloud.android.testutil.oauth.OC_OIDC_SERVER_CONFIGURATION
import eu.opencloud.android.testutil.oauth.OC_TOKEN_REQUEST_ACCESS
import eu.opencloud.android.testutil.oauth.OC_TOKEN_REQUEST_ACCESS_PUBLIC_CLIENT
import eu.opencloud.android.testutil.oauth.OC_TOKEN_REQUEST_REFRESH_PUBLIC_CLIENT
import eu.opencloud.android.testutil.oauth.OC_TOKEN_RESPONSE
import eu.opencloud.android.utils.createRemoteOperationResultMock
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class OCRemoteOAuthDataSourceTest {
    private lateinit var remoteOAuthDataSource: OCRemoteOAuthDataSource

    private val clientManager: ClientManager = mockk(relaxed = true)
    private val ocClientMocked: OpenCloudClient = mockk()

    private val oidcService: OIDCService = mockk()

    @Before
    fun setUp() {
        every { clientManager.getClientForAnonymousCredentials(any(), any()) } returns ocClientMocked

        remoteOAuthDataSource = OCRemoteOAuthDataSource(
            clientManager = clientManager,
            oidcService = oidcService,
        )
    }

    @Test
    fun `performOIDCDiscovery returns a OIDCServerConfiguration`() {
        val oidcDiscoveryResult: RemoteOperationResult<OIDCDiscoveryResponse> =
            createRemoteOperationResultMock(data = OC_REMOTE_OIDC_DISCOVERY_RESPONSE, isSuccess = true)

        every {
            oidcService.getOIDCServerDiscovery(ocClientMocked)
        } returns oidcDiscoveryResult

        val oidcDiscovery = remoteOAuthDataSource.performOIDCDiscovery(OC_SECURE_BASE_URL)

        assertNotNull(oidcDiscovery)
        assertEquals(OC_OIDC_SERVER_CONFIGURATION, oidcDiscovery)

        verify(exactly = 1) {
            clientManager.getClientForAnonymousCredentials(OC_SECURE_BASE_URL, false)
            oidcService.getOIDCServerDiscovery(ocClientMocked)
        }
    }

    @Test
    fun `performTokenRequest returns a TokenResponse`() {
        val tokenResponseResult: RemoteOperationResult<TokenResponse> =
            createRemoteOperationResultMock(data = OC_REMOTE_TOKEN_RESPONSE, isSuccess = true)

        every {
            oidcService.performTokenRequest(ocClientMocked, any())
        } returns tokenResponseResult

        val tokenResponse = remoteOAuthDataSource.performTokenRequest(OC_TOKEN_REQUEST_ACCESS)

        assertNotNull(tokenResponse)
        assertEquals(OC_TOKEN_RESPONSE, tokenResponse)

        verify(exactly = 1) {
            clientManager.getClientForAnonymousCredentials(OC_SECURE_BASE_URL, any())
            oidcService.performTokenRequest(ocClientMocked, any())
        }
    }

    /**
     * Test for public PKCE clients (RFC 7636).
     * Public clients MUST NOT send Authorization header during token exchange.
     * This test verifies that token requests work correctly with empty clientAuth.
     */
    @Test
    fun `performTokenRequest with public PKCE client returns a TokenResponse`() {
        val tokenResponseResult: RemoteOperationResult<TokenResponse> =
            createRemoteOperationResultMock(data = OC_REMOTE_TOKEN_RESPONSE, isSuccess = true)

        every {
            oidcService.performTokenRequest(ocClientMocked, any())
        } returns tokenResponseResult

        // Use the public client fixture which has empty clientAuth
        assertTrue(
            "clientAuth should be empty for public clients",
            OC_TOKEN_REQUEST_ACCESS_PUBLIC_CLIENT.clientAuth.isEmpty()
        )

        val tokenResponse = remoteOAuthDataSource.performTokenRequest(OC_TOKEN_REQUEST_ACCESS_PUBLIC_CLIENT)

        assertNotNull(tokenResponse)
        assertEquals(OC_TOKEN_RESPONSE, tokenResponse)

        verify(exactly = 1) {
            clientManager.getClientForAnonymousCredentials(OC_SECURE_BASE_URL, any())
            oidcService.performTokenRequest(ocClientMocked, any())
        }
    }

    /**
     * Test for public PKCE clients (RFC 7636) using refresh token.
     * Public clients MUST NOT send Authorization header during token refresh.
     * This test verifies that refresh token requests work correctly with empty clientAuth.
     */
    @Test
    fun `performTokenRequest with public PKCE client refresh token returns a TokenResponse`() {
        val tokenResponseResult: RemoteOperationResult<TokenResponse> =
            createRemoteOperationResultMock(data = OC_REMOTE_TOKEN_RESPONSE, isSuccess = true)

        every {
            oidcService.performTokenRequest(ocClientMocked, any())
        } returns tokenResponseResult

        // Verify the refresh token fixture has empty clientAuth
        assertTrue(
            "clientAuth should be empty for public clients",
            OC_TOKEN_REQUEST_REFRESH_PUBLIC_CLIENT.clientAuth.isEmpty()
        )

        val tokenResponse = remoteOAuthDataSource.performTokenRequest(OC_TOKEN_REQUEST_REFRESH_PUBLIC_CLIENT)

        assertNotNull(tokenResponse)
        assertEquals(OC_TOKEN_RESPONSE, tokenResponse)

        verify(exactly = 1) {
            clientManager.getClientForAnonymousCredentials(OC_SECURE_BASE_URL, any())
            oidcService.performTokenRequest(ocClientMocked, any())
        }
    }

    /**
     * RFC 7636 compliance verification:
     * This test ensures that our public client test fixtures correctly have empty clientAuth,
     * which means TokenRequestRemoteOperation will NOT add an Authorization header.
     * The actual header logic is in TokenRequestRemoteOperation:
     *   if (tokenRequestParams.clientAuth.isNotEmpty()) {
     *       postMethod.addRequestHeader(AUTHORIZATION_HEADER, tokenRequestParams.clientAuth)
     *   }
     */
    @Test
    fun `public PKCE client fixtures have empty clientAuth preventing Authorization header`() {
        // Verify access token fixture
        assertTrue(
            "Access token public client fixture should have empty clientAuth",
            OC_TOKEN_REQUEST_ACCESS_PUBLIC_CLIENT.clientAuth.isEmpty()
        )

        // Verify refresh token fixture
        assertTrue(
            "Refresh token public client fixture should have empty clientAuth",
            OC_TOKEN_REQUEST_REFRESH_PUBLIC_CLIENT.clientAuth.isEmpty()
        )

        // Verify confidential client fixtures have non-empty clientAuth (for comparison)
        assertTrue(
            "Confidential client fixture should have non-empty clientAuth",
            OC_TOKEN_REQUEST_ACCESS.clientAuth.isNotEmpty()
        )
    }

    @Test
    fun `registerClient returns a ClientRegistrationInfo`() {
        val clientRegistrationResponse: RemoteOperationResult<ClientRegistrationResponse> =
            createRemoteOperationResultMock(data = OC_REMOTE_CLIENT_REGISTRATION_RESPONSE, isSuccess = true)

        every {
            oidcService.registerClientWithRegistrationEndpoint(ocClientMocked, any())
        } returns clientRegistrationResponse

        val clientRegistrationInfo = remoteOAuthDataSource.registerClient(OC_CLIENT_REGISTRATION_REQUEST)

        assertNotNull(clientRegistrationInfo)
        assertEquals(OC_CLIENT_REGISTRATION, clientRegistrationInfo)

        verify(exactly = 1) {
            clientManager.getClientForAnonymousCredentials(OC_CLIENT_REGISTRATION_REQUEST.registrationEndpoint, false)
            oidcService.registerClientWithRegistrationEndpoint(ocClientMocked, any())
        }
    }
}
