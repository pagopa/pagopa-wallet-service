package it.pagopa.wallet.controllers

import it.pagopa.generated.wallet.api.WalletsApi
import it.pagopa.generated.wallet.model.*
import it.pagopa.wallet.domain.services.ServiceName
import it.pagopa.wallet.repositories.LoggingEventRepository
import it.pagopa.wallet.services.WalletService
import java.net.URI
import java.util.*
import kotlinx.coroutines.reactor.mono
import lombok.extern.slf4j.Slf4j
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.ResponseEntity
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.server.ServerWebExchange
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono

@RestController
@Slf4j
@Validated
class WalletController(
    @Autowired private val walletService: WalletService,
    @Autowired private val loggingEventRepository: LoggingEventRepository
) : WalletsApi {
    override fun createWallet(
        walletCreateRequestDto: Mono<WalletCreateRequestDto>,
        exchange: ServerWebExchange
    ): Mono<ResponseEntity<WalletCreateResponseDto>> {

        return walletCreateRequestDto
            .flatMap {
                walletService.createWallet(
                    it.services.map { s -> ServiceName(s.name) },
                    userId = UUID.randomUUID(),
                    paymentMethodId = UUID.randomUUID(),
                    contractId = UUID.randomUUID().toString()
                )
            }
            .flatMap { it.saveEvents(loggingEventRepository) }
            .map {
                WalletCreateResponseDto()
                    .walletId(it.id.value)
                    .redirectUrl("http://checkout-return-url")
            }
            .map { ResponseEntity.created(URI.create(it.redirectUrl)).body(it) }
    }

    override fun deleteWalletById(
        walletId: UUID,
        exchange: ServerWebExchange?
    ): Mono<ResponseEntity<Void>> {
        return mono { ResponseEntity.noContent().build() }
    }

    override fun getWalletById(
        walletId: UUID,
        exchange: ServerWebExchange
    ): Mono<ResponseEntity<WalletInfoDto>> {
        return mono { ResponseEntity.ok().build() }
    }

    override fun getWalletsByIdUser(exchange: ServerWebExchange): Mono<ResponseEntity<WalletsDto>> {
        return mono { ResponseEntity.ok().build() }
    }

    override fun patchWalletById(
        walletId: UUID,
        patchServiceDto: Flux<PatchServiceDto>,
        exchange: ServerWebExchange?
    ): Mono<ResponseEntity<Void>> {
        return mono { ResponseEntity.noContent().build() }
    }
}
