package it.pagopa.wallet.config.properties

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "pdv-tokenizer")
data class PdvTokenizerConfigProperties(
    val uri: String,
    val readTimeout: Int,
    val connectionTimeout: Int,
    val apiKey: String,
)
