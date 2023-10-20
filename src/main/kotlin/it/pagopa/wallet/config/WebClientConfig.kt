package it.pagopa.wallet.config

import io.netty.channel.ChannelOption
import io.netty.handler.timeout.ReadTimeoutHandler
import it.pagopa.generated.ecommerce.api.PaymentMethodsApi
import it.pagopa.generated.npg.api.PaymentServicesApi
import java.util.concurrent.TimeUnit
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.client.reactive.ReactorClientHttpConnector
import reactor.netty.Connection
import reactor.netty.http.client.HttpClient

@Configuration
class WebClientConfig {

    @Bean(name = ["npgWebClient"])
    fun npgClient(
        @Value("\${npgService.uri}") baseUrl: String,
        @Value("\${npgService.readTimeout}") readTimeout: Int,
        @Value("\${npgService.connectionTimeout}") connectionTimeout: Int,
        @Value("\${npgService.apiKey}") npgApiKey: String
    ): PaymentServicesApi {
        val httpClient =
            HttpClient.create()
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, connectionTimeout)
                .doOnConnected { connection: Connection ->
                    connection.addHandlerLast(
                        ReadTimeoutHandler(readTimeout.toLong(), TimeUnit.MILLISECONDS)
                    )
                }
        val webClient =
            it.pagopa.generated.npg.ApiClient.buildWebClientBuilder()
                .clientConnector(ReactorClientHttpConnector(httpClient))
                .baseUrl(baseUrl)
                .build()
        val apiClient = it.pagopa.generated.npg.ApiClient(webClient).setBasePath(baseUrl)
        apiClient.setApiKey(npgApiKey)
        return PaymentServicesApi(apiClient)
    }

    @Bean(name = ["ecommercePaymentMethodsWebClient"])
    fun ecommercePaymentMethodsClient(
        @Value("\${ecommercePaymentMethods.uri}") baseUrl: String,
        @Value("\${ecommercePaymentMethods.readTimeout}") readTimeout: Int,
        @Value("\${ecommercePaymentMethods.connectionTimeout}") connectionTimeout: Int,
        @Value("\${ecommercePaymentMethods.apiKey}") npgApiKey: String
    ): PaymentMethodsApi {
        val httpClient =
            HttpClient.create()
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, connectionTimeout)
                .doOnConnected { connection: Connection ->
                    connection.addHandlerLast(
                        ReadTimeoutHandler(readTimeout.toLong(), TimeUnit.MILLISECONDS)
                    )
                }
        val webClient =
            it.pagopa.generated.npg.ApiClient.buildWebClientBuilder()
                .clientConnector(ReactorClientHttpConnector(httpClient))
                .baseUrl(baseUrl)
                .build()
        val apiClient = it.pagopa.generated.ecommerce.ApiClient(webClient).setBasePath(baseUrl)
        apiClient.setApiKey(npgApiKey)
        return PaymentMethodsApi(apiClient)
    }
}
