package it.pagopa.wallet

import it.pagopa.wallet.config.OnboardingConfig
import it.pagopa.wallet.config.SessionUrlConfig
import it.pagopa.wallet.config.WalletMigrationConfig
import it.pagopa.wallet.config.properties.*
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.boot.runApplication
import reactor.core.publisher.Hooks

@SpringBootApplication
@EnableConfigurationProperties(
    SessionUrlConfig::class,
    OnboardingConfig::class,
    WalletMigrationConfig::class,
    PaymentMethodsConfigProperties::class,
    ExpirationQueueConfig::class,
    RetrySavePolicyConfig::class,
    ExpirationQueueConfig::class,
    LoggedActionDeadLetterQueueConfig::class,
    JwtTokenIssuerConfigProperties::class,
    PaymentMethodsHandlerConfigProperties::class)
class WalletApplication

fun main(args: Array<String>) {
    Hooks.enableAutomaticContextPropagation()
    runApplication<WalletApplication>(*args)
}
