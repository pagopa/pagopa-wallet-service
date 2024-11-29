package it.pagopa.wallet.services

import it.pagopa.wallet.audit.*
import it.pagopa.wallet.client.WalletQueueClient
import it.pagopa.wallet.common.tracing.QueueTracingInfo
import it.pagopa.wallet.config.properties.LoggedActionDeadLetterQueueConfig
import it.pagopa.wallet.repositories.LoggingEventRepository
import java.time.Duration
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Service
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono

@Service
class LoggingEventSyncWriter(
    @Autowired private val paymentWalletLoggedActionDeadLetterQueueClient: WalletQueueClient,
    @Autowired private val queueConfig: LoggedActionDeadLetterQueueConfig,
    @Autowired private val loggingEventRepository: LoggingEventRepository
) {

    private val logger = LoggerFactory.getLogger(javaClass)

    /**
     * Perform save operation for input logged action synchronously. If an error occurs writing
     * event to collection a dead letter event is then written for each of the logging event
     */
    fun <T : Any> saveEventSyncWithDLQWrite(loggedAction: LoggedAction<T>): Mono<T> {
        val walletId =
            extractWalletIdFromLoggingEvents(loggingEvents = loggedAction.events) ?: "N/A"
        return loggedAction
            .saveEvents(loggingEventRepository)
            .doOnNext { _ ->
                val events = loggedAction.events
                logger.debug(
                    "Saved logging events: [{}], for wallet with id: [{}]",
                    events.map { it.javaClass.simpleName },
                    walletId
                )
            }
            .thenReturn(Unit)
            .onErrorResume {
                logger.error("Error saving logging event to collection", it)
                Flux.fromIterable(loggedAction.events)
                    .flatMap { loggingEvent ->
                        paymentWalletLoggedActionDeadLetterQueueClient
                            .sendQueueEventWithTracingInfo(
                                event =
                                    WalletLoggingErrorEvent(
                                        loggingEvent = loggingEvent,
                                        eventId = loggingEvent.id
                                    ),
                                delay = Duration.ofSeconds(queueConfig.visibilityTimeoutSeconds),
                                tracingInfo =
                                    QueueTracingInfo(
                                        traceparent = null,
                                        tracestate = null,
                                        baggage = null
                                    )
                            )
                            .doOnNext {
                                logger.warn(
                                    "Written event into dead letter for error saving wallet logging event: [{}] for wallet with id: [{}]",
                                    loggingEvent.javaClass,
                                    walletId
                                )
                            }
                    }
                    .collectList()
                    .thenReturn(Unit)
            }
            .thenReturn(loggedAction.data)
    }

    fun extractWalletIdFromLoggingEvents(loggingEvents: List<LoggingEvent>): String? =
        loggingEvents.firstNotNullOfOrNull {
            when (it) {
                is ApplicationCreatedEvent -> null
                is ApplicationStatusChangedEvent -> null
                is WalletAddedEvent -> it.walletId
                is SessionWalletCreatedEvent -> it.walletId
                is WalletApplicationsUpdatedEvent -> it.walletId
                is WalletDeletedEvent -> it.walletId
                is WalletDetailsAddedEvent -> it.walletId
                is WalletMigratedAddedEvent -> it.walletId
                is WalletOnboardCompletedEvent -> it.walletId
            }
        }
}
