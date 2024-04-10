package it.pagopa.wallet.domain.wallets

import it.pagopa.generated.wallet.model.ClientIdDto
import it.pagopa.generated.wallet.model.WalletNotificationRequestDto.OperationResultEnum
import it.pagopa.generated.wallet.model.WalletStatusDto
import it.pagopa.wallet.WalletTestUtils
import it.pagopa.wallet.domain.wallets.details.CardDetails
import java.time.Instant
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource

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
                version = 0,
                creationDate = WalletTestUtils.creationDate,
                updateDate = WalletTestUtils.creationDate,
                onboardingChannel = OnboardingChannel.IO
            )
        }
        assert(WalletTestUtils.walletDomain() == WalletTestUtils.walletDocument().toDomain())
    }

    @ParameterizedTest
    @EnumSource(ClientIdDto::class)
    fun `updateUsageForClient adds all metadata usage keys`(clientId: ClientIdDto) {
        val updateTime = Instant.now()
        val wallet = WalletTestUtils.walletDomain().updateUsageForClient(clientId, updateTime)
        val pagopaApplication =
            wallet.applications.find { it.id == WalletApplicationId("PAGOPA") }!!

        val usageKeys =
            setOf(
                WalletApplicationMetadata.Metadata.LAST_USED_CHECKOUT,
                WalletApplicationMetadata.Metadata.LAST_USED_IO
            )

        assert(pagopaApplication.metadata.data.keys.containsAll(usageKeys))
    }

    @ParameterizedTest
    @EnumSource(ClientIdDto::class)
    fun `updateUsageForClient updates only relevant client for wallet never used`(
        clientId: ClientIdDto
    ) {
        val walletBeforeUpdate = WalletTestUtils.walletDomain()
        val updateTime = Instant.now()
        val otherApplications =
            walletBeforeUpdate.applications.filter { it.id != WalletApplicationId("PAGOPA") }

        val wallet = walletBeforeUpdate.updateUsageForClient(clientId, updateTime)
        val pagopaApplication =
            wallet.applications.find { it.id == WalletApplicationId("PAGOPA") }!!
        val nonUpdatedApplications =
            wallet.applications.filter { it.id != WalletApplicationId("PAGOPA") }

        val usageFieldMap =
            mapOf(
                ClientIdDto.CHECKOUT to WalletApplicationMetadata.Metadata.LAST_USED_CHECKOUT,
                ClientIdDto.IO to WalletApplicationMetadata.Metadata.LAST_USED_IO,
            )

        assertEquals(
            updateTime.toString(),
            pagopaApplication.metadata.data[usageFieldMap[clientId]]
        )

        assertEquals(
            otherApplications,
            nonUpdatedApplications,
            "`Wallet#updateUsageForClient` updated non-PAGOPA application!"
        )

        val otherClients = usageFieldMap.keys - clientId
        for (client in otherClients) {
            assertNull(pagopaApplication.metadata.data[usageFieldMap[client]], "client=$client")
        }
    }
}
