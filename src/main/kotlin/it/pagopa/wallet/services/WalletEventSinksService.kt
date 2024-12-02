package it.pagopa.wallet.services

import it.pagopa.wallet.audit.LoggedAction
import it.pagopa.wallet.config.properties.RetrySavePolicyConfig
import it.pagopa.wallet.repositories.LoggingEventRepository
import java.time.Duration
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.context.event.ApplicationReadyEvent
import org.springframework.context.ApplicationListener
import org.springframework.stereotype.Service
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import reactor.core.publisher.Sinks
import reactor.core.scheduler.Schedulers
import reactor.util.retry.Retry

@Service
class WalletEventSinksService(
    @Autowired private val loggingEventRepository: LoggingEventRepository,
    @Autowired private val retrySavePolicyConfig: RetrySavePolicyConfig,
    private val walletEventSink: Sinks.Many<LoggedAction<*>> =
        Sinks.many().unicast().onBackpressureBuffer()
) : ApplicationListener<ApplicationReadyEvent> {
    private val logger = LoggerFactory.getLogger(javaClass)

    fun <T : Any> tryEmitEvent(loggedAction: LoggedAction<T>): Mono<LoggedAction<T>> =
        Mono.fromCallable { walletEventSink.tryEmitNext(loggedAction) }
            .flatMap {
                if (it.isFailure)
                    Mono.error(
                        RuntimeException(
                            "Sink failed to emit new wallet event: error code ${it.name}"
                        )
                    )
                else Mono.just(loggedAction)
            }
            .doOnNext { logger.debug("Logging event emitted") }
            .doOnError { logger.error("Exception while emitting new wallet event: ", it) }
            .onErrorReturn(loggedAction)

    override fun onApplicationEvent(event: ApplicationReadyEvent) {
        consumeSinksEvent().subscribeOn(Schedulers.parallel()).subscribe()
    }

    fun consumeSinksEvent(): Flux<Any> =
        walletEventSink
            .asFlux()
            .flatMap { loggedAction ->
                Mono.defer { loggedAction.saveEvents(loggingEventRepository) }
                    .retryWhen(
                        Retry.backoff(
                                retrySavePolicyConfig.maxAttempts,
                                Duration.ofSeconds(retrySavePolicyConfig.intervalInSeconds)
                            )
                            .filter { t -> t is Exception }
                            .doBeforeRetry { signal ->
                                logger.warn(
                                    "Retrying writing event on CDC queue due to: ${signal.failure().message}"
                                )
                            }
                    )
                    .doOnError { logger.error("Exception while processing wallet event: ", it) }
                    .onErrorReturn(loggedAction)
            }
            .doOnNext { logger.debug("Logging event saved") }
}
