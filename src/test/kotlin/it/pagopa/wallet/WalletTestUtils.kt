package it.pagopa.wallet

import it.pagopa.generated.npg.model.*
import it.pagopa.generated.wallet.model.PaymentInstrumentTypeDto
import it.pagopa.generated.wallet.model.ProblemJsonDto
import it.pagopa.generated.wallet.model.ServiceDto
import it.pagopa.generated.wallet.model.WalletCreateRequestDto
import it.pagopa.wallet.domain.PaymentInstrument
import it.pagopa.wallet.domain.PaymentInstrumentId
import it.pagopa.wallet.domain.Wallet
import it.pagopa.wallet.domain.WalletId
import java.net.URI
import java.util.*
import org.springframework.http.HttpStatus

object WalletTestUtils {
    const val GATEWAY_SECURITY_TOKEN = "securityToken"

    const val USER_ID = "user-id"

    val VALID_WALLET =
        Wallet(
            WalletId(UUID.randomUUID()),
            listOf(
                PaymentInstrument(PaymentInstrumentId(UUID.randomUUID()), GATEWAY_SECURITY_TOKEN)
            )
        )

    val GATEWAY_REDIRECT_URL: URI = URI.create("http://localhost/hpp")

    val CREATE_WALLET_REQUEST: WalletCreateRequestDto =
        WalletCreateRequestDto()
            .services(listOf(ServiceDto.PAGOPA))
            .paymentInstrumentType(PaymentInstrumentTypeDto.CARDS)

    fun hppRequest(): HppRequest =
        HppRequest()
            .order(
                OrderItem().orderId("orderId").amount("0").currency("EUR").customerId("customerId")
            )
            .paymentSession(
                PaymentSessionItem()
                    .actionType(PaymentSessionItem.ActionTypeEnum.VERIFY)
                    .amount("0")
                    .language("ita")
                    .resultUrl(URI.create("http://localhost/resultUrl"))
                    .cancelUrl(URI.create("http://localhost/cancelUrl"))
                    .notificationUrl(URI.create("http://localhost/notificationUrl"))
                    .paymentService(PaymentSessionItem.PaymentServiceEnum.CARDS)
                    .recurrence(
                        RecurrenceItem()
                            .action(RecurrenceItem.ActionEnum.CONTRACT_CREATION)
                            .contractId("contractId")
                            .contractType(RecurrenceItem.ContractTypeEnum.CIT)
                    )
            )

    fun hppResponse(): HppResponse =
        HppResponse()
            .hostedPage(GATEWAY_REDIRECT_URL.toString())
            .securityToken(GATEWAY_SECURITY_TOKEN)

    fun buildProblemJson(httpStatus: HttpStatus, title: String, description: String) =
        ProblemJsonDto().status(httpStatus.value()).detail(description).title(title)
}
