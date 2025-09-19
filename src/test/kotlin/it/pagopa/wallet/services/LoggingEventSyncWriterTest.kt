package it.pagopa.wallet.services

import it.pagopa.wallet.WalletTestUtils
import it.pagopa.wallet.audit.*
import it.pagopa.wallet.client.WalletQueueClient
import it.pagopa.wallet.common.tracing.TracingUtils
import it.pagopa.wallet.common.tracing.TracingUtilsTest
import it.pagopa.wallet.config.properties.LoggedActionDeadLetterQueueConfig
import it.pagopa.wallet.domain.applications.ApplicationStatus
import it.pagopa.wallet.repositories.LoggingEventRepository
import it.pagopa.wallet.util.AzureQueueTestUtils
import java.time.Duration
import java.time.OffsetDateTime
import java.util.*
import java.util.stream.Stream
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
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
            visibilityTimeoutSeconds = 100)
    private val loggingEventRepository: LoggingEventRepository = mock()
    private val tracingUtils: TracingUtils = TracingUtilsTest.getMock()

    private val loggingEventSyncWriter =
        LoggingEventSyncWriter(
            paymentWalletLoggedActionDeadLetterQueueClient =
                paymentWalletLoggedActionDeadLetterQueueClient,
            queueConfig = queueConfig,
            loggingEventRepository = loggingEventRepository,
            tracingUtils = tracingUtils)

    @Test
    fun `should save logging events to repository`() {
        // pre-conditions
        given { loggingEventRepository.saveAll(any()) }
            .willAnswer { Flux.fromIterable(it.arguments.toList()) }
        val loggedAction =
            LoggedAction(
                data = Unit,
                event = WalletDeletedEvent(walletId = WalletTestUtils.WALLET_UUID.value.toString()))
        // test
        StepVerifier.create(
                loggingEventSyncWriter.saveEventSyncWithDLQWrite(loggedAction = loggedAction))
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
                    any(), any(), any())
            }
            .willReturn(AzureQueueTestUtils.QUEUE_SUCCESSFUL_RESPONSE)
        val loggedAction =
            LoggedAction(
                data = Unit,
                event = WalletDeletedEvent(walletId = WalletTestUtils.WALLET_UUID.value.toString()))
        val expectedDLQEvent =
            WalletLoggingErrorEvent(
                eventId = loggedAction.events[0].id, loggingEvent = loggedAction.events[0])
        // test
        StepVerifier.create(
                loggingEventSyncWriter.saveEventSyncWithDLQWrite(loggedAction = loggedAction))
            .expectNext(Unit)
            .verifyComplete()
        // verifications
        verify(loggingEventRepository, times(1)).saveAll(loggedAction.events)
        verify(paymentWalletLoggedActionDeadLetterQueueClient, times(1))
            .sendQueueEventWithTracingInfo(
                event = eq(expectedDLQEvent),
                delay = eq(Duration.ofSeconds(queueConfig.visibilityTimeoutSeconds)),
                tracingInfo = any())
    }

    @Test
    fun `should return error for exception while writing event to DLQ`() {
        // pre-conditions
        given { loggingEventRepository.saveAll(any()) }
            .willReturn(Flux.error(RuntimeException("error saving event to DLQ")))
        given {
                paymentWalletLoggedActionDeadLetterQueueClient.sendQueueEventWithTracingInfo(
                    any(), any(), any())
            }
            .willReturn(Mono.error(RuntimeException("error writing event to DLQ")))
        val loggedAction =
            LoggedAction(
                data = Unit,
                event = WalletDeletedEvent(walletId = WalletTestUtils.WALLET_UUID.value.toString()))
        val expectedDLQEvent =
            WalletLoggingErrorEvent(
                eventId = loggedAction.events[0].id, loggingEvent = loggedAction.events[0])
        // test
        StepVerifier.create(
                loggingEventSyncWriter.saveEventSyncWithDLQWrite(loggedAction = loggedAction))
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
                tracingInfo = any())
    }

    @ParameterizedTest
    @MethodSource("extract wallet id method source")
    fun `should extract wallet id from event`(
        loggingEvents: List<LoggingEvent>,
        expectedWalletId: String?
    ) {
        val walletId = loggingEventSyncWriter.extractWalletIdFromLoggingEvents(loggingEvents)
        assertEquals(expectedWalletId, walletId)
    }

    companion object {
        val walletId = UUID.randomUUID().toString()

        @JvmStatic
        fun `extract wallet id method source`(): Stream<Arguments> =
            Stream.of(
                Arguments.of(listOf(ApplicationCreatedEvent(serviceId = "serviceId")), "N/A"),
                Arguments.of(
                    listOf(
                        ApplicationStatusChangedEvent(
                            serviceId = "serviceId",
                            oldStatus = ApplicationStatus.ENABLED,
                            newStatus = ApplicationStatus.DISABLED)),
                    "N/A"),
                Arguments.of(listOf(WalletAddedEvent(walletId = walletId)), walletId),
                Arguments.of(
                    listOf(
                        SessionWalletCreatedEvent(
                            walletId = walletId,
                            auditWallet = AuditWalletCreated(orderId = "orderId"))),
                    walletId),
                Arguments.of(
                    listOf(
                        WalletApplicationsUpdatedEvent(
                            walletId = walletId, updatedApplications = listOf())),
                    walletId),
                Arguments.of(listOf(WalletDeletedEvent(walletId = walletId)), walletId),
                Arguments.of(listOf(WalletDetailsAddedEvent(walletId = walletId)), walletId),
                Arguments.of(listOf(WalletMigratedAddedEvent(walletId = walletId)), walletId),
                Arguments.of(
                    listOf(
                        WalletOnboardCompletedEvent(
                            walletId = walletId,
                            auditWallet =
                                AuditWalletCompleted(
                                    paymentMethodId = "paymentMethodId",
                                    creationDate = OffsetDateTime.now().toString(),
                                    updateDate = OffsetDateTime.now().toString(),
                                    applications = listOf(),
                                    details = null,
                                    status = "VALIDATED",
                                    validationOperationId = "validationOperationId",
                                    validationOperationResult = "EXECUTED",
                                    validationOperationTimestamp = OffsetDateTime.now().toString(),
                                    validationErrorCode = null))),
                    walletId),
                Arguments.of(
                    listOf(
                        WalletDeletedEvent(walletId = walletId),
                        ApplicationCreatedEvent(serviceId = "serviceId")),
                    walletId),
                Arguments.of(
                    listOf(
                        ApplicationCreatedEvent(serviceId = "serviceId"),
                        WalletDeletedEvent(walletId = walletId)),
                    walletId),
            )
    }
}
