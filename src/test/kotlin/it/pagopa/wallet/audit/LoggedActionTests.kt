package it.pagopa.wallet.audit

import it.pagopa.wallet.repositories.LoggingEventRepository
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.mockito.kotlin.*
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono

class LoggedActionTests {
    val repository: LoggingEventRepository = mock()

    fun saveIdWithLogging(id: String): Mono<LoggedAction<String>> {
        return Mono.just(id).map { LoggedAction(it, WalletAddedEvent(it)) }
    }

    @Test
    fun `saveEvents saves events correctly`() {
        val walletId = "walletId"
        val expectedSavedEvents = listOf(WalletAddedEvent(walletId))

        given(repository.saveAll(expectedSavedEvents)).willReturn(Flux.empty())

        val actualId = saveIdWithLogging(walletId).flatMap { it.saveEvents(repository) }.block()

        assertEquals(walletId, actualId)

        verify(repository, times(1)).saveAll(any<Iterable<LoggingEvent>>())
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
}
