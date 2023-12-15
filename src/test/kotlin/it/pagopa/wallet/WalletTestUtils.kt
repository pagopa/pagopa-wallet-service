package it.pagopa.wallet

import it.pagopa.generated.ecommerce.model.PaymentMethodResponse
import it.pagopa.generated.ecommerce.model.PaymentMethodStatus
import it.pagopa.generated.wallet.model.*
import it.pagopa.generated.wallet.model.WalletNotificationRequestDto.OperationResultEnum
import it.pagopa.wallet.documents.service.Service
import it.pagopa.wallet.documents.wallets.Application as WalletServiceDocument
import it.pagopa.wallet.documents.wallets.Wallet
import it.pagopa.wallet.documents.wallets.details.CardDetails
import it.pagopa.wallet.domain.details.*
import it.pagopa.wallet.domain.services.ServiceId
import it.pagopa.wallet.domain.services.ServiceName
import it.pagopa.wallet.domain.services.ServiceStatus
import it.pagopa.wallet.domain.wallets.*
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneId
import java.util.*
import org.springframework.http.HttpStatus

object WalletTestUtils {

    val USER_ID = UserId(UUID.randomUUID())

    val WALLET_UUID = WalletId(UUID.randomUUID())

    val SERVICE_ID = ServiceId(UUID.randomUUID())

    val PAYMENT_METHOD_ID_CARDS = PaymentMethodId(UUID.randomUUID())
    val PAYMENT_METHOD_ID_APM = PaymentMethodId(UUID.randomUUID())

    val PAYMENT_INSTRUMENT_ID = PaymentInstrumentId(UUID.randomUUID())

    val SERVICE_NAME = ServiceName("PAGOPA")

    val CONTRACT_ID = ContractId("TestContractId")

    val BIN = Bin("42424242")
    val MASKED_PAN = MaskedPan("42424242****5555")
    val EXP_DATE = ExpiryDate("12/30")
    val BRAND = WalletCardDetailsDto.BrandEnum.MASTERCARD
    val HOLDER_NAME = CardHolderName("holderName")
    const val ORDER_ID = "WFHDJFIRUT48394832"
    private val TYPE = WalletDetailsType.CARDS
    val TIMESTAMP: Instant = Instant.now()

    val creationDate: Instant = Instant.now()

    fun walletDocumentWithSessionWallet(): Wallet {
        val wallet =
            Wallet(
                WALLET_UUID.value.toString(),
                USER_ID.id.toString(),
                WalletStatusDto.INITIALIZED.name,
                PAYMENT_METHOD_ID_CARDS.value.toString(),
                null,
                CONTRACT_ID.contractId,
                null,
                listOf(),
                null,
                0,
                creationDate,
                creationDate
            )
        return wallet
    }

    fun walletDocumentWithSessionWallet(contractId: ContractId): Wallet {
        val wallet =
            Wallet(
                WALLET_UUID.value.toString(),
                USER_ID.id.toString(),
                WalletStatusDto.INITIALIZED.name,
                PAYMENT_METHOD_ID_CARDS.value.toString(),
                null,
                contractId.contractId,
                null,
                listOf(),
                null,
                0,
                creationDate,
                creationDate
            )
        return wallet
    }

    fun walletDocumentVerifiedWithCardDetails(
        bin: String,
        lastFourDigits: String,
        expiryDate: String,
        holderName: String,
        brandEnum: WalletCardDetailsDto.BrandEnum
    ): Wallet {
        val wallet =
            Wallet(
                WALLET_UUID.value.toString(),
                USER_ID.id.toString(),
                WalletStatusDto.VALIDATION_REQUESTED.name,
                PAYMENT_METHOD_ID_CARDS.value.toString(),
                null,
                CONTRACT_ID.contractId,
                null,
                listOf(),
                CardDetails(
                    WalletDetailsType.CARDS.name,
                    bin,
                    bin + "*".repeat(4) + lastFourDigits,
                    expiryDate,
                    brandEnum.name,
                    holderName
                ),
                0,
                creationDate,
                creationDate
            )
        return wallet
    }

    fun walletDocumentVerifiedWithAPM(): Wallet {
        val wallet =
            Wallet(
                WALLET_UUID.value.toString(),
                USER_ID.id.toString(),
                WalletStatusDto.VALIDATION_REQUESTED.name,
                PAYMENT_METHOD_ID_CARDS.value.toString(),
                null,
                CONTRACT_ID.contractId,
                null,
                listOf(),
                null,
                0,
                creationDate,
                creationDate
            )
        return wallet
    }

    fun walletDocumentWithError(operationResultEnum: OperationResultEnum): Wallet {
        val wallet =
            Wallet(
                WALLET_UUID.value.toString(),
                USER_ID.id.toString(),
                WalletStatusDto.ERROR.name,
                PAYMENT_METHOD_ID_CARDS.value.toString(),
                null,
                CONTRACT_ID.contractId,
                operationResultEnum.value,
                listOf(),
                null,
                0,
                creationDate,
                creationDate
            )
        return wallet
    }

    fun walletDocumentValidated(): Wallet {
        val wallet =
            Wallet(
                WALLET_UUID.value.toString(),
                USER_ID.id.toString(),
                WalletStatusDto.VALIDATED.name,
                PAYMENT_METHOD_ID_CARDS.value.toString(),
                null,
                CONTRACT_ID.contractId,
                OperationResultEnum.EXECUTED.toString(),
                listOf(),
                null,
                0,
                creationDate,
                creationDate
            )
        return wallet
    }

    fun walletDocumentValidated(operationResultEnum: OperationResultEnum): Wallet {
        val wallet =
            Wallet(
                WALLET_UUID.value.toString(),
                USER_ID.id.toString(),
                WalletStatusDto.VALIDATED.name,
                PAYMENT_METHOD_ID_CARDS.value.toString(),
                null,
                CONTRACT_ID.contractId,
                operationResultEnum.value,
                listOf(),
                null,
                0,
                creationDate,
                creationDate
            )
        return wallet
    }

    fun walletDocumentEmptyServicesNullDetailsNoPaymentInstrument(): Wallet {
        val wallet =
            Wallet(
                WALLET_UUID.value.toString(),
                USER_ID.id.toString(),
                WalletStatusDto.CREATED.name,
                PAYMENT_METHOD_ID_CARDS.value.toString(),
                null,
                CONTRACT_ID.contractId,
                null,
                listOf(),
                null,
                0,
                creationDate,
                creationDate
            )
        return wallet
    }

    fun walletDocumentEmptyServicesNullDetails(): Wallet {
        val wallet =
            Wallet(
                WALLET_UUID.value.toString(),
                USER_ID.id.toString(),
                WalletStatusDto.CREATED.name,
                PAYMENT_METHOD_ID_CARDS.value.toString(),
                PAYMENT_INSTRUMENT_ID.value.toString(),
                CONTRACT_ID.contractId,
                null,
                listOf(),
                null,
                0,
                creationDate,
                creationDate
            )
        return wallet
    }

    fun walletDocumentEmptyContractId(): Wallet {
        val wallet =
            Wallet(
                WALLET_UUID.value.toString(),
                USER_ID.id.toString(),
                WalletStatusDto.CREATED.name,
                PAYMENT_METHOD_ID_CARDS.value.toString(),
                PAYMENT_INSTRUMENT_ID.value.toString(),
                null,
                null,
                listOf(),
                null,
                0,
                creationDate,
                creationDate
            )
        return wallet
    }

    fun walletDocumentWithEmptyValidationOperationResult(): Wallet {
        val wallet =
            Wallet(
                WALLET_UUID.value.toString(),
                USER_ID.id.toString(),
                WalletStatusDto.CREATED.name,
                PAYMENT_METHOD_ID_CARDS.value.toString(),
                PAYMENT_INSTRUMENT_ID.value.toString(),
                null,
                OperationResultEnum.EXECUTED.value,
                listOf(),
                null,
                0,
                creationDate,
                creationDate
            )
        return wallet
    }

    fun walletDocumentNullDetails(): Wallet {
        val wallet =
            Wallet(
                WALLET_UUID.value.toString(),
                USER_ID.id.toString(),
                WalletStatusDto.CREATED.name,
                PAYMENT_METHOD_ID_CARDS.value.toString(),
                PAYMENT_INSTRUMENT_ID.value.toString(),
                CONTRACT_ID.contractId,
                null,
                listOf(
                    WalletServiceDocument(
                        SERVICE_ID.id.toString(),
                        SERVICE_NAME.name,
                        ServiceStatus.DISABLED.toString(),
                        TIMESTAMP.toString()
                    )
                ),
                null,
                0,
                creationDate,
                creationDate
            )
        return wallet
    }

    fun walletDomain(): it.pagopa.wallet.domain.wallets.Wallet {
        val wallet = WALLET_DOMAIN
        return wallet
    }

    fun walletDocumentNoVersion(): Wallet {
        val wallet =
            Wallet(
                WALLET_UUID.value.toString(),
                USER_ID.id.toString(),
                WalletStatusDto.CREATED.name,
                PAYMENT_METHOD_ID_CARDS.value.toString(),
                PAYMENT_INSTRUMENT_ID.value.toString(),
                CONTRACT_ID.contractId,
                OperationResultEnum.EXECUTED.value,
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
                ),
                0,
                creationDate,
                creationDate
            )
        return wallet
    }

    fun walletDocument(): Wallet {
        val wallet =
            Wallet(
                WALLET_UUID.value.toString(),
                USER_ID.id.toString(),
                WalletStatusDto.CREATED.name,
                PAYMENT_METHOD_ID_CARDS.value.toString(),
                PAYMENT_INSTRUMENT_ID.value.toString(),
                CONTRACT_ID.contractId,
                OperationResultEnum.EXECUTED.value,
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
                ),
                0,
                creationDate,
                creationDate
            )
        return wallet
    }

    val WALLET_DOMAIN =
        it.pagopa.wallet.domain.wallets.Wallet(
            WALLET_UUID,
            USER_ID,
            WalletStatusDto.CREATED,
            PAYMENT_METHOD_ID_CARDS,
            PAYMENT_INSTRUMENT_ID,
            listOf(Application(SERVICE_ID, SERVICE_NAME, ServiceStatus.DISABLED, TIMESTAMP)),
            CONTRACT_ID,
            OperationResultEnum.EXECUTED,
            CardDetails(BIN, MASKED_PAN, EXP_DATE, BRAND, HOLDER_NAME),
            0,
            creationDate,
            creationDate
        )

    private fun newWalletDocumentToBeSaved(): it.pagopa.wallet.documents.wallets.Wallet {

        return Wallet(
            WALLET_UUID.value.toString(),
            USER_ID.id.toString(),
            WalletStatusDto.CREATED.name,
            PAYMENT_METHOD_ID_CARDS.value.toString(),
            null,
            null,
            null,
            listOf(),
            null,
            0,
            creationDate,
            creationDate
        )
    }

    fun newWalletDocumentSaved(): it.pagopa.wallet.documents.wallets.Wallet {
        val wallet = newWalletDocumentToBeSaved()
        return wallet
    }

    fun newWalletDomainSaved(): it.pagopa.wallet.domain.wallets.Wallet {

        val wallet =
            Wallet(
                WALLET_UUID,
                USER_ID,
                WalletStatusDto.CREATED,
                PAYMENT_METHOD_ID_CARDS,
                null,
                listOf(),
                null,
                null,
                null,
                0,
                creationDate,
                creationDate
            )

        return wallet
    }

    fun initializedWalletDomainEmptyServicesNullDetailsNoPaymentInstrument():
        it.pagopa.wallet.domain.wallets.Wallet {
        val wallet =
            Wallet(
                WALLET_UUID,
                USER_ID,
                WalletStatusDto.CREATED,
                PAYMENT_METHOD_ID_CARDS,
                null,
                listOf(),
                null,
                null,
                null,
                0,
                creationDate,
                creationDate
            )
        return wallet
    }

    fun walletDomainEmptyServicesNullDetailsNoPaymentInstrument():
        it.pagopa.wallet.domain.wallets.Wallet {
        val wallet =
            Wallet(
                WALLET_UUID,
                USER_ID,
                WalletStatusDto.CREATED,
                PAYMENT_METHOD_ID_CARDS,
                null,
                listOf(),
                CONTRACT_ID,
                null,
                null,
                0,
                creationDate,
                creationDate
            )
        return wallet
    }

    fun walletInfoDto() =
        WalletInfoDto()
            .walletId(WALLET_UUID.value)
            .status(WalletStatusDto.CREATED)
            .creationDate(OffsetDateTime.ofInstant(TIMESTAMP, ZoneId.systemDefault()))
            .updateDate(OffsetDateTime.ofInstant(TIMESTAMP, ZoneId.systemDefault()))
            .paymentMethodId(PAYMENT_METHOD_ID_CARDS.value.toString())
            .userId(USER_ID.id.toString())
            .services(listOf())
            .details(
                WalletCardDetailsDto()
                    .maskedPan(MASKED_PAN.maskedPan)
                    .bin(BIN.bin)
                    .brand(WalletCardDetailsDto.BrandEnum.MASTERCARD)
                    .expiryDate(EXP_DATE.expDate)
                    .holder(HOLDER_NAME.holderName)
            )

    fun walletAuthDataDto() =
        WalletAuthDataDto()
            .walletId(WALLET_UUID.value)
            .contractId(CONTRACT_ID.contractId)
            .bin(BIN.bin)
            .brand(BRAND.value)

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
        WalletCreateRequestDto()
            .services(listOf(ServiceNameDto.PAGOPA))
            .useDiagnosticTracing(false)
            .paymentMethodId(PAYMENT_METHOD_ID_CARDS.value)

    val WALLET_SERVICE_1: WalletServiceDto =
        WalletServiceDto().name(ServiceNameDto.PAGOPA).status(WalletServiceStatusDto.DISABLED)

    val WALLET_SERVICE_2: WalletServiceDto =
        WalletServiceDto().name(ServiceNameDto.PAGOPA).status(WalletServiceStatusDto.ENABLED)

    val UPDATE_SERVICES_BODY: WalletServiceUpdateRequestDto =
        WalletServiceUpdateRequestDto().services(listOf(WALLET_SERVICE_1, WALLET_SERVICE_2))

    val PSP_ID = UUID.randomUUID().toString()

    val APM_SESSION_CREATE_REQUEST =
        SessionInputPayPalDataDto().apply {
            pspId = PSP_ID
            paymentMethodType = "paypal"
        }

    fun getValidCardsPaymentMethod(): PaymentMethodResponse {
        return PaymentMethodResponse()
            .id(PAYMENT_METHOD_ID_CARDS.value.toString())
            .paymentTypeCode("CP")
            .status(PaymentMethodStatus.ENABLED)
            .name("CARDS")
    }

    fun getValidAPMPaymentMethod(): PaymentMethodResponse {
        return PaymentMethodResponse()
            .id(PAYMENT_METHOD_ID_APM.value.toString())
            .paymentTypeCode("PPAL")
            .status(PaymentMethodStatus.ENABLED)
            .name("PAYPAL")
    }

    fun getDisabledCardsPaymentMethod(): PaymentMethodResponse {
        return PaymentMethodResponse()
            .id(PAYMENT_METHOD_ID_CARDS.value.toString())
            .paymentTypeCode("CP")
            .status(PaymentMethodStatus.DISABLED)
            .name("CARDS")
    }

    fun getInvalidCardsPaymentMethod(): PaymentMethodResponse {
        return PaymentMethodResponse()
            .id(PAYMENT_METHOD_ID_CARDS.value.toString())
            .paymentTypeCode("CP")
            .status(PaymentMethodStatus.ENABLED)
            .name("INVALID")
    }

    fun getUniqueId(): String {
        return "W49357937935R869i"
    }

    val NOTIFY_WALLET_REQUEST_OK_OPERATION_RESULT: WalletNotificationRequestDto =
        WalletNotificationRequestDto()
            .operationResult(OperationResultEnum.EXECUTED)
            .timestampOperation(OffsetDateTime.now())
            .operationId("validationOperationId")

    val NOTIFY_WALLET_REQUEST_KO_OPERATION_RESULT: WalletNotificationRequestDto =
        WalletNotificationRequestDto()
            .operationResult(OperationResultEnum.DECLINED)
            .timestampOperation(OffsetDateTime.now())
            .operationId("validationOperationId")
}
