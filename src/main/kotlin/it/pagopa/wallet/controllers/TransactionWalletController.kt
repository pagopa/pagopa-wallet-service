package it.pagopa.wallet.controllers

import it.pagopa.generated.wallet.api.TransactionsApi
import it.pagopa.generated.wallet.model.ClientIdDto
import it.pagopa.generated.wallet.model.WalletTransactionCreateRequestDto
import it.pagopa.generated.wallet.model.WalletTransactionCreateResponseDto
import it.pagopa.wallet.domain.wallets.OnboardingChannel
import it.pagopa.wallet.exception.InvalidRequestException
import it.pagopa.wallet.repositories.LoggingEventRepository
import it.pagopa.wallet.services.WalletService
import it.pagopa.wallet.util.TransactionId
import it.pagopa.wallet.util.WalletUtils
import java.util.*
import kotlin.jvm.optionals.getOrNull
import lombok.extern.slf4j.Slf4j
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.server.ServerWebExchange
import org.springframework.web.util.UriComponentsBuilder
import reactor.core.publisher.Mono

@RestController
@Validated
@Slf4j
class TransactionWalletController(
    @Autowired private val walletService: WalletService,
    @Autowired private val loggingEventRepository: LoggingEventRepository
) : TransactionsApi {

    override fun createWalletForTransaction(
        xUserId: UUID,
        xClientIdDto: ClientIdDto,
        transactionId: String,
        walletTransactionCreateRequestDto: Mono<WalletTransactionCreateRequestDto>,
        exchange: ServerWebExchange?
    ): Mono<ResponseEntity<WalletTransactionCreateResponseDto>> {
        if (!WalletUtils.VALID_ONBOARDING_CHANNEL.contains(xClientIdDto.toString())) {
            return Mono.error(
                InvalidRequestException(
                    "Input xClientId: [$xClientIdDto] is unknown. Handled onboarding channels: ${WalletUtils.VALID_ONBOARDING_CHANNEL}"
                )
            )
        }
        return walletTransactionCreateRequestDto
            .flatMap { request ->
                walletService
                    .createWalletForTransaction(
                        userId = xUserId,
                        paymentMethodId = request.paymentMethodId,
                        transactionId = TransactionId(transactionId),
                        amount = request.amount,
                        onboardingChannel = OnboardingChannel.valueOf(xClientIdDto.toString())
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
}
