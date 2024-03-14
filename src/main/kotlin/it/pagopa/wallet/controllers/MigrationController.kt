package it.pagopa.wallet.controllers

import it.pagopa.generated.wallet.api.MigrationsApi
import it.pagopa.generated.wallet.model.WalletPmAssociationRequestDto
import it.pagopa.generated.wallet.model.WalletPmAssociationResponseDto
import it.pagopa.generated.wallet.model.WalletPmCardDetailsRequestDto
import it.pagopa.generated.wallet.model.WalletPmCardDetailsResponseDto
import it.pagopa.wallet.domain.wallets.UserId
import it.pagopa.wallet.services.MigrationService
import java.util.*
import lombok.extern.slf4j.Slf4j
import org.springframework.http.ResponseEntity
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.server.ServerWebExchange
import reactor.core.publisher.Mono
import reactor.kotlin.core.publisher.toMono

@RestController
@Slf4j
@Validated
class MigrationController(private val migrationService: MigrationService) : MigrationsApi {
    override fun createWalletByPM(
        walletPmAssociationRequestDto: Mono<WalletPmAssociationRequestDto>,
        exchange: ServerWebExchange?
    ): Mono<ResponseEntity<WalletPmAssociationResponseDto>> =
        walletPmAssociationRequestDto
            .flatMap { request ->
                migrationService
                    .initializeWalletByPaymentManager(
                        request.walletIdPm.toString(),
                        UserId(request.userId)
                    )
                    .map {
                        WalletPmAssociationResponseDto()
                            .walletIdPm(request.walletIdPm)
                            .walletId(it.id.value)
                            .contractId(it.contractId!!.contractId)
                            .status(it.status)
                    }
            }
            .map { ResponseEntity.ok(it) }

    override fun updateWalletDetailsByPM(
        walletPmCardDetailsRequestDto: Mono<WalletPmCardDetailsRequestDto>?,
        exchange: ServerWebExchange?
    ): Mono<ResponseEntity<WalletPmCardDetailsResponseDto>> {
        return ResponseEntity.ok(WalletPmCardDetailsResponseDto().walletId(UUID.randomUUID()))
            .toMono()
    }
}
