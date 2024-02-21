package it.pagopa.wallet.controllers

import it.pagopa.generated.wallet.api.MigrationsApi
import it.pagopa.generated.wallet.model.WalletPmAssociationRequestDto
import it.pagopa.generated.wallet.model.WalletPmAssociationResponseDto
import it.pagopa.generated.wallet.model.WalletStatusDto
import java.util.*
import lombok.extern.slf4j.Slf4j
import org.springframework.http.ResponseEntity
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.server.ServerWebExchange
import reactor.core.publisher.Mono

@RestController
@Slf4j
@Validated
class MigrationController : MigrationsApi {
    override fun createWalletByPM(
        walletPmAssociationRequestDto: Mono<WalletPmAssociationRequestDto>?,
        exchange: ServerWebExchange?
    ): Mono<ResponseEntity<WalletPmAssociationResponseDto>> {
        return WalletPmAssociationResponseDto()
            .walletIdPm(123)
            .walletId(UUID.randomUUID())
            .contractId("contractId")
            .status(WalletStatusDto.CREATED)
            .let { Mono.just(ResponseEntity.ofNullable(it)) }
    }
}
