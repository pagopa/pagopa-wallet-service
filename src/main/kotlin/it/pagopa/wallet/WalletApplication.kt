package it.pagopa.wallet

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.data.mongodb.config.EnableReactiveMongoAuditing

@EnableReactiveMongoAuditing @SpringBootApplication class WalletApplication

fun main(args: Array<String>) {
    runApplication<WalletApplication>(*args)
}
