package it.pagopa.wallet.controllers

import it.pagopa.generated.npg.api.NotifyApi
import it.pagopa.generated.npg.model.NotificationRequestDto
import it.pagopa.wallet.services.WalletService
import java.util.*
import lombok.extern.slf4j.Slf4j
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.RestController

@RestController
@Slf4j
class WalletNotifyController(@Autowired private val walletService: WalletService) : NotifyApi {
    override suspend fun notifyWallet(
        correlationUuid: UUID,
        notificationRequestDto: NotificationRequestDto
    ): ResponseEntity<Unit> {
        val (wallet, redirectUrl) = walletService.notify(correlationUuid, notificationRequestDto)

        return ResponseEntity.ok(Unit)
    }
}
