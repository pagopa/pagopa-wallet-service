package it.pagopa.wallet.services

import it.pagopa.wallet.audit.WalletExpiredEvent
import it.pagopa.wallet.client.WalletQueueClient
import it.pagopa.wallet.config.properties.ExpirationQueueConfig
import it.pagopa.wallet.util.AzureQueueTestUtils
import kotlin.time.Duration.Companion.seconds
import org.junit.jupiter.api.BeforeEach
import org.mockito.kotlin.*

class DomainEventDispatcherServiceTest {

    private val config = ExpirationQueueConfig("", "", 100, 100)

    private var argumentCaptor = argumentCaptor<WalletExpiredEvent>()
    private val walletQueueClient: WalletQueueClient = mock()
    private val domainEventDispatcherService =
        DomainEventDispatcherService(walletQueueClient, mock(), config)

    @BeforeEach
    fun setup() {
        given {
                walletQueueClient.sendExpirationEvent(
                    argumentCaptor.capture(),
                    eq(config.timeoutWalletCreated.seconds),
                    any()
                )
            }
            .willReturn(AzureQueueTestUtils.QUEUE_SUCCESSFUL_RESPONSE)
    }
    /*
    @Test
    fun `should dispatch supported events`() {
        val walletCreatedLoggingEvent = WalletAddedEvent(walletId = UUID.randomUUID().toString())

        domainEventDispatcherService
            .dispatchEvent(walletCreatedLoggingEvent)
            .test()
            .assertNext { Assertions.assertEquals(walletCreatedLoggingEvent.walletId, it.id) }
            .verifyComplete()
    }*/
}
