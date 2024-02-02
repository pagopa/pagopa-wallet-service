package it.pagopa.wallet.config

import lombok.Data
import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "session-url")
@Data
data class SessionUrlConfig(
    val basePath: String,
    val outcomeSuffix: String,
    val cancelSuffix: String,
    val notificationUrl: String,
    val trxWithContextualOnboardNotificationUrl: String
)
