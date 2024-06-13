package it.pagopa.wallet.services

import com.azure.core.http.rest.Response
import com.azure.storage.queue.models.SendMessageResult
import it.pagopa.wallet.audit.WalletAddedEvent
import it.pagopa.wallet.audit.WalletExpiredEvent
import it.pagopa.wallet.client.WalletQueueClient
import it.pagopa.wallet.common.tracing.TracedMono
import it.pagopa.wallet.common.tracing.TracingUtilsTest
import it.pagopa.wallet.config.properties.ExpirationQueueConfig
import it.pagopa.wallet.util.AzureQueueTestUtils
import java.time.Duration
import java.util.*
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.*
import reactor.core.publisher.Mono
import reactor.kotlin.test.test

class DomainEventDispatcherServiceTest {

    private val config = ExpirationQueueConfig("", "", 100, 100)

    private val walletQueueClient: WalletQueueClient = mock()
    private val tracingUtils = TracingUtilsTest.getMock()
    private val domainEventDispatcherService =
        DomainEventDispatcherService(walletQueueClient, tracingUtils, config)

    @BeforeEach
    fun setup() {
        given { walletQueueClient.sendExpirationEvent(any(), any(), any()) }
            .willAnswer { AzureQueueTestUtils.QUEUE_SUCCESSFUL_RESPONSE }
    }

    @Test
    fun `should dispatch WalletExpiredEvent from WalletAdded domain event`() {
        val walletCreatedLoggingEvent = WalletAddedEvent(walletId = UUID.randomUUID().toString())

        domainEventDispatcherService
            .dispatchEvent(walletCreatedLoggingEvent)
            .test()
            .assertNext { Assertions.assertEquals(walletCreatedLoggingEvent, it) }
            .verifyComplete()

        argumentCaptor<WalletExpiredEvent> {
            verify(walletQueueClient, times(1))
                .sendExpirationEvent(
                    capture(),
                    eq(Duration.ofSeconds(config.timeoutWalletCreated)),
                    any()
                )
            Assertions.assertEquals(walletCreatedLoggingEvent.walletId, lastValue.walletId)
            verify(tracingUtils, times(1)).traceMono(any(), any<TracedMono<Any>>())
        }
    }

    @Test
    fun `should return error if queue dispatching fails`() {
        val walletCreatedLoggingEvent = WalletAddedEvent(walletId = UUID.randomUUID().toString())
        given { walletQueueClient.sendExpirationEvent(any(), any(), any()) }
            .willAnswer {
                Mono.error<Response<SendMessageResult>>(RuntimeException("Fail to publish message"))
            }

        domainEventDispatcherService.dispatchEvent(walletCreatedLoggingEvent).test().expectError()
        verify(tracingUtils, times(1)).traceMono(any(), any<TracedMono<Any>>())
    }
}
