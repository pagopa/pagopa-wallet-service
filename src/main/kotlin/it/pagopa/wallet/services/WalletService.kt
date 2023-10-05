package it.pagopa.wallet.services

import it.pagopa.wallet.documents.wallet.Wallet
import it.pagopa.wallet.repositories.WalletRepository
import lombok.extern.slf4j.Slf4j
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Service
import reactor.core.publisher.Mono

@Service
@Slf4j
class WalletService(@Autowired private val walletRepository: WalletRepository) {

    fun findByWalletId(walletId: String): Mono<Wallet> {
        return walletRepository.findById(walletId)
    }
}
