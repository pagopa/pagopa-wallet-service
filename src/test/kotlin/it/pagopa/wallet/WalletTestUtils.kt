package it.pagopa.wallet

import it.pagopa.generated.wallet.model.*
import it.pagopa.wallet.documents.service.Service
import it.pagopa.wallet.documents.wallets.Wallet
import it.pagopa.wallet.documents.wallets.WalletService as WalletServiceDocument
import it.pagopa.wallet.documents.wallets.details.CardDetails
import it.pagopa.wallet.domain.details.*
import it.pagopa.wallet.domain.services.ServiceId
import it.pagopa.wallet.domain.services.ServiceName
import it.pagopa.wallet.domain.services.ServiceStatus
import it.pagopa.wallet.domain.wallets.*
import java.time.Instant
import java.util.*
import org.springframework.http.HttpStatus

object WalletTestUtils {

    val USER_ID = UserId(UUID.randomUUID())

    val WALLET_UUID = WalletId(UUID.randomUUID())

    val SERVICE_ID = ServiceId(UUID.randomUUID())

    val PAYMENT_METHOD_ID = PaymentMethodId(UUID.randomUUID())

    val PAYMENT_INSTRUMENT_ID = PaymentInstrumentId(UUID.randomUUID())

    val SERVICE_NAME = ServiceName("TEST_SERVICE_NAME")

    val CONTRACT_ID = ContractId("TestContractId")

    val BIN = Bin("424242")
    val MASKED_PAN = MaskedPan("424242******5555")
    val EXP_DATE = ExpiryDate("203012")
    val BRAND = WalletCardDetailsDto.BrandEnum.MASTERCARD
    val HOLDER_NAME = CardHolderName("holderName")
    private val TYPE = WalletDetailsType.CARDS
    private val TIMESTAMP = Instant.now()

    val WALLET_DOCUMENT_EMPTY_SERVICES_NULL_DETAILS_NO_PAYMENT_INSTRUMENT: Wallet =
        Wallet(
            WALLET_UUID.value.toString(),
            USER_ID.id.toString(),
            PAYMENT_METHOD_ID.value.toString(),
            null,
            CONTRACT_ID.contractId,
            listOf(),
            null
        )

    val WALLET_DOCUMENT_EMPTY_SERVICES_NULL_DETAILS: Wallet =
        Wallet(
            WALLET_UUID.value.toString(),
            USER_ID.id.toString(),
            PAYMENT_METHOD_ID.value.toString(),
            PAYMENT_INSTRUMENT_ID.value.toString(),
            CONTRACT_ID.contractId,
            listOf(),
            null
        )

    val WALLET_DOCUMENT_NULL_DETAILS: Wallet =
        Wallet(
            WALLET_UUID.value.toString(),
            USER_ID.id.toString(),
            PAYMENT_METHOD_ID.value.toString(),
            PAYMENT_INSTRUMENT_ID.value.toString(),
            CONTRACT_ID.contractId,
            listOf(
                WalletServiceDocument(
                    SERVICE_ID.id.toString(),
                    SERVICE_NAME.name,
                    ServiceStatus.DISABLED.toString(),
                    TIMESTAMP.toString()
                )
            ),
            null
        )

    val WALLET_DOCUMENT: Wallet =
        Wallet(
            WALLET_UUID.value.toString(),
            USER_ID.id.toString(),
            PAYMENT_METHOD_ID.value.toString(),
            PAYMENT_INSTRUMENT_ID.value.toString(),
            CONTRACT_ID.contractId,
            listOf(
                WalletServiceDocument(
                    SERVICE_ID.id.toString(),
                    SERVICE_NAME.name,
                    ServiceStatus.DISABLED.toString(),
                    TIMESTAMP.toString()
                )
            ),
            CardDetails(
                TYPE.toString(),
                BIN.bin,
                MASKED_PAN.maskedPan,
                EXP_DATE.expDate,
                BRAND.toString(),
                HOLDER_NAME.holderName
            )
        )

    val WALLET_DOMAIN =
        Wallet(
            WALLET_UUID,
            USER_ID,
            WalletStatusDto.CREATED,
            Instant.now(),
            Instant.now(),
            PAYMENT_METHOD_ID,
            PAYMENT_INSTRUMENT_ID,
            listOf(WalletService(SERVICE_ID, SERVICE_NAME, ServiceStatus.DISABLED, TIMESTAMP)),
            CONTRACT_ID,
            CardDetails(BIN, MASKED_PAN, EXP_DATE, BRAND, HOLDER_NAME)
        )

    val WALLET_DOMAIN_NULL_DETAILS =
        Wallet(
            WALLET_UUID,
            USER_ID,
            WalletStatusDto.CREATED,
            Instant.now(),
            Instant.now(),
            PAYMENT_METHOD_ID,
            PAYMENT_INSTRUMENT_ID,
            listOf(WalletService(SERVICE_ID, SERVICE_NAME, ServiceStatus.DISABLED, TIMESTAMP)),
            CONTRACT_ID,
            null
        )

    val WALLET_DOMAIN_EMPTY_SERVICES_NULL_DETAILS =
        Wallet(
            WALLET_UUID,
            USER_ID,
            WalletStatusDto.CREATED,
            TIMESTAMP,
            TIMESTAMP,
            PAYMENT_METHOD_ID,
            PAYMENT_INSTRUMENT_ID,
            listOf(),
            CONTRACT_ID,
            null
        )

    val WALLET_DOMAIN_EMPTY_SERVICES_NULL_DETAILS_NO_PAYMENT_INSTRUMENT =
        Wallet(
            WALLET_UUID,
            USER_ID,
            WalletStatusDto.CREATED,
            TIMESTAMP,
            TIMESTAMP,
            PAYMENT_METHOD_ID,
            null,
            listOf(),
            CONTRACT_ID,
            null
        )

    val SERVICE_DOCUMENT: Service =
        Service(
            SERVICE_ID.id.toString(),
            SERVICE_NAME.name,
            ServiceStatus.DISABLED.name,
            TIMESTAMP.toString()
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
