package it.pagopa.wallet.domain.wallets

import it.pagopa.generated.wallet.model.WalletNotificationRequestDto.OperationResultEnum
import it.pagopa.generated.wallet.model.WalletStatusDto
import it.pagopa.wallet.WalletTestUtils
import it.pagopa.wallet.WalletTestUtils.TEST_DEFAULT_CLIENTS
import it.pagopa.wallet.domain.wallets.details.CardDetails
import java.time.Instant
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow

class WalletTest {

    @Test
    fun `can construct wallet with empty applications and null details`() {
        assertDoesNotThrow {
            Wallet(
                id = WalletTestUtils.WALLET_UUID,
                userId = WalletTestUtils.USER_ID,
                status = WalletStatusDto.CREATED,
                paymentMethodId = WalletTestUtils.PAYMENT_METHOD_ID_CARDS,
                applications = listOf(),
                contractId = WalletTestUtils.CONTRACT_ID,
                validationOperationResult = OperationResultEnum.EXECUTED,
                validationErrorCode = null,
                details = null,
                clients = TEST_DEFAULT_CLIENTS,
                version = 0,
                creationDate = WalletTestUtils.creationDate,
                updateDate = WalletTestUtils.creationDate,
                onboardingChannel = OnboardingChannel.IO
            )
        }
    }

    @Test
    fun `can construct wallet with applications and null details`() {
        assertDoesNotThrow {
            Wallet(
                id = WalletTestUtils.WALLET_UUID,
                userId = WalletTestUtils.USER_ID,
                status = WalletStatusDto.CREATED,
                paymentMethodId = WalletTestUtils.PAYMENT_METHOD_ID_CARDS,
                applications =
                    listOf(
                        WalletApplication(
                            WalletTestUtils.WALLET_APPLICATION_ID,
                            WalletApplicationStatus.DISABLED,
                            Instant.now(),
                            Instant.now(),
                            WalletTestUtils.APPLICATION_METADATA
                        )
                    ),
                contractId = WalletTestUtils.CONTRACT_ID,
                validationOperationResult = OperationResultEnum.EXECUTED,
                validationErrorCode = null,
                details = null,
                clients = TEST_DEFAULT_CLIENTS,
                version = 0,
                creationDate = WalletTestUtils.creationDate,
                updateDate = WalletTestUtils.creationDate,
                onboardingChannel = OnboardingChannel.IO
            )
        }
    }

    @Test
    fun `can construct wallet with applications and card details`() {

        assertDoesNotThrow {
            Wallet(
                id = WalletTestUtils.WALLET_UUID,
                userId = WalletTestUtils.USER_ID,
                status = WalletStatusDto.CREATED,
                paymentMethodId = WalletTestUtils.PAYMENT_METHOD_ID_CARDS,
                applications =
                    listOf(
                        WalletApplication(
                            WalletTestUtils.WALLET_APPLICATION_ID,
                            WalletApplicationStatus.DISABLED,
                            Instant.now(),
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
                        WalletTestUtils.LAST_FOUR_DIGITS,
                        WalletTestUtils.EXP_DATE,
                        WalletTestUtils.BRAND,
                        WalletTestUtils.PAYMENT_INSTRUMENT_GATEWAY_ID
                    ),
                clients = TEST_DEFAULT_CLIENTS,
                version = 0,
                creationDate = WalletTestUtils.creationDate,
                updateDate = WalletTestUtils.creationDate,
                onboardingChannel = OnboardingChannel.IO
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
                applications =
                    listOf(
                        WalletApplication(
                            WalletTestUtils.WALLET_APPLICATION_ID,
                            WalletApplicationStatus.DISABLED,
                            Instant.now(),
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
                        WalletTestUtils.LAST_FOUR_DIGITS,
                        WalletTestUtils.EXP_DATE,
                        WalletTestUtils.BRAND,
                        WalletTestUtils.PAYMENT_INSTRUMENT_GATEWAY_ID
                    ),
                clients = TEST_DEFAULT_CLIENTS,
                version = 0,
                creationDate = WalletTestUtils.creationDate,
                updateDate = WalletTestUtils.creationDate,
                onboardingChannel = OnboardingChannel.IO
            )
        }
        assert(WalletTestUtils.walletDomain() == WalletTestUtils.walletDocument().toDomain())
    }
}
