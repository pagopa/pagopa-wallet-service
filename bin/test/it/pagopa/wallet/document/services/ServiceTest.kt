package it.pagopa.wallet.document.applications

import it.pagopa.wallet.WalletTestUtils
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test

class ApplicationTest {

    @Test
    fun `can build application document`() {
        Assertions.assertNotNull(WalletTestUtils.APPLICATION_DOCUMENT)
    }
}
