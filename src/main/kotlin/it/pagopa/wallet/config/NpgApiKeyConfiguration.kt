package it.pagopa.wallet.config

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import it.pagopa.wallet.util.npg.NpgPspApiKeysConfig
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class NpgApiKeyConfiguration {

    private val objectMapper = jacksonObjectMapper()

    @Bean
    fun npgPaypalPspApiKeysConfig(
        @Value("\${wallet.onboarding.paypal.apiKeys}") paypalApiKeys: String,
        @Value("\${wallet.onboarding.paypal.pspList}") pspToHandle: Set<String>,
        @Value("\${npgService.apiKey}") defaultApiKey: String
    ) =
        NpgPspApiKeysConfig.parseApiKeyConfiguration(
                jsonSecretConfiguration = paypalApiKeys,
                pspToHandle = pspToHandle,
                objectMapper = objectMapper,
                defaultApiKey = defaultApiKey
            )
            .fold({ throw it }, { it })
}
