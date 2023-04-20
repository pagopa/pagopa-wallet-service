package it.pagopa.wallet.config

import io.netty.channel.ChannelOption
import io.netty.handler.timeout.ReadTimeoutHandler
import it.pagopa.generated.npg.api.DefaultApi
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
    ): it.pagopa.generated.npg.api.DefaultApi {
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
        return DefaultApi(apiClient)
    }
}
