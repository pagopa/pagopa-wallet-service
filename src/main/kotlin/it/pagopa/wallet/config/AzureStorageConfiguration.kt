package it.pagopa.wallet.config

import com.azure.core.http.netty.NettyAsyncHttpClientBuilder
import com.azure.core.util.serializer.JsonSerializerProvider
import com.azure.storage.queue.QueueClientBuilder
import it.pagopa.wallet.audit.WalletEvent
import it.pagopa.wallet.client.WalletQueueClient
import it.pagopa.wallet.common.serialization.StrictJsonSerializerProvider
import it.pagopa.wallet.common.serialization.WalletEventMixin
import it.pagopa.wallet.config.properties.ExpirationQueueConfig
import java.time.Duration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import reactor.netty.http.client.HttpClient

@Configuration
class AzureStorageConfiguration {

    @Bean
    fun jsonSerializerProvider(): JsonSerializerProvider =
        StrictJsonSerializerProvider()
            .addMixIn(WalletEvent::class.java, WalletEventMixin::class.java)

    @Bean
    fun expirationQueueClient(
        expirationQueueConfig: ExpirationQueueConfig,
        jsonSerializerProvider: JsonSerializerProvider
    ): WalletQueueClient {
        val serializer = jsonSerializerProvider.createInstance()
        val queue =
            QueueClientBuilder()
                .connectionString(expirationQueueConfig.storageConnectionString)
                .queueName(expirationQueueConfig.storageQueueName)
                .httpClient(
                    NettyAsyncHttpClientBuilder(
                            HttpClient.create().resolver { nameResolverSpec ->
                                nameResolverSpec.ndots(1)
                            }
                        )
                        .build()
                )
                .buildAsyncClient()
        return WalletQueueClient(
            queue,
            serializer,
            Duration.ofSeconds(expirationQueueConfig.ttlSeconds)
        )
    }
}
