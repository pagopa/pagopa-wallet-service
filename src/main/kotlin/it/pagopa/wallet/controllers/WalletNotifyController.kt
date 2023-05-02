package it.pagopa.wallet.controllers

import it.pagopa.generated.npgnotification.api.NotifyApi
import it.pagopa.generated.npgnotification.model.NotificationRequestDto
import it.pagopa.wallet.services.WalletService
import java.util.*
import lombok.extern.slf4j.Slf4j
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.server.ServerWebExchange
import reactor.core.publisher.Mono

@RestController
@Slf4j
class WalletNotifyController(@Autowired private val walletService: WalletService) : NotifyApi {

    override fun notifyWallet(
        correlationId: UUID?,
        notificationRequestDto: Mono<NotificationRequestDto>?,
        exchange: ServerWebExchange?
    ): Mono<ResponseEntity<Void>> {
        return notificationRequestDto!!
            .flatMap { n -> walletService.notify(correlationId!!, n) }
            .mapNotNull { ResponseEntity.ok().build() }
    }
}
