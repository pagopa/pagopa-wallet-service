package it.pagopa.wallet

import it.pagopa.wallet.config.OnboardingConfig
import it.pagopa.wallet.config.SessionUrlConfig
import it.pagopa.wallet.config.WalletMigrationConfig
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.boot.runApplication

@SpringBootApplication
@EnableConfigurationProperties(
    SessionUrlConfig::class,
    OnboardingConfig::class,
    WalletMigrationConfig::class
)
class WalletApplication

fun main(args: Array<String>) {
    runApplication<WalletApplication>(*args)
}
