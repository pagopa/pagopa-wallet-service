package it.pagopa.wallet.document.wallets

import it.pagopa.generated.wallet.model.WalletNotificationRequestDto
import it.pagopa.generated.wallet.model.WalletStatusDto
import it.pagopa.wallet.WalletTestUtils
import it.pagopa.wallet.domain.wallets.ContractId
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

    @Test
    fun `can convert document to domain object`() {
        val walletDoc = WalletTestUtils.walletDocument()
        assert(walletDoc == walletDoc)
        assert(WalletTestUtils.walletDocument() == WalletTestUtils.walletDocument())
        assert(WalletTestUtils.walletDocument() == WalletTestUtils.walletDomain().toDocument())
        assert(
            WalletTestUtils.walletDocument().hashCode() ==
                WalletTestUtils.walletDomain().toDocument().hashCode()
        )
        assert(!WalletTestUtils.walletDocument().equals(WalletTestUtils.walletDomain()))
        assert(
            WalletTestUtils.walletDocument().toDomain() !=
                WalletTestUtils.walletDomain().status(WalletStatusDto.ERROR)
        )
        assert(
            WalletTestUtils.walletDocument().toDomain() !=
                WalletTestUtils.walletDomain().contractId(ContractId("ctrId"))
        )
        assert(
            WalletTestUtils.walletDocument().toDomain() !=
                WalletTestUtils.walletDomain().applications(listOf())
        )
        assert(
            WalletTestUtils.walletDocument().toDomain() !=
                WalletTestUtils.walletDomain()
                    .validationOperationResult(
                        WalletNotificationRequestDto.OperationResultEnum.VOIDED
                    )
        )
    }
}
