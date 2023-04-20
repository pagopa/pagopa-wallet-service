package it.pagopa.wallet.config

import it.pagopa.generated.npg.auth.ApiKeyAuth
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class WebClientConfigTest {

    @Test
    fun `Should build api client successfully with valued api key`() {
        val apiKey = "apiKey"
        val apiClient = WebClientConfig().npgClient("http://localhost", 1000, 1000, apiKey)
        assertEquals(
            apiKey,
            (apiClient.apiClient.getAuthentication("ApiKeyAuth") as ApiKeyAuth).apiKey
        )
    }
}
