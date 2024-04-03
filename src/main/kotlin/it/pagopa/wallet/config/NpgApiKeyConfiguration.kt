package it.pagopa.wallet.config

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import it.pagopa.wallet.util.npg.NpgPspApiKeysConfig
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean

class NpgApiKeyConfiguration {

    private val objectMapper = jacksonObjectMapper()

    @Bean
    fun npgPaypalPspApiKeysConfig(
        @Value("\${wallet.onboarding.payPalPSPApiKey}") paypalApiKeys: String,
        @Value("\${wallet.onboarding.paypal.pspList}") pspToHandle: Set<String>
    ) = NpgPspApiKeysConfig.parseApiKeyConfiguration(
        jsonSecretConfiguration = paypalApiKeys,
        pspToHandle = pspToHandle,
        objectMapper = objectMapper
    ).fold(
        { throw it },
        { it }
    )
}