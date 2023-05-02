package it.pagopa.wallet

import it.pagopa.generated.npg.model.*
import it.pagopa.generated.wallet.model.*
import it.pagopa.wallet.domain.PaymentInstrumentId
import it.pagopa.wallet.domain.Wallet
import it.pagopa.wallet.domain.WalletId
import it.pagopa.wallet.domain.details.CardDetails
import java.net.URI
import java.time.OffsetDateTime
import java.util.*
import org.springframework.http.HttpStatus

object WalletTestUtils {
    const val GATEWAY_SECURITY_TOKEN = "securityToken"

    const val USER_ID = "user-id"
    val now = OffsetDateTime.now().toString()

    val VALID_WALLET_WITH_CARD_DETAILS =
        Wallet(
            id = WalletId(UUID.randomUUID()),
            userId = USER_ID,
            status = WalletStatusDto.INITIALIZED,
            creationDate = now,
            updateDate = now,
            paymentInstrumentType = TypeDto.CARDS,
            paymentInstrumentId = PaymentInstrumentId(UUID.randomUUID()),
            gatewaySecurityToken = "securityToken",
            services = listOf(ServiceDto.PAGOPA),
            details =
                CardDetails(
                    bin = "123456",
                    maskedPan = "123456******9876",
                    expiryDate = "203012",
                    contractNumber = "contractNumber",
                    brand = WalletCardDetailsDto.BrandEnum.MASTERCARD,
                    holderName = "holder name"
                )
        )

    val VALID_WALLET_WITHOUT_INSTRUMENT_DETAILS =
        Wallet(
            id = WalletId(UUID.randomUUID()),
            userId = USER_ID,
            status = WalletStatusDto.INITIALIZED,
            creationDate = now,
            updateDate = now,
            paymentInstrumentType = TypeDto.CARDS,
            paymentInstrumentId = PaymentInstrumentId(UUID.randomUUID()),
            gatewaySecurityToken = "securityToken",
            services = listOf(ServiceDto.PAGOPA),
            details = null
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

    fun buildProblemJson(
        httpStatus: HttpStatus,
        title: String,
        description: String
    ): ProblemJsonDto = ProblemJsonDto().status(httpStatus.value()).detail(description).title(title)
}
