package it.pagopa.wallet.services

import it.pagopa.generated.wallet.model.WalletStatusDto
import it.pagopa.wallet.audit.LoggedAction
import it.pagopa.wallet.audit.WalletAddedEvent
import it.pagopa.wallet.domain.wallets.*
import it.pagopa.wallet.repositories.WalletRepository
import java.time.Instant
import java.util.*
import lombok.extern.slf4j.Slf4j
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Service
import reactor.core.publisher.Mono

@Service
@Slf4j
class WalletService(@Autowired private val walletRepository: WalletRepository) {

    fun createWallet(
        serviceList: List<it.pagopa.wallet.domain.services.ServiceName>,
        userId: UUID,
        paymentMethodId: UUID,
        contractId: String
    ): Mono<LoggedAction<Wallet>> {
        val creationTime = Instant.now()
        val wallet =
            Wallet(
                WalletId(UUID.randomUUID()),
                UserId(userId),
                WalletStatusDto.CREATED,
                creationTime,
                creationTime,
                PaymentMethodId(paymentMethodId),
                paymentInstrumentId = null,
                listOf(), // TODO Find all services by serviceName
                ContractId(contractId),
                details = null
            )

        return walletRepository.save(wallet.toDocument()).map {
            LoggedAction(wallet, WalletAddedEvent(it.id))
        }
    }
}
