package it.pagopa.wallet.services

import io.mockk.every
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import it.pagopa.generated.wallet.model.WalletStatusDto
import it.pagopa.wallet.WalletTestUtils.CONTRACT_ID
import it.pagopa.wallet.WalletTestUtils.PAYMENT_METHOD_ID
import it.pagopa.wallet.WalletTestUtils.SERVICE_NAME
import it.pagopa.wallet.WalletTestUtils.USER_ID
import it.pagopa.wallet.WalletTestUtils.WALLET_DOCUMENT_EMPTY_SERVICES_NULL_DETAILS_NO_PAYMENT_INSTRUMENT
import it.pagopa.wallet.WalletTestUtils.WALLET_DOMAIN_EMPTY_SERVICES_NULL_DETAILS_NO_PAYMENT_INSTRUMENT
import it.pagopa.wallet.WalletTestUtils.WALLET_UUID
import it.pagopa.wallet.audit.LoggedAction
import it.pagopa.wallet.audit.WalletAddedEvent
import it.pagopa.wallet.repositories.WalletRepository
import java.util.*
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.reactor.mono
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.given
import org.mockito.kotlin.mock
import reactor.test.StepVerifier

@OptIn(ExperimentalCoroutinesApi::class)
class WalletServiceTest {
    private val walletRepository: WalletRepository = mock()

    private val walletService: WalletService = WalletService(walletRepository)

    @Test
    fun `should save wallet document`() {
        /* preconditions */

        val mockedUUID = UUID.randomUUID()
        mockkStatic(UUID::class)
        every { UUID.randomUUID() }.answers { mockedUUID }

        val expectedLoggedAction =
            LoggedAction(
                WALLET_DOMAIN_EMPTY_SERVICES_NULL_DETAILS_NO_PAYMENT_INSTRUMENT,
                WalletAddedEvent(WALLET_UUID.value.toString())
            )

        given { walletRepository.save(any()) }
            .willReturn(mono { WALLET_DOCUMENT_EMPTY_SERVICES_NULL_DETAILS_NO_PAYMENT_INSTRUMENT })

        /* test */

        StepVerifier.create(
                walletService.createWallet(
                    listOf(SERVICE_NAME),
                    USER_ID.id,
                    PAYMENT_METHOD_ID.value,
                    CONTRACT_ID.contractId
                )
            )
            .expectNextMatches {
                expectedLoggedAction.data.id == WALLET_UUID &&
                    expectedLoggedAction.data.userId == USER_ID &&
                    expectedLoggedAction.data.services.isEmpty() &&
                    expectedLoggedAction.data.status == WalletStatusDto.CREATED &&
                    expectedLoggedAction.data.contractId == CONTRACT_ID &&
                    expectedLoggedAction.data.details == null &&
                    expectedLoggedAction.data.paymentInstrumentId == null &&
                    expectedLoggedAction.data.paymentMethodId == PAYMENT_METHOD_ID
            }
            .verifyComplete()

        unmockkStatic(UUID::class)
    }
}
