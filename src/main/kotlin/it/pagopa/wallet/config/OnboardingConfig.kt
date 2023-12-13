package it.pagopa.wallet.config

import java.net.URI
import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "wallet.onboarding")
data class OnboardingConfig(
    val cardReturnUrl: URI,
    val apmReturnUrl: URI,
    val payPalPSPApiKey: String
)
