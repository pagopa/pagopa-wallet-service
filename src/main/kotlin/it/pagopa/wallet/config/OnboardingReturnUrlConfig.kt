package it.pagopa.wallet.config

import java.net.URI
import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "wallet.onboarding")
data class OnboardingReturnUrlConfig(val cardReturnUrl: URI, val apmReturnUrl: URI)
