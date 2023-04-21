package it.pagopa.wallet.services

import it.pagopa.generated.npg.model.HppRequest
import it.pagopa.generated.npg.model.OrderItem
import it.pagopa.generated.npg.model.PaymentSessionItem
import it.pagopa.generated.npg.model.RecurrenceItem
import it.pagopa.wallet.client.NpgClient
import it.pagopa.wallet.domain.PaymentInstrument
import it.pagopa.wallet.domain.PaymentInstrumentId
import it.pagopa.wallet.domain.Wallet
import it.pagopa.wallet.domain.WalletId
import it.pagopa.wallet.exception.BadGatewayException
import it.pagopa.wallet.repositories.WalletRepository
import java.net.URI
import java.util.*
import kotlinx.coroutines.reactor.awaitSingle
import kotlinx.coroutines.reactor.awaitSingleOrNull
import lombok.extern.slf4j.Slf4j
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Service

@Service
@Slf4j
class WalletService(
    @Autowired private val walletRepository: WalletRepository,
    @Autowired private val npgClient: NpgClient
) {
    suspend fun createWallet(): Pair<Wallet, URI> {
        val paymentInstrumentId = PaymentInstrumentId(UUID.randomUUID())

        val paymentGatewayResponse =
            try {
                npgClient
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
                                    actionType = PaymentSessionItem.ActionTypeEnum.VERIFY
                                    recurrence =
                                        RecurrenceItem().apply {
                                            action = RecurrenceItem.ActionEnum.CONTRACT_CREATION
                                            contractId = paymentInstrumentId.value.toString()
                                            contractType = RecurrenceItem.ContractTypeEnum.CIT
                                        }
                                }
                        }
                    )
                    .awaitSingle()
            } catch (e: Throwable) {
                throw BadGatewayException(
                        "Could not send order to payment gateway. Reason: ${e.message}"
                    )
                    .initCause(e)
            }

        val securityToken =
            paymentGatewayResponse.securityToken
                ?: throw BadGatewayException("Payment gateway didn't provide security token")
        val paymentInstrument = PaymentInstrument(paymentInstrumentId, securityToken)

        val wallet = Wallet(WalletId(UUID.randomUUID()), listOf(paymentInstrument))
        val savedWallet =
            walletRepository.save(wallet).awaitSingleOrNull()
                ?: throw BadGatewayException("Could not save wallet to DB")

        val redirectUrl =
            paymentGatewayResponse.hostedPage
                ?: throw BadGatewayException("Payment gateway didn't provide redirect URL")

        return Pair(savedWallet, URI.create(redirectUrl))
    }

    private fun generateRandomString(length: Int): String {
        val allowedChars = ('A'..'Z') + ('a'..'z') + ('0'..'9')
        return (1..length).map { allowedChars.random() }.joinToString("")
    }
}
