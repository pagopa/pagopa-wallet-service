package it.pagopa.wallet.repositories

import it.pagopa.wallet.domain.Wallet
import java.util.UUID
import org.springframework.data.repository.reactive.ReactiveCrudRepository

interface WalletRepository : ReactiveCrudRepository<Wallet, UUID> {}
