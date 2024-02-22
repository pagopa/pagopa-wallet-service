package it.pagopa.wallet.domain.wallets

import it.pagopa.generated.wallet.model.WalletNotificationRequestDto.OperationResultEnum
import it.pagopa.generated.wallet.model.WalletStatusDto
import it.pagopa.wallet.WalletTestUtils
import it.pagopa.wallet.domain.services.ServiceStatus
import it.pagopa.wallet.domain.wallets.details.CardDetails
import java.time.Instant
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow

class WalletTest {

    @Test
    fun `can construct wallet with empty services and null details`() {
        assertDoesNotThrow {
            Wallet(
                id = WalletTestUtils.WALLET_UUID,
                userId = WalletTestUtils.USER_ID,
                status = WalletStatusDto.CREATED,
                paymentMethodId = WalletTestUtils.PAYMENT_METHOD_ID_CARDS,
                paymentInstrumentId = WalletTestUtils.PAYMENT_INSTRUMENT_ID,
                applications = listOf(),
                contractId = WalletTestUtils.CONTRACT_ID,
                validationOperationResult = OperationResultEnum.EXECUTED,
                validationErrorCode = null,
                details = null,
                version = 0,
                creationDate = WalletTestUtils.creationDate,
                updateDate = WalletTestUtils.creationDate
            )
        }
    }

    @Test
    fun `can construct wallet with services and null details`() {
        assertDoesNotThrow {
            Wallet(
                id = WalletTestUtils.WALLET_UUID,
                userId = WalletTestUtils.USER_ID,
                status = WalletStatusDto.CREATED,
                paymentMethodId = WalletTestUtils.PAYMENT_METHOD_ID_CARDS,
                paymentInstrumentId = WalletTestUtils.PAYMENT_INSTRUMENT_ID,
                applications =
                    listOf(
                        Application(
                            WalletTestUtils.SERVICE_ID,
                            WalletTestUtils.SERVICE_NAME,
                            ServiceStatus.DISABLED,
                            Instant.now(),
                            WalletTestUtils.APPLICATION_METADATA
                        )
                    ),
                contractId = WalletTestUtils.CONTRACT_ID,
                validationOperationResult = OperationResultEnum.EXECUTED,
                validationErrorCode = null,
                details = null,
                version = 0,
                creationDate = WalletTestUtils.creationDate,
                updateDate = WalletTestUtils.creationDate
            )
        }
    }

    @Test
    fun `can construct wallet with services and card details`() {

        assertDoesNotThrow {
            Wallet(
                id = WalletTestUtils.WALLET_UUID,
                userId = WalletTestUtils.USER_ID,
                status = WalletStatusDto.CREATED,
                paymentMethodId = WalletTestUtils.PAYMENT_METHOD_ID_CARDS,
                paymentInstrumentId = WalletTestUtils.PAYMENT_INSTRUMENT_ID,
                applications =
                    listOf(
                        Application(
                            WalletTestUtils.SERVICE_ID,
                            WalletTestUtils.SERVICE_NAME,
                            ServiceStatus.DISABLED,
                            Instant.now(),
                            WalletTestUtils.APPLICATION_METADATA
                        )
                    ),
                contractId = WalletTestUtils.CONTRACT_ID,
                validationOperationResult = OperationResultEnum.EXECUTED,
                validationErrorCode = null,
                details =
                    CardDetails(
                        WalletTestUtils.BIN,
                        WalletTestUtils.MASKED_PAN,
                        WalletTestUtils.EXP_DATE,
                        WalletTestUtils.BRAND,
                        WalletTestUtils.HOLDER_NAME
                    ),
                version = 0,
                creationDate = WalletTestUtils.creationDate,
                updateDate = WalletTestUtils.creationDate
            )
        }
    }

    @Test
    fun `can construct wallet without operation result `() {

        assertDoesNotThrow {
            Wallet(
                id = WalletTestUtils.WALLET_UUID,
                userId = WalletTestUtils.USER_ID,
                status = WalletStatusDto.CREATED,
                paymentMethodId = WalletTestUtils.PAYMENT_METHOD_ID_CARDS,
                paymentInstrumentId = WalletTestUtils.PAYMENT_INSTRUMENT_ID,
                applications =
                    listOf(
                        Application(
                            WalletTestUtils.SERVICE_ID,
                            WalletTestUtils.SERVICE_NAME,
                            ServiceStatus.DISABLED,
                            Instant.now(),
                            WalletTestUtils.APPLICATION_METADATA
                        )
                    ),
                contractId = WalletTestUtils.CONTRACT_ID,
                validationOperationResult = null,
                validationErrorCode = null,
                details =
                    CardDetails(
                        WalletTestUtils.BIN,
                        WalletTestUtils.MASKED_PAN,
                        WalletTestUtils.EXP_DATE,
                        WalletTestUtils.BRAND,
                        WalletTestUtils.HOLDER_NAME
                    ),
                version = 0,
                creationDate = WalletTestUtils.creationDate,
                updateDate = WalletTestUtils.creationDate
            )
        }
        assert(WalletTestUtils.walletDomain() == WalletTestUtils.walletDocument().toDomain())
    }
}
