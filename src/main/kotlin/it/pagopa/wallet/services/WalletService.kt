package it.pagopa.wallet.services

import it.pagopa.generated.wallet.model.WalletStatusDto
import it.pagopa.wallet.audit.LoggedAction
import it.pagopa.wallet.audit.WalletAddedEvent
import it.pagopa.wallet.documents.wallets.Wallet as WalletDocument
import it.pagopa.wallet.documents.wallets.WalletService as WalletServiceDocument
import it.pagopa.wallet.documents.wallets.details.WalletDetails
import it.pagopa.wallet.domain.details.CardDetails
import it.pagopa.wallet.domain.details.WalletDetails as WalletDetailsDomain
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

        return walletRepository.save(getDocumentWallet(wallet)).map {
            LoggedAction(wallet, WalletAddedEvent(it.id))
        }
    }

    private fun getDocumentWallet(wallet: Wallet): it.pagopa.wallet.documents.wallets.Wallet =
        WalletDocument(
            wallet.id.value.toString(),
            wallet.userId.id.toString(),
            wallet.paymentMethodId.value.toString(),
            wallet.paymentInstrumentId?.value.toString(),
            wallet.contractId.contractId,
            wallet.services.map { ls ->
                WalletServiceDocument(
                    ls.id.id.toString(),
                    ls.name.name,
                    ls.status.name,
                    ls.lastUpdate.toString()
                )
            },
            getCardDetailsDocument(wallet.details)
        )

    private fun getCardDetailsDocument(details: WalletDetailsDomain?): WalletDetails? =
        when (details) {
            is CardDetails ->
                it.pagopa.wallet.documents.wallets.details.CardDetails(
                    details.type.name,
                    details.bin.bin,
                    details.maskedPan.maskedPan,
                    details.expiryDate.expDate,
                    details.brand.name,
                    details.holder.holderName
                )
            else -> null
        }
    /*
        fun updateWallet(): Mono<Unit> {}
        fun createService(): Mono<Unit> {}
        fun updateService(): Mono<Unit> {}
    */

}
