package it.pagopa.wallet.services

import com.azure.core.http.rest.Response
import com.azure.storage.queue.models.SendMessageResult
import it.pagopa.wallet.audit.LoggingEvent
import it.pagopa.wallet.audit.WalletAddedEvent
import it.pagopa.wallet.audit.WalletExpiredEvent
import it.pagopa.wallet.client.WalletQueueClient
import it.pagopa.wallet.config.properties.ExpirationQueueConfig
import it.pagopa.wallet.domain.wallets.DomainEventDispatcher
import it.pagopa.wallet.domain.wallets.WalletId
import it.pagopa.wallet.util.TracingUtils
import kotlin.time.Duration.Companion.seconds
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import reactor.core.publisher.Mono

@Component
class DomainEventDispatcherService(
    private val walletQueueClient: WalletQueueClient,
    private val tracingUtils: TracingUtils,
    private val expirationQueueConfig: ExpirationQueueConfig,
) : DomainEventDispatcher {

    private val logger: Logger = LoggerFactory.getLogger(javaClass)

    companion object {
        const val WALLET_CREATED_EVENT_HANDLER_SPAN_NAME = "walletCreatedEventHandler"
    }

    override fun dispatchEvent(event: LoggingEvent): Mono<LoggingEvent> =
        when (event) {
            is WalletAddedEvent -> onWalletCreated(event).map { event }
            else -> {
                logger.info("No dispatcher for ${event.javaClass.name}")
                Mono.empty()
            }
        }

    private fun onWalletCreated(
        walletCreated: WalletAddedEvent
    ): Mono<Response<SendMessageResult>> =
        tracingUtils
            .traceMono(WALLET_CREATED_EVENT_HANDLER_SPAN_NAME) { tracingInfo ->
                logger.info(
                    "Handling wallet created event for [{}], publishing to storage queue",
                    walletCreated.walletId
                )
                val walletExpiredEvent = WalletExpiredEvent.of(WalletId.of(walletCreated.walletId))
                walletQueueClient.sendExpirationEvent(
                    event = walletExpiredEvent,
                    delay = expirationQueueConfig.timeoutWalletCreated.seconds,
                    tracingInfo = tracingInfo
                )
            }
            .doOnNext {
                logger.info(
                    "Successfully published expiration message for [{}]",
                    walletCreated.walletId
                )
            }
            .doOnError { logger.error("Failed to publish event for [${walletCreated.walletId}]") }
}
