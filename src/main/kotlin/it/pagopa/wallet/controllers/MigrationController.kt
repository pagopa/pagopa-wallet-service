package it.pagopa.wallet.controllers

import it.pagopa.generated.wallet.api.MigrationsApi
import it.pagopa.generated.wallet.model.WalletPmAssociationRequestDto
import it.pagopa.generated.wallet.model.WalletPmAssociationResponseDto
import it.pagopa.wallet.repositories.LoggingEventRepository
import it.pagopa.wallet.services.WalletService
import lombok.extern.slf4j.Slf4j
import org.springframework.beans.factory.annotation.Autowired
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
        TODO("Not yet implemented")
    }
}