package it.pagopa.wallet.common.serialization

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import it.pagopa.wallet.audit.WalletCreatedEvent
import it.pagopa.wallet.audit.WalletEvent
import it.pagopa.wallet.common.QueueEvent
import it.pagopa.wallet.common.tracing.QueueTracingInfo
import it.pagopa.wallet.config.SerializationConfiguration
import it.pagopa.wallet.domain.wallets.WalletId
import java.util.*
import java.util.stream.Stream
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource

class WalletEventTest {

    private val serializer =
        SerializationConfiguration().objectMapperBuilder().build<ObjectMapper>()

    @ParameterizedTest
    @MethodSource("walletEvents")
    fun shouldSerializeWalletEvent(walletEvent: WalletEvent) {
        val json = serializer.writeValueAsString(walletEvent)
        val deserializedEvent = serializer.readValue<WalletEvent>(json)
        assertEquals(walletEvent, deserializedEvent)
    }

    @ParameterizedTest
    @MethodSource("walletEvents")
    fun shouldSerializeQueueWalletEvent(walletEvent: WalletEvent) {
        val queueEvent = QueueEvent(walletEvent, QueueTracingInfo.empty())
        val json = serializer.writeValueAsString(queueEvent)
        val deserializedEvent = serializer.readValue<QueueEvent<WalletEvent>>(json)
        assertEquals(queueEvent, deserializedEvent)
    }

    companion object {
        @JvmStatic
        private fun walletEvents() =
            Stream.of(
                Arguments.of(WalletCreatedEvent.of(WalletId(UUID.randomUUID()))),
            )
    }
}
