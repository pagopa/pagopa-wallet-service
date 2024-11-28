package it.pagopa.wallet.common.serialization

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import it.pagopa.wallet.audit.WalletCreatedEvent
import it.pagopa.wallet.audit.WalletQueueEvent
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

class WalletQueueEventTest {

    private val serializer =
        SerializationConfiguration().objectMapperBuilder().build<ObjectMapper>()

    @ParameterizedTest
    @MethodSource("walletEvents")
    fun shouldSerializeWalletEvent(walletQueueEvent: WalletQueueEvent) {
        val json = serializer.writeValueAsString(walletQueueEvent)
        val deserializedEvent = serializer.readValue<WalletQueueEvent>(json)
        assertEquals(walletQueueEvent, deserializedEvent)
    }

    @ParameterizedTest
    @MethodSource("walletEvents")
    fun shouldSerializeQueueWalletEvent(walletQueueEvent: WalletQueueEvent) {
        val queueEvent = QueueEvent(walletQueueEvent, QueueTracingInfo.empty())
        val json = serializer.writeValueAsString(queueEvent)
        val deserializedEvent = serializer.readValue<QueueEvent<WalletQueueEvent>>(json)
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
