package it.pagopa.wallet.services

import it.pagopa.wallet.WalletTestUtils.WALLET_DOMAIN
import it.pagopa.wallet.audit.LoggedAction
import it.pagopa.wallet.audit.LoggingEvent
import it.pagopa.wallet.audit.WalletAddedEvent
import it.pagopa.wallet.config.properties.RetrySavePolicyConfig
import it.pagopa.wallet.repositories.LoggingEventRepository
import java.time.Duration
import org.junit.jupiter.api.Test
import org.mockito.kotlin.*
import reactor.core.publisher.Flux
import reactor.core.publisher.Sinks
import reactor.test.StepVerifier

class WalletEventSinksServiceTest {
    private val loggingEventRepository: LoggingEventRepository = mock()
    private val retrySavePolicyConfig: RetrySavePolicyConfig = RetrySavePolicyConfig(1, 1, 150)
    private val walletEventSink: Sinks.Many<LoggedAction<*>> =
        Sinks.many().unicast().onBackpressureBuffer()
    private val walletEventSinkSpy: Sinks.Many<LoggedAction<*>> = spy(walletEventSink)
    private val walletEventSinksService: WalletEventSinksService =
        WalletEventSinksService(loggingEventRepository, retrySavePolicyConfig, walletEventSinkSpy)

    @Test
    fun testEmitEventAndReadEvent() {
        /* preconditions */
        given { loggingEventRepository.saveAll(any<Iterable<LoggingEvent>>()) }
            .willReturn { Flux.empty() }

        val loggedAction =
            LoggedAction(WALLET_DOMAIN, WalletAddedEvent(WALLET_DOMAIN.id.value.toString()))

        /* test */
        walletEventSinksService.tryEmitEvent(loggedAction).subscribe()

        StepVerifier.create(walletEventSinksService.consumeSinksEvent())
            .expectNext(loggedAction.data)
            .verifyTimeout(Duration.ofMillis(200))

        verify(loggingEventRepository, times(1)).saveAll(any())
    }

    @Test
    fun testEmitEventAndReadEventWithRetry() {
        /* preconditions */
        given { loggingEventRepository.saveAll(any<Iterable<LoggingEvent>>()) }
            .willThrow(RuntimeException())
            .willReturn { Flux.empty() }

        val loggedAction =
            LoggedAction(WALLET_DOMAIN, WalletAddedEvent(WALLET_DOMAIN.id.value.toString()))

        /* test */
        walletEventSinksService.tryEmitEvent(loggedAction).subscribe()

        StepVerifier.create(walletEventSinksService.consumeSinksEvent())
            .expectNext(loggedAction.data)
            .verifyTimeout(Duration.ofSeconds(retrySavePolicyConfig.intervalInSeconds.plus(1)))

        verify(loggingEventRepository, times(2)).saveAll(any())
    }

    @Test
    fun testEmitEventKOShouldReturnLoggedAction() {
        /* preconditions */
        doThrow(RuntimeException("Test error")).`when`(walletEventSinkSpy).emitNext(any(), any())

        val loggedAction =
            LoggedAction(WALLET_DOMAIN, WalletAddedEvent(WALLET_DOMAIN.id.value.toString()))

        /* test */
        StepVerifier.create(walletEventSinksService.tryEmitEvent(loggedAction))
            .expectNext(loggedAction)
            .verifyComplete()
    }
}
