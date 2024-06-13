package it.pagopa.wallet.client

import com.azure.core.util.BinaryData
import com.azure.storage.queue.QueueAsyncClient
import it.pagopa.wallet.audit.WalletCreatedEvent
import it.pagopa.wallet.common.QueueEvent
import it.pagopa.wallet.common.tracing.QueueTracingInfo
import it.pagopa.wallet.config.AzureStorageConfiguration
import it.pagopa.wallet.config.SerializationConfiguration
import it.pagopa.wallet.util.AzureQueueTestUtils
import java.time.Duration
import java.time.Instant
import java.util.*
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import org.mockito.kotlin.*
import reactor.kotlin.test.test

class WalletQueueClientTest {

    private val jsonSerializer =
        AzureStorageConfiguration()
            .jsonSerializerProvider(SerializationConfiguration().objectMapperBuilder().build())
            .createInstance()

    private val azureQueueClient = mock<QueueAsyncClient>()

    private val walletQueueClient =
        WalletQueueClient(
            expirationQueueClient = azureQueueClient,
            jsonSerializer = jsonSerializer,
            ttl = Duration.ZERO
        )

    @Test
    fun shouldEmitWalletCreatedEvent() {
        val expiredEvent =
            WalletCreatedEvent(
                UUID.randomUUID().toString(),
                Instant.now(),
                UUID.randomUUID().toString()
            )
        val tracingInfo = QueueTracingInfo("traceparent", "state", "baggage")
        val expectedQueueEvent = QueueEvent(expiredEvent, tracingInfo)

        given { azureQueueClient.sendMessageWithResponse(any<BinaryData>(), any(), any()) }
            .willReturn(AzureQueueTestUtils.QUEUE_SUCCESSFUL_RESPONSE)

        walletQueueClient
            .sendWalletCreatedEvent(expiredEvent, delay = Duration.ofSeconds(10), tracingInfo)
            .test()
            .assertNext { Assertions.assertEquals(200, it.statusCode) }
            .verifyComplete()

        argumentCaptor<BinaryData> {
            verify(azureQueueClient, times(1))
                .sendMessageWithResponse(capture(), eq(Duration.ofSeconds(10)), eq(Duration.ZERO))
            val eventSent = lastValue.toObject(QueueEvent::class.java, jsonSerializer)
            Assertions.assertEquals(expectedQueueEvent, eventSent)
        }
    }
}
