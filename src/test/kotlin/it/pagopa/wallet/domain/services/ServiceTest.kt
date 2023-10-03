package it.pagopa.wallet.domain.services

import it.pagopa.wallet.WalletTestUtils.SERVICE_ID
import it.pagopa.wallet.WalletTestUtils.SERVICE_NAME
import java.time.Instant
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow

class ServiceTest {
    @Test
    fun `can construct service`() {
        assertDoesNotThrow {
            Service(SERVICE_ID, SERVICE_NAME, ServiceStatus.DISABLED, Instant.now())
        }
    }
}
