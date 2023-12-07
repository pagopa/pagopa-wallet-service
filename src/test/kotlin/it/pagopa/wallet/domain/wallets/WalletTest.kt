package it.pagopa.wallet.domain.wallets

import it.pagopa.generated.wallet.model.WalletNotificationRequestDto.OperationResultEnum
import it.pagopa.generated.wallet.model.WalletStatusDto
import it.pagopa.wallet.WalletTestUtils
import it.pagopa.wallet.domain.details.CardDetails
import it.pagopa.wallet.domain.services.ServiceStatus
import java.time.Instant
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow

class WalletTest {

    @Test
    fun `can construct wallet with empty services and null details`() {
        assertDoesNotThrow {
            Wallet(
                WalletTestUtils.WALLET_UUID,
                WalletTestUtils.USER_ID,
                WalletStatusDto.CREATED,
                WalletTestUtils.PAYMENT_METHOD_ID_CARDS,
                WalletTestUtils.PAYMENT_INSTRUMENT_ID,
                listOf(),
                WalletTestUtils.CONTRACT_ID,
                OperationResultEnum.EXECUTED,
                null
            )
        }
    }

    @Test
    fun `can construct wallet with services and null details`() {
        assertDoesNotThrow {
            Wallet(
                WalletTestUtils.WALLET_UUID,
                WalletTestUtils.USER_ID,
                WalletStatusDto.CREATED,
                WalletTestUtils.PAYMENT_METHOD_ID_CARDS,
                WalletTestUtils.PAYMENT_INSTRUMENT_ID,
                listOf(
                    Application(
                        WalletTestUtils.SERVICE_ID,
                        WalletTestUtils.SERVICE_NAME,
                        ServiceStatus.DISABLED,
                        Instant.now()
                    )
                ),
                WalletTestUtils.CONTRACT_ID,
                OperationResultEnum.EXECUTED,
                null
            )
        }
    }

    @Test
    fun `can construct wallet with services and card details`() {

        assertDoesNotThrow {
            Wallet(
                WalletTestUtils.WALLET_UUID,
                WalletTestUtils.USER_ID,
                WalletStatusDto.CREATED,
                WalletTestUtils.PAYMENT_METHOD_ID_CARDS,
                WalletTestUtils.PAYMENT_INSTRUMENT_ID,
                listOf(
                    Application(
                        WalletTestUtils.SERVICE_ID,
                        WalletTestUtils.SERVICE_NAME,
                        ServiceStatus.DISABLED,
                        Instant.now()
                    )
                ),
                WalletTestUtils.CONTRACT_ID,
                OperationResultEnum.EXECUTED,
                CardDetails(
                    WalletTestUtils.BIN,
                    WalletTestUtils.MASKED_PAN,
                    WalletTestUtils.EXP_DATE,
                    WalletTestUtils.BRAND,
                    WalletTestUtils.HOLDER_NAME
                )
            )
        }
    }

    @Test
    fun `can construct wallet without operation result `() {

        assertDoesNotThrow {
            Wallet(
                WalletTestUtils.WALLET_UUID,
                WalletTestUtils.USER_ID,
                WalletStatusDto.CREATED,
                WalletTestUtils.PAYMENT_METHOD_ID_CARDS,
                WalletTestUtils.PAYMENT_INSTRUMENT_ID,
                listOf(
                    Application(
                        WalletTestUtils.SERVICE_ID,
                        WalletTestUtils.SERVICE_NAME,
                        ServiceStatus.DISABLED,
                        Instant.now()
                    )
                ),
                WalletTestUtils.CONTRACT_ID,
                null,
                CardDetails(
                    WalletTestUtils.BIN,
                    WalletTestUtils.MASKED_PAN,
                    WalletTestUtils.EXP_DATE,
                    WalletTestUtils.BRAND,
                    WalletTestUtils.HOLDER_NAME
                )
            )
        }
    }

    @Test
    fun `can convert domain object to document`() {
        assert(WalletTestUtils.walletDomain().toDocument().equals(WalletTestUtils.walletDocument()))
        assert(
            WalletTestUtils.walletDomain()
                .hashCode()
                .equals(WalletTestUtils.walletDocument().toDomain().hashCode())
        )
        assert(
            !WalletTestUtils.walletDomain()
                .status(WalletStatusDto.ERROR)
                .toDocument()
                .equals(WalletTestUtils.walletDocument())
        )
        assert(
            !WalletTestUtils.walletDomain()
                .applications(listOf())
                .toDocument()
                .equals(WalletTestUtils.walletDocument())
        )
        assert(
            !WalletTestUtils.walletDomain()
                .contractId(ContractId("ctrId"))
                .toDocument()
                .equals(WalletTestUtils.walletDocument())
        )
        assert(
            !WalletTestUtils.walletDomain()
                .validationOperationResult(OperationResultEnum.VOIDED)
                .toDocument()
                .equals(WalletTestUtils.walletDocument())
        )
    }
}
