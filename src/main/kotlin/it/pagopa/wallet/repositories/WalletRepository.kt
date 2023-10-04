package it.pagopa.wallet.repositories

import it.pagopa.wallet.documents.Wallet
import org.springframework.data.repository.reactive.ReactiveCrudRepository
import org.springframework.stereotype.Repository

@Repository interface WalletRepository : ReactiveCrudRepository<Wallet, String>
