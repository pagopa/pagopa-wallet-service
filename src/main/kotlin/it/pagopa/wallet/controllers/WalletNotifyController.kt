package it.pagopa.wallet.controllers

import it.pagopa.generated.npgnotification.api.NotifyApi
import it.pagopa.generated.npgnotification.model.NotificationRequestDto
import it.pagopa.generated.wallet.model.WalletStatusDto
import it.pagopa.wallet.exception.InternalServerErrorException
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
        correlationId: UUID,
        notificationRequestDto: NotificationRequestDto
    ): ResponseEntity<Unit> {
        val wallet = walletService.notify(correlationId, notificationRequestDto)
        return wallet.let {
            if (it.status == WalletStatusDto.CREATED || it.status == WalletStatusDto.ERROR) {
                ResponseEntity.noContent().build()
            } else {
                throw InternalServerErrorException("Internal server error")
            }
        }
    }
}
