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
        assert(WalletTestUtils.walletDocument().toDomain().equals(WalletTestUtils.walletDomain()))
        assert(
            !WalletTestUtils.walletDocument()
                .toDomain()
                .equals(WalletTestUtils.walletDomain().status(WalletStatusDto.ERROR))
        )
        assert(
            !WalletTestUtils.walletDocument()
                .toDomain()
                .equals(WalletTestUtils.walletDomain().contractId(ContractId("ctrId")))
        )
        assert(
            !WalletTestUtils.walletDocument()
                .toDomain()
                .equals(WalletTestUtils.walletDomain().applications(listOf()))
        )
        assert(
            !WalletTestUtils.walletDocument()
                .toDomain()
                .equals(
                    WalletTestUtils.walletDomain()
                        .validationOperationResult(
                            WalletNotificationRequestDto.OperationResultEnum.VOIDED
                        )
                )
        )
    }
}
