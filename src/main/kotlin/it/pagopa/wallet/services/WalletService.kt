package it.pagopa.wallet.services

import it.pagopa.generated.wallet.model.WalletStatusDto
import it.pagopa.wallet.audit.LoggedAction
import it.pagopa.wallet.audit.WalletAddedEvent
import it.pagopa.wallet.audit.WalletPatchEvent
import it.pagopa.wallet.domain.services.ServiceName
import it.pagopa.wallet.domain.services.ServiceStatus
import it.pagopa.wallet.domain.wallets.*
import it.pagopa.wallet.exception.WalletNotFoundException
import it.pagopa.wallet.repositories.WalletRepository
import java.time.Instant
import java.util.*
import lombok.extern.slf4j.Slf4j
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Service
import reactor.core.publisher.Mono
import reactor.kotlin.core.publisher.switchIfEmpty

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

    fun patchWallet(
        walletId: UUID,
        service: Pair<ServiceName, ServiceStatus>
    ): Mono<LoggedAction<Wallet>> {
        return walletRepository
            .findById(walletId.toString())
            .switchIfEmpty { Mono.error(WalletNotFoundException(WalletId(walletId))) }
            .map { it.toDomain() to updateServiceList(it, service) }
            .flatMap { (oldService, updatedService) ->
                walletRepository.save(updatedService).thenReturn(oldService)
            }
            .map { LoggedAction(it, WalletPatchEvent(it.id.value.toString())) }
    }

    private fun updateServiceList(
        wallet: it.pagopa.wallet.documents.wallets.Wallet,
        service: Pair<ServiceName, ServiceStatus>
    ): it.pagopa.wallet.documents.wallets.Wallet {
        val updatedServiceList = wallet.services.toMutableList()
        when (val index = wallet.services.indexOfFirst { s -> s.name == service.first.name }) {
            -1 ->
                updatedServiceList.add(
                    it.pagopa.wallet.documents.wallets.WalletService(
                        UUID.randomUUID().toString(),
                        service.first.name,
                        service.second.name,
                        Instant.now().toString()
                    )
                )
            else -> {
                val oldWalletService = updatedServiceList[index]
                updatedServiceList[index] =
                    it.pagopa.wallet.documents.wallets.WalletService(
                        oldWalletService.id,
                        oldWalletService.name,
                        service.second.name,
                        Instant.now().toString()
                    )
            }
        }
        return wallet.setServices(updatedServiceList)
    }
}
