package it.pagopa.wallet

import it.pagopa.generated.wallet.model.*
import it.pagopa.wallet.documents.service.Service
import it.pagopa.wallet.documents.wallets.Wallet
import it.pagopa.wallet.documents.wallets.WalletService as WalletServiceDocument
import it.pagopa.wallet.documents.wallets.details.CardDetails
import it.pagopa.wallet.domain.common.ServiceId
import it.pagopa.wallet.domain.common.ServiceName
import it.pagopa.wallet.domain.common.ServiceStatus
import it.pagopa.wallet.domain.wallets.PaymentInstrumentId
import it.pagopa.wallet.domain.wallets.PaymentMethodId
import it.pagopa.wallet.domain.wallets.WalletId
import java.time.Instant
import java.time.OffsetDateTime
import java.util.*
import org.springframework.http.HttpStatus

object WalletTestUtils {

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
            contractNumber = UUID.randomUUID().toString().replace("-", ""),
            details =
                CardDetails(
                    bin = "123456",
                    maskedPan = "123456******9876",
                    expiryDate = "203012",
                    brand = WalletCardDetailsDto.BrandEnum.MASTERCARD,
                    holderName = "holder name"
                )
        )

    val WALLET_UUID = WalletId(UUID.randomUUID())

    val SERVICE_ID = ServiceId(UUID.randomUUID())

    val PAYMENT_METHOD_ID = PaymentMethodId(UUID.randomUUID())

    val PAYMENT_INSTRUMENT_ID = PaymentInstrumentId(UUID.randomUUID())

    val SERVICE_NAME = ServiceName("TEST_SERVICE_NAME")

    const val CONTRACT_ID = "TestContractId"

    val BIN = "424242"
    val MASKED_APN = "424242******5555"
    val EXP_DATE = "203012"
    val BRAND = WalletCardDetailsDto.BrandEnum.MASTERCARD
    val HOLDER_NAME = "holderName"
    val TYPE = "CARDS"

    fun walletDocumentEmptyServiceNullDetails(): Wallet =
        Wallet(
            WALLET_UUID.value.toString(),
            USER_ID,
            PAYMENT_METHOD_ID.value.toString(),
            PAYMENT_INSTRUMENT_ID.value.toString(),
            CONTRACT_ID,
            listOf(),
            null
        )

    fun walletDocumentNullDetails(): Wallet =
        Wallet(
            WALLET_UUID.value.toString(),
            USER_ID,
            PAYMENT_METHOD_ID.value.toString(),
            PAYMENT_INSTRUMENT_ID.value.toString(),
            CONTRACT_ID,
            listOf(
                WalletServiceDocument(
                    SERVICE_ID.id.toString(),
                    SERVICE_NAME.name,
                    ServiceStatus.DISABLED.toString(),
                    Instant.now().toString()
                )
            ),
            null
        )

    fun walletDocument(): Wallet =
        Wallet(
            WALLET_UUID.value.toString(),
            USER_ID,
            PAYMENT_METHOD_ID.value.toString(),
            PAYMENT_INSTRUMENT_ID.value.toString(),
            CONTRACT_ID,
            listOf(
                WalletServiceDocument(
                    SERVICE_ID.id.toString(),
                    SERVICE_NAME.name,
                    ServiceStatus.DISABLED.toString(),
                    Instant.now().toString()
                )
            ),
            CardDetails(TYPE, BIN, MASKED_APN, EXP_DATE, BRAND.toString(), HOLDER_NAME)
        )

    fun serviceDocument(): Service =
        Service(
            SERVICE_ID.id.toString(),
            SERVICE_NAME.name,
            ServiceStatus.DISABLED.name,
            Instant.now().toString()
        )

    fun buildProblemJson(
        httpStatus: HttpStatus,
        title: String,
        description: String
    ): ProblemJsonDto = ProblemJsonDto().status(httpStatus.value()).detail(description).title(title)

    val CREATE_WALLET_REQUEST: WalletCreateRequestDto =
        WalletCreateRequestDto().services(listOf(ServiceNameDto.PAGOPA)).useDiagnosticTracing(false)

    val PATCH_SERVICE_1: PatchServiceDto =
        PatchServiceDto().name(ServiceNameDto.PAGOPA).status(ServicePatchStatusDto.DISABLED)

    val PATCH_SERVICE_2: PatchServiceDto =
        PatchServiceDto().name(ServiceNameDto.PAGOPA).status(ServicePatchStatusDto.ENABLED)

    val FLUX_PATCH_SERVICES: List<PatchServiceDto> = listOf(PATCH_SERVICE_1, PATCH_SERVICE_2)
}
