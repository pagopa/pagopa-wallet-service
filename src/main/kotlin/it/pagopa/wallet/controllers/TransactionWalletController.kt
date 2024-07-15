package it.pagopa.wallet.controllers

import it.pagopa.generated.wallet.api.TransactionsApi
import it.pagopa.generated.wallet.model.ClientIdDto
import it.pagopa.generated.wallet.model.WalletTransactionCreateRequestDto
import it.pagopa.generated.wallet.model.WalletTransactionCreateResponseDto
import it.pagopa.wallet.repositories.LoggingEventRepository
import it.pagopa.wallet.services.WalletService
import it.pagopa.wallet.util.TransactionId
import it.pagopa.wallet.util.toOnboardingChannel
import it.pagopa.wallet.warmup.annotations.WarmupFunction
import it.pagopa.wallet.warmup.utils.WarmupUtils
import java.time.Duration
import java.util.*
import kotlin.jvm.optionals.getOrNull
import lombok.extern.slf4j.Slf4j
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.server.ServerWebExchange
import org.springframework.web.util.UriComponentsBuilder
import reactor.core.publisher.Mono

@RestController
@Validated
@Slf4j
class TransactionWalletController(
    @Autowired private val walletService: WalletService,
    @Autowired private val loggingEventRepository: LoggingEventRepository,
    private val webClient: WebClient = WebClient.create()
) : TransactionsApi {

    override fun createWalletForTransaction(
        xUserId: UUID,
        xClientIdDto: ClientIdDto,
        transactionId: String,
        walletTransactionCreateRequestDto: Mono<WalletTransactionCreateRequestDto>,
        exchange: ServerWebExchange?
    ): Mono<ResponseEntity<WalletTransactionCreateResponseDto>> {
        return walletTransactionCreateRequestDto
            .flatMap { request ->
                walletService
                    .createWalletForTransaction(
                        userId = xUserId,
                        paymentMethodId = request.paymentMethodId,
                        transactionId = TransactionId(transactionId),
                        amount = request.amount,
                        onboardingChannel = xClientIdDto.toOnboardingChannel()
                    )
                    .flatMap { (loggedAction, returnUri) ->
                        loggedAction.saveEvents(loggingEventRepository).map {
                            Triple(it.id.value, request, returnUri)
                        }
                    }
            }
            .map { (walletId, request, returnUri) ->
                val response = WalletTransactionCreateResponseDto()
                if (returnUri.getOrNull() != null) {
                    response.redirectUrl(
                        UriComponentsBuilder.fromUri(returnUri.get())
                            .fragment(
                                "walletId=${walletId}&useDiagnosticTracing=${request.useDiagnosticTracing}"
                            )
                            .build()
                            .toUriString()
                    )
                }
                response.walletId(walletId)
            }
            .map { ResponseEntity.status(HttpStatus.CREATED).body(it) }
    }

    @WarmupFunction
    fun createTransactionWalletWarmup() {
        webClient
            .post()
            .uri(
                WarmupUtils.TRANSACTION_WALLET_URI,
                mapOf("transactionId" to WarmupUtils.mockedUUID)
            )
            .contentType(MediaType.APPLICATION_JSON)
            .header(WarmupUtils.USER_ID_HEADER_KEY, WarmupUtils.mockedUUID.toString())
            .header(WarmupUtils.CLIENT_ID_HEADER_KEY, "IO")
            .bodyValue(WarmupUtils.createTransactionWalletRequestDto)
            .retrieve()
            .toBodilessEntity()
            .block(Duration.ofSeconds(10))
    }
}
