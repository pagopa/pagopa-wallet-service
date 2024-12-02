package it.pagopa.wallet.services

import it.pagopa.wallet.WalletTestUtils
import it.pagopa.wallet.audit.LoggedAction
import it.pagopa.wallet.audit.WalletDeletedEvent
import it.pagopa.wallet.audit.WalletLoggingErrorEvent
import it.pagopa.wallet.client.WalletQueueClient
import it.pagopa.wallet.common.tracing.TracingUtils
import it.pagopa.wallet.common.tracing.TracingUtilsTest
import it.pagopa.wallet.config.properties.LoggedActionDeadLetterQueueConfig
import it.pagopa.wallet.repositories.LoggingEventRepository
import it.pagopa.wallet.util.AzureQueueTestUtils
import java.time.Duration
import org.junit.jupiter.api.Test
import org.mockito.kotlin.*
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import reactor.test.StepVerifier

class LoggingEventSyncWriterTest {

    private val paymentWalletLoggedActionDeadLetterQueueClient: WalletQueueClient = mock()
    private val queueConfig =
        LoggedActionDeadLetterQueueConfig(
            storageQueueName = "dql-name",
            ttlSeconds = 100,
            storageConnectionString = "storage-connection-string",
            visibilityTimeoutSeconds = 100
        )
    private val loggingEventRepository: LoggingEventRepository = mock()
    private val tracingUtils: TracingUtils = TracingUtilsTest.getMock()

    private val loggingEventSyncWriter =
        LoggingEventSyncWriter(
            paymentWalletLoggedActionDeadLetterQueueClient =
                paymentWalletLoggedActionDeadLetterQueueClient,
            queueConfig = queueConfig,
            loggingEventRepository = loggingEventRepository,
            tracingUtils = tracingUtils
        )

    @Test
    fun `should save logging events to repository`() {
        // pre-conditions
        given { loggingEventRepository.saveAll(any()) }
            .willAnswer { Flux.fromIterable(it.arguments.toList()) }
        val loggedAction =
            LoggedAction(
                data = Unit,
                event = WalletDeletedEvent(walletId = WalletTestUtils.WALLET_UUID.value.toString())
            )
        // test
        StepVerifier.create(
                loggingEventSyncWriter.saveEventSyncWithDLQWrite(loggedAction = loggedAction)
            )
            .expectNext(Unit)
            .verifyComplete()
        // verifications
        verify(loggingEventRepository, times(1)).saveAll(loggedAction.events)
        verify(paymentWalletLoggedActionDeadLetterQueueClient, times(0))
            .sendQueueEventWithTracingInfo(any(), any(), any())
    }

    @Test
    fun `should resume from error writing logging event to collection writing event to DLQ`() {
        // pre-conditions
        given { loggingEventRepository.saveAll(any()) }
            .willReturn(Flux.error(RuntimeException("error saving event to DLQ")))
        given {
                paymentWalletLoggedActionDeadLetterQueueClient.sendQueueEventWithTracingInfo(
                    any(),
                    any(),
                    any()
                )
            }
            .willReturn(AzureQueueTestUtils.QUEUE_SUCCESSFUL_RESPONSE)
        val loggedAction =
            LoggedAction(
                data = Unit,
                event = WalletDeletedEvent(walletId = WalletTestUtils.WALLET_UUID.value.toString())
            )
        val expectedDLQEvent =
            WalletLoggingErrorEvent(
                eventId = loggedAction.events[0].id,
                loggingEvent = loggedAction.events[0]
            )
        // test
        StepVerifier.create(
                loggingEventSyncWriter.saveEventSyncWithDLQWrite(loggedAction = loggedAction)
            )
            .expectNext(Unit)
            .verifyComplete()
        // verifications
        verify(loggingEventRepository, times(1)).saveAll(loggedAction.events)
        verify(paymentWalletLoggedActionDeadLetterQueueClient, times(1))
            .sendQueueEventWithTracingInfo(
                event = eq(expectedDLQEvent),
                delay = eq(Duration.ofSeconds(queueConfig.visibilityTimeoutSeconds)),
                tracingInfo = any()
            )
    }

    @Test
    fun `should return error for exception while writing event to DLQ`() {
        // pre-conditions
        given { loggingEventRepository.saveAll(any()) }
            .willReturn(Flux.error(RuntimeException("error saving event to DLQ")))
        given {
                paymentWalletLoggedActionDeadLetterQueueClient.sendQueueEventWithTracingInfo(
                    any(),
                    any(),
                    any()
                )
            }
            .willReturn(Mono.error(RuntimeException("error writing event to DLQ")))
        val loggedAction =
            LoggedAction(
                data = Unit,
                event = WalletDeletedEvent(walletId = WalletTestUtils.WALLET_UUID.value.toString())
            )
        val expectedDLQEvent =
            WalletLoggingErrorEvent(
                eventId = loggedAction.events[0].id,
                loggingEvent = loggedAction.events[0]
            )
        // test
        StepVerifier.create(
                loggingEventSyncWriter.saveEventSyncWithDLQWrite(loggedAction = loggedAction)
            )
            .expectErrorMatches {
                it is RuntimeException && it.message == "error writing event to DLQ"
            }
            .verify()
        // verifications
        verify(loggingEventRepository, times(1)).saveAll(loggedAction.events)
        verify(paymentWalletLoggedActionDeadLetterQueueClient, times(1))
            .sendQueueEventWithTracingInfo(
                event = eq(expectedDLQEvent),
                delay = eq(Duration.ofSeconds(queueConfig.visibilityTimeoutSeconds)),
                tracingInfo = any()
            )
    }
}
