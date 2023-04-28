package it.pagopa.wallet

import it.pagopa.generated.npg.model.*
import it.pagopa.generated.wallet.model.*
import it.pagopa.wallet.domain.Wallet
import it.pagopa.wallet.domain.WalletId
import java.net.URI
import java.time.OffsetDateTime
import java.util.*
import org.springframework.http.HttpStatus

object WalletTestUtils {
    const val GATEWAY_SECURITY_TOKEN = "securityToken"

    const val USER_ID = "user-id"
    val now = OffsetDateTime.now().toString()

    val VALID_WALLET =
        Wallet(
            WalletId(UUID.randomUUID()),
            USER_ID,
            WalletStatusDto.INITIALIZED,
            now,
            now,
            TypeDto.CARDS,
            null,
            null,
            GATEWAY_SECURITY_TOKEN,
            listOf(ServiceDto.PAGOPA),
            null
        )

    val GATEWAY_REDIRECT_URL: URI = URI.create("http://localhost/hpp")

    val CREATE_WALLET_REQUEST: WalletCreateRequestDto =
        WalletCreateRequestDto().services(listOf(ServiceDto.PAGOPA)).type(TypeDto.CARDS)

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
