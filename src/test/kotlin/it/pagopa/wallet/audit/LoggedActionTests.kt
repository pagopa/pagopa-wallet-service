package it.pagopa.wallet.audit

import it.pagopa.generated.wallet.model.WalletNotificationRequestDto.OperationResultEnum
import it.pagopa.wallet.WalletTestUtils
import it.pagopa.wallet.domain.wallets.LoggingEventDispatcher
import it.pagopa.wallet.repositories.LoggingEventRepository
import it.pagopa.wallet.repositories.LoggingEventRepositoryImpl
import it.pagopa.wallet.repositories.LoggingEventRepositoryMongo
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.*
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import reactor.kotlin.test.test

class LoggedActionTests {

    private val mongoRepository: LoggingEventRepositoryMongo = mock()
    private val loggingEventDispatcher: LoggingEventDispatcher = mock()
    val repository: LoggingEventRepository =
        LoggingEventRepositoryImpl(loggingEventDispatcher, mongoRepository)

    fun saveIdWithLogging(id: String): Mono<LoggedAction<String>> {
        return Mono.just(id).map { LoggedAction(it, WalletAddedEvent(it)) }
    }

    fun saveWalletNotificationEventWithLogging(id: String): Mono<LoggedAction<String>> {
        return Mono.just(id).map {
            LoggedAction(
                it,
                WalletNotificationEvent(
                    walletId = it,
                    validationOperationId = "validationOperationId",
                    validationOperationResult = OperationResultEnum.EXECUTED.value,
                    validationErrorCode = null,
                    validationOperationTimestamp = WalletTestUtils.TIMESTAMP.toString()
                )
            )
        }
    }

    @BeforeEach
    fun setup() {
        given { loggingEventDispatcher.dispatchEvent(any()) }
            .willAnswer { Mono.just(it.arguments[0]) }
    }

    @Test
    fun `saveEvents saves events correctly`() {
        val walletId = "walletId"
        val expectedSavedEvents = listOf(WalletAddedEvent(walletId))

        given(mongoRepository.saveAll(expectedSavedEvents)).willReturn(Flux.empty())

        val actualId = saveIdWithLogging(walletId).flatMap { it.saveEvents(repository) }.block()

        assertEquals(walletId, actualId)

        verify(mongoRepository, times(1)).saveAll(any<Iterable<LoggingEvent>>())
    }

    @Test
    fun `map transforms inner value and keeps events the same`() {
        val loggingEvent1 = WalletAddedEvent("walletId1")

        val f = { LoggedAction("id1", loggingEvent1) }

        val result = f().map(String::uppercase)
        val expected = LoggedAction("ID1", loggingEvent1)

        assertEquals(expected, result)
    }

    @Test
    fun `flatMap concatenates logging events`() {
        val loggingEvent1 = WalletAddedEvent("walletId1")
        val loggingEvent2 = WalletAddedEvent("walletId2")

        val f = { LoggedAction("id1", loggingEvent1) }
        val g = { LoggedAction("id2", loggingEvent2) }

        val result = f().flatMap { g() }
        val expected = LoggedAction("id2", listOf(loggingEvent1, loggingEvent2))

        assertEquals(expected, result)
    }

    @Test
    fun `flatMapLogged concatenates events correctly`() {
        val walletId1 = "walletId1"

        val actual = saveIdWithLogging(walletId1).flatMapLogged { saveIdWithLogging(it) }.block()

        val expected =
            LoggedAction(
                walletId1,
                listOf(WalletAddedEvent(walletId1), WalletAddedEvent(walletId1))
            )

        assertEquals(expected, actual)
    }

    @Test
    fun `mapLogged maps data correctly`() {
        val walletId1 = "walletId1"

        val f = { id: String -> Mono.just(id.uppercase()) }

        val actual = saveIdWithLogging(walletId1).mapLogged(f).block()

        val expected = LoggedAction(walletId1.uppercase(), listOf(WalletAddedEvent(walletId1)))

        assertEquals(expected, actual)
    }

    @Test
    fun `saveEvents saves WalletNotificationEvent events correctly`() {
        val walletId = "walletId"
        val expectedSavedEvents =
            listOf(
                WalletNotificationEvent(
                    walletId = walletId,
                    validationOperationId = "validationOperationId",
                    validationOperationResult = OperationResultEnum.EXECUTED.value,
                    validationErrorCode = null,
                    validationOperationTimestamp = WalletTestUtils.TIMESTAMP.toString()
                )
            )

        given(mongoRepository.saveAll(expectedSavedEvents)).willReturn(Flux.empty())

        val actualId =
            saveWalletNotificationEventWithLogging(walletId)
                .flatMap { it.saveEvents(repository) }
                .block()

        assertEquals(walletId, actualId)

        verify(mongoRepository, times(1)).saveAll(any<Iterable<LoggingEvent>>())
    }

    @Test
    fun `when save domain events than domain dispatcher is used for each event`() {
        val walletId = "walletId"
        val expectedSavedEvents =
            listOf(
                WalletAddedEvent(walletId),
                WalletNotificationEvent(
                    walletId = walletId,
                    validationOperationId = "validationOperationId",
                    validationOperationResult = OperationResultEnum.EXECUTED.value,
                    validationErrorCode = null,
                    validationOperationTimestamp = WalletTestUtils.TIMESTAMP.toString()
                )
            )
        given(mongoRepository.saveAll(any<Iterable<LoggingEvent>>())).willAnswer {
            Flux.fromIterable(expectedSavedEvents)
        }
        val actualId =
            saveWalletNotificationEventWithLogging(walletId)
                .flatMap { it.saveEvents(repository) }
                .block()
        assertEquals(walletId, actualId)

        argumentCaptor<LoggingEvent> {
            verify(loggingEventDispatcher, times(2)).dispatchEvent(capture())
            assertArrayEquals(expectedSavedEvents.toTypedArray(), allValues.toTypedArray())
        }
    }

    @Test
    fun `when save domain events and one event is not supported by domain event dispatcher should also returns the whole events`() {
        val walletId = "walletId"
        val expectedSavedEvents =
            listOf(
                WalletAddedEvent(walletId),
                WalletNotificationEvent(
                    walletId = walletId,
                    validationOperationId = "validationOperationId",
                    validationOperationResult = OperationResultEnum.EXECUTED.value,
                    validationErrorCode = null,
                    validationOperationTimestamp = WalletTestUtils.TIMESTAMP.toString()
                )
            )
        given { loggingEventDispatcher.dispatchEvent(any()) }
            .willAnswer { Mono.just(it.arguments.get(0)) }
            .willAnswer { Mono.empty<LoggingEvent>() }

        given(mongoRepository.saveAll(any<Iterable<LoggingEvent>>())).willAnswer {
            Flux.fromIterable(expectedSavedEvents)
        }

        saveWalletNotificationEventWithLogging(walletId)
            .flatMap { it.saveEvents(repository) }
            .test()
            .assertNext {
                argumentCaptor<LoggingEvent> {
                    verify(loggingEventDispatcher, times(2)).dispatchEvent(capture())
                    assertArrayEquals(expectedSavedEvents.toTypedArray(), allValues.toTypedArray())
                }
            }
            .verifyComplete()
    }
}
