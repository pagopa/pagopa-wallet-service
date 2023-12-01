package it.pagopa.wallet.document.wallets

import it.pagopa.wallet.WalletTestUtils
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test

class WalletTest {

    @Test
    fun `can build wallet document`() {
        assertNotNull(WalletTestUtils.walletDocumentEmptyServicesNullDetails())
        assertNotNull(WalletTestUtils.walletDocumentNullDetails())
        assertNotNull(WalletTestUtils.walletDocument())
        assertNotNull(WalletTestUtils.walletDocumentEmptyContractId())
        assertNotNull(WalletTestUtils.walletDocumentWithEmptyValidationOperationResult())
    }
}
