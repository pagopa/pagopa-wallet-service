package it.pagopa.wallet.config

import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jdk8.Jdk8Module
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.kotlinModule
import it.pagopa.wallet.audit.WalletQueueEvent
import it.pagopa.wallet.common.serialization.WalletQueueEventMixin
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder

@Configuration
class SerializationConfiguration {

    // enrich spring object mapper by providing mixin and some other useful modules
    @Bean
    fun objectMapperBuilder(): Jackson2ObjectMapperBuilder =
        Jackson2ObjectMapperBuilder()
            .modules(Jdk8Module(), JavaTimeModule(), kotlinModule())
            .featuresToDisable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
            .serializationInclusion(JsonInclude.Include.NON_NULL)
            .mixIn(WalletQueueEvent::class.java, WalletQueueEventMixin::class.java)
}
