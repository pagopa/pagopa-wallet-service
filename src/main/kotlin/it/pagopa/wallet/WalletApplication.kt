package it.pagopa.wallet

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.scheduling.annotation.EnableScheduling

@SpringBootApplication @EnableScheduling class WalletApplication

fun main(args: Array<String>) {
    runApplication<WalletApplication>(*args)
}
