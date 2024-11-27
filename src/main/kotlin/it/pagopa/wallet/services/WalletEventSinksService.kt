package it.pagopa.wallet.services

import it.pagopa.wallet.audit.LoggedAction
import it.pagopa.wallet.domain.wallets.Wallet
import it.pagopa.wallet.repositories.LoggingEventRepository
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.context.event.ApplicationReadyEvent
import org.springframework.context.ApplicationListener
import org.springframework.stereotype.Service
import reactor.core.publisher.Mono
import reactor.core.publisher.Sinks

@Service
class WalletEventSinksService(
    @Autowired private val loggingEventRepository: LoggingEventRepository
) : ApplicationListener<ApplicationReadyEvent> {
    val walletEventSink: Sinks.Many<LoggedAction<*>> =
        Sinks.many().unicast().onBackpressureBuffer()
    private val logger = LoggerFactory.getLogger(javaClass)

    fun <T : Any> tryEmitEvent(loggedAction: LoggedAction<T>) : Mono<LoggedAction<T>> =
         Mono.just { walletEventSink.tryEmitNext(loggedAction) }
            .doOnError { logger.error("Exception while processing wallet event: ", it) }
            .map{loggedAction}
            .onErrorResume { Mono.just(loggedAction) }


    override fun onApplicationEvent(event: ApplicationReadyEvent) {
        walletEventSink
            .asFlux()
            .flatMap { it.saveEvents(loggingEventRepository) }
            .doOnError { logger.error("Exception while processing wallet event: ", it) }
            .onErrorResume { Mono.empty() }
            .subscribe()
    }
}
