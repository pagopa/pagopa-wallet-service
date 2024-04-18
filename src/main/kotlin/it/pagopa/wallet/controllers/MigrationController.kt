package it.pagopa.wallet.controllers

import it.pagopa.generated.wallet.api.MigrationsApi
import it.pagopa.generated.wallet.model.*
import it.pagopa.wallet.domain.wallets.ContractId
import it.pagopa.wallet.domain.wallets.UserId
import it.pagopa.wallet.domain.wallets.details.*
import it.pagopa.wallet.services.MigrationService
import it.pagopa.wallet.util.Tracing
import it.pagopa.wallet.util.Tracing.Migration.CONTRACT_HMAC
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import lombok.extern.slf4j.Slf4j
import org.springframework.http.ResponseEntity
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.server.ServerWebExchange
import reactor.core.publisher.Mono

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
        walletPmCardDetailsRequestDto: Mono<WalletPmCardDetailsRequestDto>,
        xContractHmac: String?,
        exchange: ServerWebExchange?
    ): Mono<ResponseEntity<WalletPmCardDetailsResponseDto>> =
        Tracing.customizeSpan(walletPmCardDetailsRequestDto) {
                xContractHmac?.also { setAttribute(CONTRACT_HMAC, it) }
            }
            .flatMap { request ->
                migrationService.updateWalletCardDetails(
                    contractId = ContractId(request.contractIdentifier),
                    cardDetails =
                        CardDetails(
                            bin = Bin(request.cardBin),
                            lastFourDigits = LastFourDigits(request.lastFourDigits),
                            expiryDate = parseExpiryDateMMYY(request.expiryDate),
                            brand = request.paymentCircuit,
                            paymentInstrumentGatewayId =
                                PaymentInstrumentGatewayId(request.paymentGatewayCardId)
                        )
                )
            }
            .map { ResponseEntity.ok(WalletPmCardDetailsResponseDto().walletId(it.id.value)) }
            .contextWrite { context ->
                xContractHmac?.let { context.put(CONTRACT_HMAC, it) } ?: context
            }

    @SuppressWarnings("kotlin:S6508")
    override fun removeWalletByPM(
        walletPmDeleteRequestDto: Mono<WalletPmDeleteRequestDto>,
        xContractHmac: String?,
        exchange: ServerWebExchange?
    ): Mono<ResponseEntity<Void>> =
        Tracing.customizeSpan(walletPmDeleteRequestDto) {
                xContractHmac?.also { setAttribute(CONTRACT_HMAC, it) }
            }
            .flatMap { migrationService.deleteWallet(ContractId(it.contractIdentifier)) }
            .map<ResponseEntity<Void>?> { ResponseEntity.noContent().build() }
            .contextWrite { context ->
                xContractHmac?.let { context.put(CONTRACT_HMAC, it) } ?: context
            }

    private fun parseExpiryDateMMYY(expiryDate: String): ExpiryDate =
        ExpiryDate.fromYearMonth(YearMonth.parse(expiryDate, DateTimeFormatter.ofPattern("MM/yy")))
}
