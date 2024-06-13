package it.pagopa.wallet.services

import com.azure.core.http.rest.Response
import com.azure.storage.queue.models.SendMessageResult
import it.pagopa.wallet.audit.LoggingEvent
import it.pagopa.wallet.audit.WalletAddedEvent
import it.pagopa.wallet.audit.WalletExpiredEvent
import it.pagopa.wallet.client.WalletQueueClient
import it.pagopa.wallet.common.tracing.TracingUtils
import it.pagopa.wallet.config.properties.ExpirationQueueConfig
import it.pagopa.wallet.domain.wallets.DomainEventDispatcher
import it.pagopa.wallet.domain.wallets.WalletId
import java.time.Duration
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

    private val walletExpireTimeout by lazy {
        Duration.ofSeconds(expirationQueueConfig.timeoutWalletCreated)
    }

    companion object {
        const val WALLET_CREATED_EVENT_HANDLER_SPAN_NAME = "walletCreatedEventHandler"
    }

    override fun dispatchEvent(event: LoggingEvent): Mono<LoggingEvent> =
        when (event) {
            is WalletAddedEvent -> onWalletCreated(event).map { event }
            else -> Mono.empty()
        }

    private fun onWalletCreated(
        walletCreated: WalletAddedEvent
    ): Mono<Response<SendMessageResult>> =
        tracingUtils
            .traceMono(WALLET_CREATED_EVENT_HANDLER_SPAN_NAME) { tracingInfo ->
                logger.info(
                    "Handling wallet created event for [{}], publishing to storage queue with delay of [{}]",
                    walletCreated.walletId,
                    walletExpireTimeout
                )
                val walletExpiredEvent = WalletExpiredEvent.of(WalletId.of(walletCreated.walletId))
                walletQueueClient.sendExpirationEvent(
                    event = walletExpiredEvent,
                    delay = walletExpireTimeout,
                    tracingInfo = tracingInfo
                )
            }
            .doOnNext {
                logger.info(
                    "Successfully published expiration message for [{}] with delay of [{}]",
                    walletCreated.walletId,
                    walletExpireTimeout
                )
            }
            .doOnError { logger.error("Failed to publish event for [${walletCreated.walletId}]") }
}
