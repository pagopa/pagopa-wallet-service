package it.pagopa.wallet.domain.wallets

import it.pagopa.generated.wallet.model.ClientIdDto
import it.pagopa.generated.wallet.model.WalletNotificationRequestDto.OperationResultEnum
import it.pagopa.generated.wallet.model.WalletStatusDto
import it.pagopa.wallet.WalletTestUtils
import it.pagopa.wallet.WalletTestUtils.TEST_DEFAULT_CLIENTS
import it.pagopa.wallet.domain.wallets.details.CardDetails
import it.pagopa.wallet.exception.WalletClientConfigurationException
import java.time.Instant
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.function.Executable
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

    @ParameterizedTest
    @EnumSource(Client.WellKnown::class)
    fun `updateUsageForClient updates only relevant client for wallet never used`(
        clientToBeUpdated: Client.WellKnown
    ) {
        val walletBeforeUpdate = WalletTestUtils.walletDomain()
        val updateTime = Instant.now()
        val otherClientsDataBeforeUpdate =
            walletBeforeUpdate.clients.filter { it.key != clientToBeUpdated }

        val wallet =
            walletBeforeUpdate.updateUsageForClient(
                ClientIdDto.valueOf(clientToBeUpdated.name),
                updateTime
            )
        val otherClientsData = wallet.clients.filter { it.key != clientToBeUpdated }

        assertEquals(
            updateTime.toString(),
            wallet.clients[clientToBeUpdated]!!.lastUsage!!.toString()
        )

        assertEquals(
            otherClientsDataBeforeUpdate,
            otherClientsData,
            "`Wallet#updateUsageForClient` updated unexpected client data!"
        )
    }

    @Test
    fun `updateUsageForClient adds client configuration for well-known non-configured client`() {
        val walletBeforeUpdate = WalletTestUtils.walletDomain().copy(clients = mapOf())

        val updatedWallet = walletBeforeUpdate.updateUsageForClient(ClientIdDto.IO, Instant.now())
        assertEquals(setOf(Client.WellKnown.IO), updatedWallet.clients.keys)

        assertAll(
            Client.WellKnown.values().map {
                Executable {
                    assertEquals(Client.Status.ENABLED, updatedWallet.clients[it]!!.status)
                }
            }
        )
    }

    @Test
    fun `updateUsageForClient throws exception on non-configured client`() {
        val walletBeforeUpdate =
            WalletTestUtils.walletDomain()
                .copy(clients = mapOf(Client.WellKnown.IO to Client(Client.Status.ENABLED, null)))

        assertThrows<WalletClientConfigurationException> {
            walletBeforeUpdate.updateUsageForClient(ClientIdDto.CHECKOUT, Instant.now())
        }
    }
}
