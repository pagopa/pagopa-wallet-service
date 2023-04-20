package it.pagopa.wallet.controllers

import it.pagopa.generated.wallet.api.WalletsApi
import it.pagopa.generated.wallet.model.CreateWalletResponseDto
import it.pagopa.wallet.services.WalletService
import lombok.extern.slf4j.Slf4j
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.RestController

@RestController
@Slf4j
class WalletController(@Autowired private val walletService: WalletService) : WalletsApi {
    override suspend fun createWallet(body: Any): ResponseEntity<CreateWalletResponseDto> {
        val (wallet, redirectUrl) = walletService.createWallet()

        return ResponseEntity.ok(CreateWalletResponseDto(wallet.id.value, redirectUrl.toString()))
    }
}
