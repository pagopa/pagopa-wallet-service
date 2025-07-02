package it.pagopa.wallet.config

import io.netty.channel.ChannelOption
import io.netty.channel.epoll.EpollChannelOption
import io.netty.handler.timeout.ReadTimeoutHandler
import it.pagopa.generated.ecommerce.api.PaymentMethodsApi
import it.pagopa.generated.npg.api.PaymentServicesApi
import it.pagopa.wallet.config.properties.JwtTokenIssuerConfigProperties
import it.pagopa.wallet.config.properties.PaymentMethodsConfigProperties
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
        @Value("\${npgService.tcp.keepAlive.enabled}") tcpKeepAliveEnabled: Boolean,
        @Value("\${npgService.tcp.keepAlive.idle}") tcpKeepAliveIdle: Int,
        @Value("\${npgService.tcp.keepAlive.intvl}") tcpKeepAliveIntvl: Int,
        @Value("\${npgService.tcp.keepAlive.cnt}") tcpKeepAliveCnt: Int
    ): PaymentServicesApi {
        val httpClient =
            HttpClient.create()
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, connectionTimeout)
                .option(ChannelOption.SO_KEEPALIVE, tcpKeepAliveEnabled)
                .option(EpollChannelOption.TCP_KEEPIDLE, tcpKeepAliveIdle)
                .option(EpollChannelOption.TCP_KEEPINTVL, tcpKeepAliveIntvl)
                .option(EpollChannelOption.TCP_KEEPCNT, tcpKeepAliveCnt)
                .doOnConnected { connection: Connection ->
                    connection.addHandlerLast(
                        ReadTimeoutHandler(readTimeout.toLong(), TimeUnit.MILLISECONDS)
                    )
                }
                .resolver { it.ndots(1) }
        val webClient =
            it.pagopa.generated.npg.ApiClient.buildWebClientBuilder()
                .clientConnector(ReactorClientHttpConnector(httpClient))
                .baseUrl(baseUrl)
                .build()
        val apiClient = it.pagopa.generated.npg.ApiClient(webClient).setBasePath(baseUrl)
        return PaymentServicesApi(apiClient)
    }

    @Bean(name = ["ecommercePaymentMethodsWebClient"])
    fun ecommercePaymentMethodsClient(
        config: PaymentMethodsConfigProperties,
    ): PaymentMethodsApi {
        val httpClient =
            HttpClient.create()
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, config.connectionTimeout)
                .doOnConnected { connection: Connection ->
                    connection.addHandlerLast(
                        ReadTimeoutHandler(config.readTimeout.toLong(), TimeUnit.MILLISECONDS)
                    )
                }
                .resolver { it.ndots(1) }
        val webClient =
            it.pagopa.generated.npg.ApiClient.buildWebClientBuilder()
                .clientConnector(ReactorClientHttpConnector(httpClient))
                .baseUrl(config.uri)
                .build()
        val apiClient = it.pagopa.generated.ecommerce.ApiClient(webClient).setBasePath(config.uri)
        apiClient.setApiKey(config.apiKey)
        return PaymentMethodsApi(apiClient)
    }

    @Bean(name = ["ecommercePaymentMethodsWebClientV2"])
    fun ecommercePaymentMethodsClientV2(
        config: PaymentMethodsConfigProperties
    ): it.pagopa.generated.ecommerce.paymentmethods.v2.api.PaymentMethodsApi {
        val httpClient =
            HttpClient.create()
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, config.connectionTimeout)
                .doOnConnected { connection: Connection ->
                    connection.addHandlerLast(
                        ReadTimeoutHandler(config.readTimeout.toLong(), TimeUnit.MILLISECONDS)
                    )
                }
                .resolver { it.ndots(1) }
        val webClient =
            it.pagopa.generated.npg.ApiClient.buildWebClientBuilder()
                .clientConnector(ReactorClientHttpConnector(httpClient))
                .baseUrl(config.uriV2)
                .build()
        val apiClient =
            it.pagopa.generated.ecommerce.paymentmethods.v2
                .ApiClient(webClient)
                .setBasePath(config.uriV2)
        apiClient.setApiKey(config.apiKey)
        return it.pagopa.generated.ecommerce.paymentmethods.v2.api.PaymentMethodsApi(apiClient)
    }

    @Bean(name = ["jwtTokenIssuerWebClient"])
    fun jwtTokenIssuerWebClient(
        config: JwtTokenIssuerConfigProperties
    ): it.pagopa.generated.jwtIssuer.api.JwtIssuerApi {
        val httpClient =
            HttpClient.create()
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, config.connectionTimeout)
                .doOnConnected { connection: Connection ->
                    connection.addHandlerLast(
                        ReadTimeoutHandler(config.readTimeout.toLong(), TimeUnit.MILLISECONDS)
                    )
                }
                .resolver { it.ndots(1) }
        val webClient =
            it.pagopa.generated.jwtIssuer.ApiClient.buildWebClientBuilder()
                .clientConnector(ReactorClientHttpConnector(httpClient))
                .defaultHeader("x-api-key", config.apiKey)
                .baseUrl(config.uri)
                .build()
        val apiClient = it.pagopa.generated.jwtIssuer.ApiClient(webClient).setBasePath(config.uri)

        return it.pagopa.generated.jwtIssuer.api.JwtIssuerApi(apiClient)
    }
}
