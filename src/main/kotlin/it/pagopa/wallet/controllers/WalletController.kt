package it.pagopa.wallet.controllers

import it.pagopa.generated.wallet.api.WalletsApi
import it.pagopa.generated.wallet.model.WalletCreateRequestDto
import it.pagopa.generated.wallet.model.WalletCreateResponseDto
import it.pagopa.generated.wallet.model.WalletInfoDto
import it.pagopa.wallet.services.WalletService
import java.util.*
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
class WalletController(
    @Autowired private val walletService: WalletService,
) : WalletsApi {

    override fun createWallet(
        xUserId: String,
        walletCreateRequestDto: Mono<WalletCreateRequestDto>,
        exchange: ServerWebExchange?
    ): Mono<ResponseEntity<WalletCreateResponseDto>> =
        walletCreateRequestDto.flatMap {
            walletService.createWallet(it, xUserId).map { (wallet, redirectUrl) ->
                ResponseEntity.ok(
                    WalletCreateResponseDto()
                        .walletId(wallet.id.value)
                        .redirectUrl(redirectUrl.toString())
                )
            }
        }

    override fun getWalletById(
        walletId: UUID,
        exchange: ServerWebExchange?
    ): Mono<ResponseEntity<WalletInfoDto>> {
        TODO("Not yet implemented")
    }
}
