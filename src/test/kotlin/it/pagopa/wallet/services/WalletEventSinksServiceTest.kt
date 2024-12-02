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
    private var loggingEventRepository: LoggingEventRepository = mock()
    private var retrySavePolicyConfig: RetrySavePolicyConfig = RetrySavePolicyConfig(1, 1)
    private val walletEventSink: Sinks.Many<LoggedAction<*>> = mock()
    private lateinit var walletEventSinksService: WalletEventSinksService

    @Test
    fun testEmitEventAndReadEvent() {
        walletEventSinksService =
            WalletEventSinksService(loggingEventRepository, retrySavePolicyConfig)
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
        walletEventSinksService =
            WalletEventSinksService(loggingEventRepository, retrySavePolicyConfig)
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
    fun testEmitEventResultKOShouldReturnLoggedAction() {
        walletEventSinksService =
            WalletEventSinksService(loggingEventRepository, retrySavePolicyConfig, walletEventSink)
        /* preconditions */
        given { walletEventSink.tryEmitNext(any()) }.willReturn { Sinks.EmitResult.FAIL_CANCELLED }

        val loggedAction =
            LoggedAction(WALLET_DOMAIN, WalletAddedEvent(WALLET_DOMAIN.id.value.toString()))

        /* test */

        StepVerifier.create(walletEventSinksService.tryEmitEvent(loggedAction))
            .expectNext(loggedAction)
            .verifyComplete()
    }

    @Test
    fun testEmitEventKOShouldReturnLoggedAction() {
        walletEventSinksService =
            WalletEventSinksService(loggingEventRepository, retrySavePolicyConfig, walletEventSink)
        /* preconditions */
        given { walletEventSink.tryEmitNext(any()) }.willThrow { RuntimeException("Test error") }

        val loggedAction =
            LoggedAction(WALLET_DOMAIN, WalletAddedEvent(WALLET_DOMAIN.id.value.toString()))

        /* test */

        StepVerifier.create(walletEventSinksService.tryEmitEvent(loggedAction))
            .expectNext(loggedAction)
            .verifyComplete()
    }
}
