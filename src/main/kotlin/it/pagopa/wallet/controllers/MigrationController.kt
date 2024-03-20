package it.pagopa.wallet.controllers

import it.pagopa.generated.wallet.api.MigrationsApi
import it.pagopa.generated.wallet.model.*
import it.pagopa.wallet.domain.wallets.ContractId
import it.pagopa.wallet.domain.wallets.UserId
import it.pagopa.wallet.domain.wallets.details.*
import it.pagopa.wallet.services.MigrationService
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import lombok.extern.slf4j.Slf4j
import org.springframework.http.ResponseEntity
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.server.ServerWebExchange
import reactor.core.publisher.Mono
import reactor.kotlin.core.publisher.toMono

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
        exchange: ServerWebExchange?
    ): Mono<ResponseEntity<WalletPmCardDetailsResponseDto>> {
        return walletPmCardDetailsRequestDto
            .flatMap { request ->
                migrationService.updateWalletCardDetails(
                    contractId = ContractId(request.newContractIdentifier),
                    cardDetails =
                        CardDetails(
                            bin = Bin(request.cardBin),
                            lastFourDigits = LastFourDigits(request.lastFourDigits),
                            expiryDate = parseExpiryDateMMYY(request.expiryDate),
                            brand =
                                WalletCardDetailsDto.BrandEnum.fromValue(request.paymentCircuit),
                            paymentInstrumentGatewayId =
                                PaymentInstrumentGatewayId(request.paymentGatewayCardId)
                        )
                )
            }
            .map { ResponseEntity.ok(WalletPmCardDetailsResponseDto().walletId(it.id.value)) }
    }

    @SuppressWarnings("kotlin:S6508")
    override fun removeWalletByPM(
        walletPmDeleteRequestDto: Mono<WalletPmDeleteRequestDto>?,
        exchange: ServerWebExchange?
    ): Mono<ResponseEntity<Void>> = ResponseEntity.noContent().build<Void>().toMono()

    private fun parseExpiryDateMMYY(expiryDate: String): ExpiryDate =
        ExpiryDate.fromYearMonth(YearMonth.parse(expiryDate, DateTimeFormatter.ofPattern("MM/yy")))
}
