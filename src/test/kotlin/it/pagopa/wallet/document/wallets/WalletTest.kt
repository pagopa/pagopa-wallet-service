package it.pagopa.wallet.document.wallets

import it.pagopa.generated.wallet.model.WalletNotificationRequestDto.OperationTypeEnum
import it.pagopa.wallet.WalletTestUtils
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test

class WalletTest {

    @Test
    fun `can build wallet document`() {
        assertNotNull(WalletTestUtils.walletDocumentEmptyApplicationsNullDetails())
        assertNotNull(WalletTestUtils.walletDocumentNullDetails())
        assertNotNull(WalletTestUtils.walletDocument())
        assertNotNull(WalletTestUtils.walletDocumentEmptyContractId())
        assertNotNull(WalletTestUtils.walletDocumentWithEmptyValidationOperationResult())
        assertEquals(WalletTestUtils.walletDocument(), WalletTestUtils.walletDomain().toDocument())
    }

    @Test
    fun `can map wallet validation operation type`() {
        val walletDocument =
            WalletTestUtils.walletDocument().copy(validationOperationType = "CARD_VERIFICATION")

        assertEquals(
            OperationTypeEnum.CARD_VERIFICATION, walletDocument.toDomain().validationOperationType)
        assertEquals(
            "CARD_VERIFICATION", walletDocument.toDomain().toDocument().validationOperationType)
    }
}
