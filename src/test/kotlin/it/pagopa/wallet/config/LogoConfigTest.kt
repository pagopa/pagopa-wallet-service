package it.pagopa.wallet.config

import java.net.URI
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class LogoConfigTest {

    private val logoMapping: Map<String, URI> =
        mapOf(
            "VISA" to URI.create("http://visa"),
            "MASTERCARD" to URI.create("http://mastercard"),
            "PAYPAL" to URI.create("http://paypal"),
            "UNKNOWN" to URI.create("http://unknown")
        )

    @Test
    fun `should build logo mapping successfully`() {
        val logoMap = LogoConfig().walletLogoMapping(logoMapping.mapValues { it.value.toString() })
        assertEquals(logoMap, logoMapping)
    }

    @Test
    fun `should throw exception for missing logo mapping`() {
        val exception =
            assertThrows<IllegalStateException> {
                LogoConfig()
                    .walletLogoMapping(
                        logoMapping.filter { it.key != "VISA" }.mapValues { it.value.toString() }
                    )
            }

        assertEquals(
            "Invalid logo configuration map, missing logo entries for the following keys: [VISA]",
            exception.message
        )
    }

    @Test
    fun `should throw exception for misconfigured logo url`() {
        assertThrows<IllegalArgumentException> {
            LogoConfig().walletLogoMapping(logoMapping.mapValues { "INVALID_URL_${it.value}" })
        }
    }
}
