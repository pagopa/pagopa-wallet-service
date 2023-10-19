package it.pagopa.wallet.services

import it.pagopa.generated.wallet.model.*
import it.pagopa.wallet.audit.LoggedAction
import it.pagopa.wallet.audit.WalletAddedEvent
import it.pagopa.wallet.audit.WalletPatchEvent
import it.pagopa.wallet.documents.wallets.details.CardDetails
import it.pagopa.wallet.documents.wallets.details.WalletDetails
import it.pagopa.wallet.domain.services.ServiceName
import it.pagopa.wallet.domain.services.ServiceStatus
import it.pagopa.wallet.domain.wallets.*
import it.pagopa.wallet.exception.WalletNotFoundException
import it.pagopa.wallet.repositories.WalletRepository
import java.time.Instant
import java.time.OffsetDateTime
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

    fun findWallet(walletId: UUID): Mono<WalletInfoDto> {
        return walletRepository
            .findById(walletId.toString())
            .switchIfEmpty { Mono.error(WalletNotFoundException(WalletId(walletId))) }
            .map { wallet ->
                WalletInfoDto()
                    .walletId(UUID.fromString(wallet.id))
                    .status(WalletStatusDto.valueOf(wallet.status))
                    .paymentMethodId(wallet.paymentMethodId)
                    .paymentInstrumentId(wallet.paymentInstrumentId.let { it.toString() })
                    .userId(wallet.userId)
                    .updateDate(OffsetDateTime.parse(wallet.updateDate))
                    .creationDate(OffsetDateTime.parse(wallet.creationDate))
                    .services(
                        wallet.applications.map { application ->
                            ServiceDto()
                                .name(ServiceNameDto.valueOf(application.name))
                                .status(ServiceStatusDto.valueOf(application.status))
                        }
                    )
                    .details(toWalletInfoDetailsDto(wallet.details))
            }
    }

    private fun toWalletInfoDetailsDto(details: WalletDetails<*>?): WalletInfoDetailsDto? {
        return when (details) {
            is CardDetails ->
                WalletCardDetailsDto()
                    .type(details.type)
                    .bin(details.bin)
                    .holder(details.holder)
                    .expiryDate(details.expiryDate)
                    .maskedPan(details.maskedPan)
            else -> null
        }
    }

    private fun updateServiceList(
        wallet: it.pagopa.wallet.documents.wallets.Wallet,
        dataService: Pair<ServiceName, ServiceStatus>
    ): it.pagopa.wallet.documents.wallets.Wallet {
        val updatedServiceList = wallet.applications.toMutableList()
        when (
            val index = wallet.applications.indexOfFirst { s -> s.name == dataService.first.name }
        ) {
            -1 ->
                updatedServiceList.add(
                    it.pagopa.wallet.documents.wallets.Application(
                        UUID.randomUUID().toString(),
                        dataService.first.name,
                        dataService.second.name,
                        Instant.now().toString()
                    )
                )
            else -> {
                val oldWalletService = updatedServiceList[index]
                updatedServiceList[index] =
                    it.pagopa.wallet.documents.wallets.Application(
                        oldWalletService.id,
                        oldWalletService.name,
                        dataService.second.name,
                        Instant.now().toString()
                    )
            }
        }
        return wallet.setApplications(updatedServiceList)
    }
}
