package it.pagopa.wallet.document.wallets

import it.pagopa.wallet.WalletTestUtils
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test

class WalletTest {

    @Test
    fun `can build wallet document`() {
        assertNotNull(WalletTestUtils.WALLET_DOCUMENT_EMPTY_SERVICES_NULL_DETAILS)
        assertNotNull(WalletTestUtils.WALLET_DOCUMENT_NULL_DETAILS)
        assertNotNull(WalletTestUtils.WALLET_DOCUMENT)
    }
}
