package it.pagopa.wallet.repositories

import it.pagopa.wallet.domain.Wallet
import org.springframework.data.repository.reactive.ReactiveCrudRepository
import java.util.UUID

interface WalletRepository : ReactiveCrudRepository<Wallet, UUID> {
}
