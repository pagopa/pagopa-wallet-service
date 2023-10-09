package it.pagopa.wallet.document.services

import it.pagopa.wallet.WalletTestUtils
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test

class ServiceTest {

    @Test
    fun `can build service document`() {
        Assertions.assertNotNull(WalletTestUtils.SERVICE_DOCUMENT)
    }
}
