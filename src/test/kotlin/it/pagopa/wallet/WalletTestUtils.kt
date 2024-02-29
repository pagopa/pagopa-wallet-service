package it.pagopa.wallet

import it.pagopa.generated.ecommerce.model.PaymentMethodResponse
import it.pagopa.generated.ecommerce.model.PaymentMethodStatus
import it.pagopa.generated.wallet.model.*
import it.pagopa.generated.wallet.model.WalletNotificationRequestDto.OperationResultEnum
import it.pagopa.wallet.documents.service.Service
import it.pagopa.wallet.documents.wallets.Application as WalletServiceDocument
import it.pagopa.wallet.documents.wallets.Wallet
import it.pagopa.wallet.documents.wallets.details.CardDetails
import it.pagopa.wallet.documents.wallets.details.PayPalDetails as PayPalDetailsDocument
import it.pagopa.wallet.documents.wallets.details.WalletDetails
import it.pagopa.wallet.domain.services.ApplicationMetadata
import it.pagopa.wallet.domain.services.ServiceId
import it.pagopa.wallet.domain.services.ServiceName
import it.pagopa.wallet.domain.services.ServiceStatus
import it.pagopa.wallet.domain.wallets.*
import it.pagopa.wallet.domain.wallets.details.*
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

    private val APPLICATION_METADATA_HASHMAP: HashMap<String, String> = hashMapOf()
    val APPLICATION_METADATA = ApplicationMetadata(APPLICATION_METADATA_HASHMAP)

    val CONTRACT_ID = ContractId("TestContractId")

    val BIN = Bin("42424242")
    val LAST_FOUR_DIGITS = LastFourDigits("5555")
    val EXP_DATE = ExpiryDate("203012")
    val BRAND = WalletCardDetailsDto.BrandEnum.MASTERCARD
    const val ORDER_ID = "WFHDJFIRUT48394832"
    private val TYPE = WalletDetailsType.CARDS
    val TIMESTAMP: Instant = Instant.now()

    val MASKED_EMAIL = MaskedEmail("maskedEmail")

    val creationDate: Instant = Instant.now()

    fun walletDocumentWithSessionWallet(): Wallet {
        val wallet =
            Wallet(
                id = WALLET_UUID.value.toString(),
                userId = USER_ID.id.toString(),
                status = WalletStatusDto.INITIALIZED.name,
                paymentMethodId = PAYMENT_METHOD_ID_CARDS.value.toString(),
                paymentInstrumentId = null,
                contractId = CONTRACT_ID.contractId,
                validationOperationResult = null,
                validationErrorCode = null,
                applications = listOf(),
                details = null,
                version = 0,
                creationDate = creationDate,
                updateDate = creationDate
            )
        return wallet
    }

    fun walletDocumentWithSessionWallet(contractId: ContractId): Wallet {
        val wallet =
            Wallet(
                id = WALLET_UUID.value.toString(),
                userId = USER_ID.id.toString(),
                status = WalletStatusDto.INITIALIZED.name,
                paymentMethodId = PAYMENT_METHOD_ID_CARDS.value.toString(),
                paymentInstrumentId = null,
                contractId = contractId.contractId,
                validationOperationResult = null,
                validationErrorCode = null,
                applications = listOf(),
                details = null,
                version = 0,
                creationDate = creationDate,
                updateDate = creationDate
            )
        return wallet
    }

    fun walletDocumentVerifiedWithCardDetails(
        bin: String,
        lastFourDigits: String,
        expiryDate: String,
        brandEnum: WalletCardDetailsDto.BrandEnum
    ): Wallet {
        val wallet =
            Wallet(
                id = WALLET_UUID.value.toString(),
                userId = USER_ID.id.toString(),
                status = WalletStatusDto.VALIDATION_REQUESTED.name,
                paymentMethodId = PAYMENT_METHOD_ID_CARDS.value.toString(),
                paymentInstrumentId = null,
                contractId = CONTRACT_ID.contractId,
                validationOperationResult = null,
                validationErrorCode = null,
                applications = listOf(),
                details =
                    CardDetails(
                        WalletDetailsType.CARDS.name,
                        bin,
                        lastFourDigits,
                        expiryDate,
                        brandEnum.name
                    ),
                version = 0,
                creationDate = creationDate,
                updateDate = creationDate
            )
        return wallet
    }

    fun walletDocumentVerifiedWithAPM(details: WalletDetails<*>): Wallet {
        val wallet =
            Wallet(
                id = WALLET_UUID.value.toString(),
                userId = USER_ID.id.toString(),
                status = WalletStatusDto.VALIDATION_REQUESTED.name,
                paymentMethodId = PAYMENT_METHOD_ID_CARDS.value.toString(),
                paymentInstrumentId = null,
                contractId = CONTRACT_ID.contractId,
                validationOperationResult = null,
                validationErrorCode = null,
                applications = listOf(),
                details = details,
                version = 0,
                creationDate = creationDate,
                updateDate = creationDate
            )
        return wallet
    }

    fun walletDocumentWithError(
        operationResultEnum: OperationResultEnum,
        errorCode: String? = null,
        details: WalletDetails<*>? = null
    ): Wallet {
        return Wallet(
            id = WALLET_UUID.value.toString(),
            userId = USER_ID.id.toString(),
            status = WalletStatusDto.ERROR.name,
            paymentMethodId = PAYMENT_METHOD_ID_CARDS.value.toString(),
            paymentInstrumentId = null,
            contractId = CONTRACT_ID.contractId,
            validationOperationResult = operationResultEnum.value,
            validationErrorCode = errorCode,
            applications = listOf(),
            details = details,
            version = 0,
            creationDate = creationDate,
            updateDate = creationDate
        )
    }

    fun walletDocumentValidated(): Wallet {
        val wallet =
            Wallet(
                id = WALLET_UUID.value.toString(),
                userId = USER_ID.id.toString(),
                status = WalletStatusDto.VALIDATED.name,
                paymentMethodId = PAYMENT_METHOD_ID_CARDS.value.toString(),
                paymentInstrumentId = null,
                contractId = CONTRACT_ID.contractId,
                validationOperationResult = OperationResultEnum.EXECUTED.toString(),
                validationErrorCode = null,
                applications = listOf(),
                details = null,
                version = 0,
                creationDate = creationDate,
                updateDate = creationDate
            )
        return wallet
    }

    fun walletDocumentValidated(
        operationResultEnum: OperationResultEnum,
        details: WalletDetails<*>
    ): Wallet {
        val wallet =
            Wallet(
                id = WALLET_UUID.value.toString(),
                userId = USER_ID.id.toString(),
                status = WalletStatusDto.VALIDATED.name,
                paymentMethodId = PAYMENT_METHOD_ID_CARDS.value.toString(),
                paymentInstrumentId = null,
                contractId = CONTRACT_ID.contractId,
                validationOperationResult = operationResultEnum.value,
                validationErrorCode = null,
                applications = listOf(),
                details = details,
                version = 0,
                creationDate = creationDate,
                updateDate = creationDate
            )
        return wallet
    }

    fun walletDocumentEmptyServicesNullDetailsNoPaymentInstrument(): Wallet {
        val wallet =
            Wallet(
                id = WALLET_UUID.value.toString(),
                userId = USER_ID.id.toString(),
                status = WalletStatusDto.CREATED.name,
                paymentMethodId = PAYMENT_METHOD_ID_CARDS.value.toString(),
                paymentInstrumentId = null,
                contractId = CONTRACT_ID.contractId,
                validationOperationResult = null,
                validationErrorCode = null,
                applications = listOf(),
                details = null,
                version = 0,
                creationDate = creationDate,
                updateDate = creationDate
            )
        return wallet
    }

    fun walletDocumentEmptyServicesNullDetails(): Wallet {
        val wallet =
            Wallet(
                id = WALLET_UUID.value.toString(),
                userId = USER_ID.id.toString(),
                status = WalletStatusDto.CREATED.name,
                paymentMethodId = PAYMENT_METHOD_ID_CARDS.value.toString(),
                paymentInstrumentId = PAYMENT_INSTRUMENT_ID.value.toString(),
                contractId = CONTRACT_ID.contractId,
                validationOperationResult = null,
                validationErrorCode = null,
                applications = listOf(),
                details = null,
                version = 0,
                creationDate = creationDate,
                updateDate = creationDate
            )
        return wallet
    }

    fun walletDocumentEmptyContractId(): Wallet {
        val wallet =
            Wallet(
                id = WALLET_UUID.value.toString(),
                userId = USER_ID.id.toString(),
                status = WalletStatusDto.CREATED.name,
                paymentMethodId = PAYMENT_METHOD_ID_CARDS.value.toString(),
                paymentInstrumentId = PAYMENT_INSTRUMENT_ID.value.toString(),
                contractId = null,
                validationOperationResult = null,
                validationErrorCode = null,
                applications = listOf(),
                details = null,
                version = 0,
                creationDate = creationDate,
                updateDate = creationDate
            )
        return wallet
    }

    fun walletDocumentWithEmptyValidationOperationResult(): Wallet {
        val wallet =
            Wallet(
                id = WALLET_UUID.value.toString(),
                userId = USER_ID.id.toString(),
                status = WalletStatusDto.CREATED.name,
                paymentMethodId = PAYMENT_METHOD_ID_CARDS.value.toString(),
                paymentInstrumentId = PAYMENT_INSTRUMENT_ID.value.toString(),
                contractId = null,
                validationOperationResult = OperationResultEnum.EXECUTED.value,
                validationErrorCode = null,
                applications = listOf(),
                details = null,
                version = 0,
                creationDate = creationDate,
                updateDate = creationDate
            )
        return wallet
    }

    fun walletDocumentNullDetails(): Wallet {
        val wallet =
            Wallet(
                id = WALLET_UUID.value.toString(),
                userId = USER_ID.id.toString(),
                status = WalletStatusDto.CREATED.name,
                paymentMethodId = PAYMENT_METHOD_ID_CARDS.value.toString(),
                paymentInstrumentId = PAYMENT_INSTRUMENT_ID.value.toString(),
                contractId = CONTRACT_ID.contractId,
                validationOperationResult = null,
                validationErrorCode = null,
                applications =
                    listOf(
                        WalletServiceDocument(
                            SERVICE_ID.id.toString(),
                            SERVICE_NAME.name,
                            ServiceStatus.DISABLED.toString(),
                            TIMESTAMP.toString(),
                            APPLICATION_METADATA.data
                        )
                    ),
                details = null,
                version = 0,
                creationDate = creationDate,
                updateDate = creationDate
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
                id = WALLET_UUID.value.toString(),
                userId = USER_ID.id.toString(),
                status = WalletStatusDto.CREATED.name,
                paymentMethodId = PAYMENT_METHOD_ID_CARDS.value.toString(),
                paymentInstrumentId = PAYMENT_INSTRUMENT_ID.value.toString(),
                contractId = CONTRACT_ID.contractId,
                validationOperationResult = OperationResultEnum.EXECUTED.value,
                validationErrorCode = null,
                applications =
                    listOf(
                        WalletServiceDocument(
                            SERVICE_ID.id.toString(),
                            SERVICE_NAME.name,
                            ServiceStatus.DISABLED.toString(),
                            TIMESTAMP.toString(),
                            APPLICATION_METADATA_HASHMAP
                        )
                    ),
                details =
                    CardDetails(
                        TYPE.toString(),
                        BIN.bin,
                        LAST_FOUR_DIGITS.lastFourDigits,
                        EXP_DATE.expDate,
                        BRAND.toString()
                    ),
                version = 0,
                creationDate = creationDate,
                updateDate = creationDate
            )
        return wallet
    }

    fun walletDocument(): Wallet {
        val wallet =
            Wallet(
                id = WALLET_UUID.value.toString(),
                userId = USER_ID.id.toString(),
                status = WalletStatusDto.CREATED.name,
                paymentMethodId = PAYMENT_METHOD_ID_CARDS.value.toString(),
                paymentInstrumentId = PAYMENT_INSTRUMENT_ID.value.toString(),
                contractId = CONTRACT_ID.contractId,
                validationOperationResult = OperationResultEnum.EXECUTED.value,
                validationErrorCode = null,
                applications =
                    listOf(
                        WalletServiceDocument(
                            SERVICE_ID.id.toString(),
                            SERVICE_NAME.name,
                            ServiceStatus.DISABLED.toString(),
                            TIMESTAMP.toString(),
                            APPLICATION_METADATA_HASHMAP
                        )
                    ),
                details =
                    CardDetails(
                        TYPE.toString(),
                        BIN.bin,
                        LAST_FOUR_DIGITS.lastFourDigits,
                        EXP_DATE.expDate,
                        BRAND.toString()
                    ),
                version = 0,
                creationDate = creationDate,
                updateDate = creationDate
            )
        return wallet
    }

    fun walletDocumentAPM(): Wallet {
        val wallet =
            Wallet(
                id = WALLET_UUID.value.toString(),
                userId = USER_ID.id.toString(),
                status = WalletStatusDto.CREATED.name,
                paymentMethodId = PAYMENT_METHOD_ID_CARDS.value.toString(),
                paymentInstrumentId = PAYMENT_INSTRUMENT_ID.value.toString(),
                contractId = CONTRACT_ID.contractId,
                validationOperationResult = OperationResultEnum.EXECUTED.value,
                validationErrorCode = null,
                applications =
                    listOf(
                        WalletServiceDocument(
                            SERVICE_ID.id.toString(),
                            SERVICE_NAME.name,
                            ServiceStatus.DISABLED.toString(),
                            TIMESTAMP.toString(),
                            APPLICATION_METADATA_HASHMAP
                        )
                    ),
                details = PayPalDetailsDocument(maskedEmail = MASKED_EMAIL.value, pspId = PSP_ID),
                version = 0,
                creationDate = creationDate,
                updateDate = creationDate
            )
        return wallet
    }

    val WALLET_DOMAIN =
        Wallet(
            id = WALLET_UUID,
            userId = USER_ID,
            status = WalletStatusDto.CREATED,
            paymentMethodId = PAYMENT_METHOD_ID_CARDS,
            paymentInstrumentId = PAYMENT_INSTRUMENT_ID,
            applications =
                listOf(
                    Application(
                        SERVICE_ID,
                        SERVICE_NAME,
                        ServiceStatus.DISABLED,
                        TIMESTAMP,
                        APPLICATION_METADATA
                    )
                ),
            contractId = CONTRACT_ID,
            validationOperationResult = OperationResultEnum.EXECUTED,
            validationErrorCode = null,
            details = CardDetails(BIN, LAST_FOUR_DIGITS, EXP_DATE, BRAND),
            version = 0,
            creationDate = creationDate,
            updateDate = creationDate
        )

    private fun newWalletDocumentToBeSaved(): Wallet {

        return Wallet(
            id = WALLET_UUID.value.toString(),
            userId = USER_ID.id.toString(),
            status = WalletStatusDto.CREATED.name,
            paymentMethodId = PAYMENT_METHOD_ID_CARDS.value.toString(),
            paymentInstrumentId = null,
            contractId = null,
            validationOperationResult = null,
            validationErrorCode = null,
            applications = listOf(),
            details = null,
            version = 0,
            creationDate = creationDate,
            updateDate = creationDate
        )
    }

    fun newWalletDocumentSaved(): Wallet {
        val wallet = newWalletDocumentToBeSaved()
        return wallet
    }

    fun newWalletDomainSaved(): it.pagopa.wallet.domain.wallets.Wallet {

        val wallet =
            Wallet(
                id = WALLET_UUID,
                userId = USER_ID,
                status = WalletStatusDto.CREATED,
                paymentMethodId = PAYMENT_METHOD_ID_CARDS,
                paymentInstrumentId = null,
                applications = listOf(),
                validationOperationResult = null,
                validationErrorCode = null,
                contractId = null,
                version = 0,
                creationDate = creationDate,
                updateDate = creationDate
            )

        return wallet
    }

    fun initializedWalletDomainEmptyServicesNullDetailsNoPaymentInstrument():
        it.pagopa.wallet.domain.wallets.Wallet {
        val wallet =
            Wallet(
                id = WALLET_UUID,
                userId = USER_ID,
                status = WalletStatusDto.CREATED,
                paymentMethodId = PAYMENT_METHOD_ID_CARDS,
                paymentInstrumentId = null,
                applications = listOf(),
                contractId = null,
                validationOperationResult = null,
                validationErrorCode = null,
                details = null,
                version = 0,
                creationDate = creationDate,
                updateDate = creationDate
            )
        return wallet
    }

    fun walletDomainEmptyServicesNullDetailsNoPaymentInstrument():
        it.pagopa.wallet.domain.wallets.Wallet {
        val wallet =
            Wallet(
                id = WALLET_UUID,
                userId = USER_ID,
                status = WalletStatusDto.CREATED,
                paymentMethodId = PAYMENT_METHOD_ID_CARDS,
                paymentInstrumentId = null,
                applications = listOf(),
                contractId = CONTRACT_ID,
                validationOperationResult = null,
                validationErrorCode = null,
                details = null,
                version = 0,
                creationDate = creationDate,
                updateDate = creationDate
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
                    .lastFourDigits(LAST_FOUR_DIGITS.lastFourDigits)
                    .bin(BIN.bin)
                    .brand(WalletCardDetailsDto.BrandEnum.MASTERCARD)
                    .expiryDate(EXP_DATE.expDate)
            )

    fun walletInfoDtoAPM() =
        WalletInfoDto()
            .walletId(WALLET_UUID.value)
            .status(WalletStatusDto.CREATED)
            .creationDate(OffsetDateTime.ofInstant(TIMESTAMP, ZoneId.systemDefault()))
            .updateDate(OffsetDateTime.ofInstant(TIMESTAMP, ZoneId.systemDefault()))
            .paymentMethodId(PAYMENT_METHOD_ID_APM.value.toString())
            .userId(USER_ID.id.toString())
            .services(listOf())
            .details(
                WalletPaypalDetailsDto().type("PAYPAL").maskedEmail("maskedEmail").pspId(PSP_ID)
            )

    fun walletCardAuthDataDto() =
        WalletAuthDataDto()
            .walletId(WALLET_UUID.value)
            .contractId(CONTRACT_ID.contractId)
            .brand(BRAND.value)
            .paymentMethodData(WalletAuthCardDataDto().bin(BIN.bin).paymentMethodType("cards"))

    fun walletAPMAuthDataDto() =
        WalletAuthDataDto()
            .walletId(WALLET_UUID.value)
            .contractId(CONTRACT_ID.contractId)
            .brand("PAYPAL")
            .paymentMethodData(WalletAuthAPMDataDto().paymentMethodType("apm"))

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

    val CREATE_WALLET_TRANSACTION_REQUEST: WalletTransactionCreateRequestDto =
        WalletTransactionCreateRequestDto()
            .useDiagnosticTracing(false)
            .paymentMethodId(PAYMENT_METHOD_ID_CARDS.value)
            .amount(200)

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

    val NOTIFY_WALLET_REQUEST_OK_OPERATION_RESULT_WITH_PAYPAL_DETAILS:
        WalletNotificationRequestDto =
        WalletNotificationRequestDto()
            .operationResult(OperationResultEnum.EXECUTED)
            .timestampOperation(OffsetDateTime.now())
            .operationId("validationOperationId")
            .details(
                WalletNotificationRequestPaypalDetailsDto()
                    .type("PAYPAL")
                    .maskedEmail(MASKED_EMAIL.value)
            )

    val NOTIFY_WALLET_REQUEST_KO_OPERATION_RESULT: WalletNotificationRequestDto =
        WalletNotificationRequestDto()
            .operationResult(OperationResultEnum.DECLINED)
            .timestampOperation(OffsetDateTime.now())
            .operationId("validationOperationId")
}
