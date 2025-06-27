package it.pagopa.wallet.config.properties

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "jwt-issuer")
data class JwtTokenIssuerConfigProperties(
    val uri: String,
    val readTimeout: Int,
    val connectionTimeout: Int,
    val apiKey: String
)
