package it.pagopa.wallet.services

import it.pagopa.generated.npg.model.HppRequest
import it.pagopa.generated.npg.model.OrderItem
import it.pagopa.generated.npg.model.PaymentSessionItem
import it.pagopa.generated.npg.model.RecurrenceItem
import it.pagopa.generated.wallet.model.WalletCreateRequestDto
import it.pagopa.wallet.client.NpgClient
import it.pagopa.wallet.domain.*
import it.pagopa.wallet.exception.BadGatewayException
import it.pagopa.wallet.exception.InternalServerErrorException
import it.pagopa.wallet.repositories.WalletRepository
import java.net.URI
import java.time.OffsetDateTime
import java.util.*
import lombok.extern.slf4j.Slf4j
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Service
import reactor.core.publisher.Mono

@Service
@Slf4j
class WalletService(
    @Autowired private val walletRepository: WalletRepository,
    @Autowired private val npgClient: NpgClient
) {
    fun createWallet(
        walletCreateRequestDto: WalletCreateRequestDto,
        userId: String
    ): Mono<Pair<Wallet, URI>> {
        val paymentInstrumentId = PaymentInstrumentId(UUID.randomUUID())
        return npgClient
            .orderHpp(
                UUID.randomUUID(),
                HppRequest().apply {
                    order =
                        OrderItem().apply {
                            orderId = generateRandomString(27)
                            amount = 0.toString()
                            currency = "EUR"
                            customerId = generateRandomString(27)
                        }
                    paymentSession =
                        PaymentSessionItem().apply {
                            amount = 0.toString()
                            language = "ita"
                            resultUrl = URI.create("http://localhost")
                            cancelUrl = URI.create("http://localhost")
                            notificationUrl = URI.create("http://localhost")
                            paymentService = PaymentSessionItem.PaymentServiceEnum.CARDS
                            actionType = PaymentSessionItem.ActionTypeEnum.VERIFY
                            recurrence =
                                RecurrenceItem().apply {
                                    action = RecurrenceItem.ActionEnum.CONTRACT_CREATION
                                    contractId = generateRandomString(18)
                                    contractType = RecurrenceItem.ContractTypeEnum.CIT
                                }
                        }
                }
            )
            .onErrorMap {
                BadGatewayException(
                        "Could not send order to payment gateway. Reason: ${it.message}"
                    )
                    .initCause(it)
            }
            .map {
                val securityToken =
                    it.securityToken
                        ?: throw BadGatewayException(
                            "Payment gateway didn't provide security token"
                        )
                val redirectUrl =
                    it.hostedPage
                        ?: throw BadGatewayException("Payment gateway didn't provide redirect URL")
                Pair(securityToken, redirectUrl)
            }
            .flatMap { (securityToken, redirectUrl) ->
                val now = OffsetDateTime.now().toString()

                // TODO: update null values
                val wallet =
                    Wallet(
                        WalletId(UUID.randomUUID()),
                        userId,
                        WalletStatus.INITIALIZED,
                        now,
                        now,
                        PaymentInstrumentType.valueOf(walletCreateRequestDto.type.value),
                        null,
                        null,
                        securityToken,
                        walletCreateRequestDto.services.map { service ->
                            WalletServiceEnum.valueOf(service.value)
                        },
                        null
                    )

                walletRepository
                    .save(wallet)
                    .map { savedWallet -> Pair(savedWallet, URI.create(redirectUrl)) }
                    .onErrorMap {
                        InternalServerErrorException(
                            "Error saving wallet to DB, ${it.localizedMessage}"
                        )
                    }
            }
    }

    private fun generateRandomString(length: Int): String {
        val allowedChars = ('A'..'Z') + ('a'..'z') + ('0'..'9')
        return (1..length).map { allowedChars.random() }.joinToString("")
    }
}
