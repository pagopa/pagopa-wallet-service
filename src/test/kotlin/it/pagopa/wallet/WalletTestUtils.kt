package it.pagopa.wallet

import it.pagopa.generated.ecommerce.model.PaymentMethodResponse
import it.pagopa.generated.ecommerce.model.PaymentMethodStatus
import it.pagopa.generated.wallet.model.*
import it.pagopa.generated.wallet.model.WalletNotificationRequestDto.OperationResultEnum
import it.pagopa.wallet.documents.applications.Application
import it.pagopa.wallet.documents.wallets.Wallet
import it.pagopa.wallet.documents.wallets.WalletApplication as WalletApplicationDocument
import it.pagopa.wallet.documents.wallets.details.CardDetails as CardDetailsDocument
import it.pagopa.wallet.documents.wallets.details.PayPalDetails as PayPalDetailsDocument
import it.pagopa.wallet.documents.wallets.details.WalletDetails
import it.pagopa.wallet.domain.applications.ApplicationDescription
import it.pagopa.wallet.domain.applications.ApplicationId
import it.pagopa.wallet.domain.applications.ApplicationStatus
import it.pagopa.wallet.domain.wallets.*
import it.pagopa.wallet.domain.wallets.WalletApplication
import it.pagopa.wallet.domain.wallets.details.*
import it.pagopa.wallet.util.TransactionId
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneId
import java.util.*
import org.springframework.http.HttpStatus

object WalletTestUtils {

    val USER_ID = UserId(UUID.randomUUID())
    val WALLET_UUID = WalletId(UUID.randomUUID())
    val APPLICATION_ID = ApplicationId("PAGOPA")
    val APPLICATION_DESCRIPTION = ApplicationDescription("")
    val WALLET_APPLICATION_ID = WalletApplicationId("PAGOPA")
    val PAYMENT_METHOD_ID_CARDS = PaymentMethodId(UUID.randomUUID())
    val PAYMENT_METHOD_ID_APM = PaymentMethodId(UUID.randomUUID())
    val APPLICATION_METADATA_HASHMAP: HashMap<String, String> = hashMapOf()
    val APPLICATION_METADATA = WalletApplicationMetadata(APPLICATION_METADATA_HASHMAP)
    val CONTRACT_ID = ContractId("W49357937935R869i")
    val BIN = Bin("42424242")
    val LAST_FOUR_DIGITS = LastFourDigits("5555")
    val EXP_DATE = ExpiryDate("203012")
    val BRAND = WalletCardDetailsDto.BrandEnum.MASTERCARD
    val PAYMENT_INSTRUMENT_GATEWAY_ID = PaymentInstrumentGatewayId("paymentInstrumentGatewayId")
    const val ORDER_ID = "WFHDJFIRUT48394832"
    private val TYPE = WalletDetailsType.CARDS
    val TIMESTAMP: Instant = Instant.now()
    val MASKED_EMAIL = MaskedEmail("maskedEmail")
    val creationDate: Instant = Instant.now()
    const val TRANSACTION_ID = "0cbd232af3464a6985921cf437510e03"
    const val AMOUNT = 100

    fun newWalletDocumentToBeSaved(paymentMethodId: PaymentMethodId): Wallet {

        return Wallet(
            id = WALLET_UUID.value.toString(),
            userId = USER_ID.id.toString(),
            status = WalletStatusDto.CREATED.name,
            paymentMethodId = paymentMethodId.value.toString(),
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

    fun newWalletDocumentToBeSaved(
        paymentMethodId: PaymentMethodId,
        applications: List<WalletApplicationDocument>
    ): Wallet {

        return Wallet(
            id = WALLET_UUID.value.toString(),
            userId = USER_ID.id.toString(),
            status = WalletStatusDto.CREATED.name,
            paymentMethodId = paymentMethodId.value.toString(),
            contractId = null,
            validationOperationResult = null,
            validationErrorCode = null,
            applications,
            details = null,
            version = 0,
            creationDate = creationDate,
            updateDate = creationDate
        )
    }

    fun newWalletDocumentForPaymentWithContextualOnboardToBeSaved(
        paymentMethodId: PaymentMethodId,
        application: Application
    ): Wallet {
        return newWalletDocumentToBeSaved(paymentMethodId)
            .copy(
                applications =
                    listOf(
                        WalletApplicationDocument(
                            application.id,
                            status = parseWalletApplicationStatus(application.status),
                            creationDate.toString(),
                            creationDate.toString(),
                            hashMapOf(
                                Pair(
                                    WalletApplicationMetadata.Metadata
                                        .PAYMENT_WITH_CONTEXTUAL_ONBOARD
                                        .value,
                                    true.toString()
                                ),
                                Pair(
                                    WalletApplicationMetadata.Metadata.TRANSACTION_ID.value,
                                    TransactionId(TRANSACTION_ID).value().toString()
                                ),
                                Pair(
                                    WalletApplicationMetadata.Metadata.AMOUNT.value,
                                    AMOUNT.toString()
                                )
                            )
                        )
                    )
            )
    }

    private fun parseWalletApplicationStatus(status: String): String =
        when (status) {
            ApplicationStatus.ENABLED.name -> WalletApplicationStatus.ENABLED.name
            else -> WalletApplicationStatus.DISABLED.name
        }

    fun walletDocumentCreatedStatus(paymentMethodId: PaymentMethodId): Wallet {
        return Wallet(
            id = WALLET_UUID.value.toString(),
            userId = USER_ID.id.toString(),
            status = WalletStatusDto.CREATED.name,
            paymentMethodId = paymentMethodId.value.toString(),
            contractId = CONTRACT_ID.contractId,
            validationOperationResult = null,
            validationErrorCode = null,
            applications = listOf(),
            details = null,
            version = 0,
            creationDate = creationDate,
            updateDate = creationDate
        )
    }

    fun walletDocumentInitializedStatus(paymentMethodId: PaymentMethodId): Wallet {
        return Wallet(
            id = WALLET_UUID.value.toString(),
            userId = USER_ID.id.toString(),
            status = WalletStatusDto.INITIALIZED.name,
            paymentMethodId = paymentMethodId.value.toString(),
            contractId = CONTRACT_ID.contractId,
            validationOperationResult = null,
            validationErrorCode = null,
            applications = listOf(),
            details = null,
            version = 0,
            creationDate = creationDate,
            updateDate = creationDate
        )
    }

    fun walletDocumentValidationRequestedStatus(paymentMethodId: PaymentMethodId): Wallet {
        return walletDocumentCreatedStatus(paymentMethodId)
            .copy(
                status = WalletStatusDto.VALIDATION_REQUESTED.name,
                applications = listOf(),
                details =
                    if (paymentMethodId == PAYMENT_METHOD_ID_CARDS) {
                        CardDetailsDocument(
                            TYPE.toString(),
                            BIN.bin,
                            LAST_FOUR_DIGITS.lastFourDigits,
                            EXP_DATE.expDate,
                            BRAND.toString(),
                            PAYMENT_INSTRUMENT_GATEWAY_ID.paymentInstrumentGatewayId
                        )
                    } else {
                        PayPalDetailsDocument(maskedEmail = null, pspId = PSP_ID)
                    },
            )
    }

    fun walletDocumentCreatedStatusForTransactionWithContextualOnboard(
        paymentMethodId: PaymentMethodId
    ): Wallet {
        return walletDocumentCreatedStatus(paymentMethodId)
            .copy(
                applications =
                    listOf(
                        WalletApplicationDocument(
                            WALLET_APPLICATION_ID.id,
                            WalletApplicationStatus.ENABLED.toString(),
                            creationDate.toString(),
                            creationDate.toString(),
                            hashMapOf(
                                Pair(
                                    WalletApplicationMetadata.Metadata
                                        .PAYMENT_WITH_CONTEXTUAL_ONBOARD
                                        .value,
                                    true.toString()
                                ),
                                Pair(
                                    WalletApplicationMetadata.Metadata.TRANSACTION_ID.value,
                                    TransactionId(TRANSACTION_ID).value().toString()
                                ),
                                Pair(
                                    WalletApplicationMetadata.Metadata.AMOUNT.value,
                                    AMOUNT.toString()
                                )
                            )
                        )
                    )
            )
    }

    fun walletDocumentInitializedStatusForTransactionWithContextualOnboard(
        paymentMethodId: PaymentMethodId
    ): Wallet {
        return walletDocumentInitializedStatus(paymentMethodId)
            .copy(
                applications =
                    listOf(
                        WalletApplicationDocument(
                            WALLET_APPLICATION_ID.id,
                            WalletApplicationStatus.ENABLED.toString(),
                            creationDate.toString(),
                            creationDate.toString(),
                            hashMapOf(
                                Pair(
                                    WalletApplicationMetadata.Metadata
                                        .PAYMENT_WITH_CONTEXTUAL_ONBOARD
                                        .value,
                                    true.toString()
                                ),
                                Pair(
                                    WalletApplicationMetadata.Metadata.TRANSACTION_ID.value,
                                    TransactionId(TRANSACTION_ID).value().toString()
                                ),
                                Pair(
                                    WalletApplicationMetadata.Metadata.AMOUNT.value,
                                    AMOUNT.toString()
                                )
                            )
                        )
                    )
            )
    }

    fun walletDocumentStatusValidatedCard() = walletDocumentStatusValidatedCard(BRAND)

    fun walletDocumentStatusValidatedCard(brand: WalletCardDetailsDto.BrandEnum): Wallet {
        return Wallet(
            id = WALLET_UUID.value.toString(),
            userId = USER_ID.id.toString(),
            status = WalletStatusDto.VALIDATED.name,
            paymentMethodId = PAYMENT_METHOD_ID_CARDS.value.toString(),
            contractId = CONTRACT_ID.contractId,
            validationOperationResult = OperationResultEnum.EXECUTED.value,
            validationErrorCode = null,
            applications =
                listOf(
                    WalletApplicationDocument(
                        WALLET_APPLICATION_ID.id,
                        WalletApplicationStatus.DISABLED.toString(),
                        TIMESTAMP.toString(),
                        TIMESTAMP.toString(),
                        APPLICATION_METADATA_HASHMAP
                    )
                ),
            details =
                CardDetailsDocument(
                    TYPE.toString(),
                    BIN.bin,
                    LAST_FOUR_DIGITS.lastFourDigits,
                    EXP_DATE.expDate,
                    brand.toString(),
                    PAYMENT_INSTRUMENT_GATEWAY_ID.paymentInstrumentGatewayId
                ),
            version = 0,
            creationDate = creationDate,
            updateDate = creationDate
        )
    }

    fun walletDocumentStatusValidatedAPM(paypalEmail: String?): Wallet {
        return Wallet(
            id = WALLET_UUID.value.toString(),
            userId = USER_ID.id.toString(),
            status = WalletStatusDto.VALIDATED.name,
            paymentMethodId = PAYMENT_METHOD_ID_CARDS.value.toString(),
            contractId = CONTRACT_ID.contractId,
            validationOperationResult = OperationResultEnum.EXECUTED.value,
            validationErrorCode = null,
            applications =
                listOf(
                    WalletApplicationDocument(
                        WALLET_APPLICATION_ID.id,
                        WalletApplicationStatus.DISABLED.toString(),
                        TIMESTAMP.toString(),
                        TIMESTAMP.toString(),
                        APPLICATION_METADATA_HASHMAP
                    )
                ),
            details = PayPalDetailsDocument(maskedEmail = paypalEmail, pspId = PSP_ID),
            version = 0,
            creationDate = creationDate,
            updateDate = creationDate
        )
    }

    fun walletDocumentVerifiedWithCardDetails(
        bin: String,
        lastFourDigits: String,
        expiryDate: String,
        paymentInstrumentGatewayId: String,
        brandEnum: WalletCardDetailsDto.BrandEnum
    ): Wallet {
        val wallet =
            Wallet(
                id = WALLET_UUID.value.toString(),
                userId = USER_ID.id.toString(),
                status = WalletStatusDto.VALIDATION_REQUESTED.name,
                paymentMethodId = PAYMENT_METHOD_ID_CARDS.value.toString(),
                contractId = CONTRACT_ID.contractId,
                validationOperationResult = null,
                validationErrorCode = null,
                applications = listOf(),
                details =
                    CardDetailsDocument(
                        WalletDetailsType.CARDS.name,
                        bin,
                        lastFourDigits,
                        expiryDate,
                        brandEnum.name,
                        paymentInstrumentGatewayId
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

    fun walletDocumentEmptyCreatedStatus(): Wallet {
        val wallet =
            Wallet(
                id = WALLET_UUID.value.toString(),
                userId = USER_ID.id.toString(),
                status = WalletStatusDto.CREATED.name,
                paymentMethodId = PAYMENT_METHOD_ID_CARDS.value.toString(),
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

    fun walletDocumentEmptyApplicationsNullDetails(): Wallet {
        val wallet =
            Wallet(
                id = WALLET_UUID.value.toString(),
                userId = USER_ID.id.toString(),
                status = WalletStatusDto.CREATED.name,
                paymentMethodId = PAYMENT_METHOD_ID_CARDS.value.toString(),
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
                contractId = CONTRACT_ID.contractId,
                validationOperationResult = null,
                validationErrorCode = null,
                applications =
                    listOf(
                        WalletApplicationDocument(
                            WALLET_APPLICATION_ID.id.toString(),
                            WalletApplicationStatus.DISABLED.toString(),
                            TIMESTAMP.toString(),
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

    fun walletDocument(): Wallet {
        val wallet =
            Wallet(
                id = WALLET_UUID.value.toString(),
                userId = USER_ID.id.toString(),
                status = WalletStatusDto.CREATED.name,
                paymentMethodId = PAYMENT_METHOD_ID_CARDS.value.toString(),
                contractId = CONTRACT_ID.contractId,
                validationOperationResult = OperationResultEnum.EXECUTED.value,
                validationErrorCode = null,
                applications =
                    listOf(
                        WalletApplicationDocument(
                            WALLET_APPLICATION_ID.id.toString(),
                            WalletApplicationStatus.DISABLED.toString(),
                            TIMESTAMP.toString(),
                            TIMESTAMP.toString(),
                            APPLICATION_METADATA_HASHMAP
                        )
                    ),
                details =
                    CardDetailsDocument(
                        TYPE.toString(),
                        BIN.bin,
                        LAST_FOUR_DIGITS.lastFourDigits,
                        EXP_DATE.expDate,
                        BRAND.toString(),
                        PAYMENT_INSTRUMENT_GATEWAY_ID.paymentInstrumentGatewayId
                    ),
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
            applications =
                listOf(
                    WalletApplication(
                        WALLET_APPLICATION_ID,
                        WalletApplicationStatus.DISABLED,
                        TIMESTAMP,
                        TIMESTAMP,
                        APPLICATION_METADATA
                    )
                ),
            contractId = CONTRACT_ID,
            validationOperationResult = OperationResultEnum.EXECUTED,
            validationErrorCode = null,
            details =
                CardDetails(BIN, LAST_FOUR_DIGITS, EXP_DATE, BRAND, PAYMENT_INSTRUMENT_GATEWAY_ID),
            version = 0,
            creationDate = creationDate,
            updateDate = creationDate
        )

    fun walletDomainEmptyServicesNullDetailsNoPaymentInstrument():
        it.pagopa.wallet.domain.wallets.Wallet {
        val wallet =
            Wallet(
                id = WALLET_UUID,
                userId = USER_ID,
                status = WalletStatusDto.CREATED,
                paymentMethodId = PAYMENT_METHOD_ID_CARDS,
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
            .applications(listOf())
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
            .applications(listOf())
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

    val APPLICATION_DOCUMENT: Application =
        Application(
            APPLICATION_ID.id,
            APPLICATION_DESCRIPTION.description,
            ApplicationStatus.DISABLED.name,
            TIMESTAMP.toString(),
            TIMESTAMP.toString()
        )

    fun buildProblemJson(
        httpStatus: HttpStatus,
        title: String,
        description: String
    ): ProblemJsonDto = ProblemJsonDto().status(httpStatus.value()).detail(description).title(title)

    val CREATE_WALLET_REQUEST: WalletCreateRequestDto =
        WalletCreateRequestDto()
            .applications(listOf("PAGOPA"))
            .useDiagnosticTracing(false)
            .paymentMethodId(PAYMENT_METHOD_ID_CARDS.value)

    val CREATE_WALLET_TRANSACTION_REQUEST: WalletTransactionCreateRequestDto =
        WalletTransactionCreateRequestDto()
            .useDiagnosticTracing(false)
            .paymentMethodId(PAYMENT_METHOD_ID_CARDS.value)
            .amount(200)

    val WALLET_SERVICE_1: WalletApplicationDto =
        WalletApplicationDto().name("PAGOPA").status(WalletApplicationStatusDto.DISABLED)

    val WALLET_SERVICE_2: WalletApplicationDto =
        WalletApplicationDto().name("PAGOPA").status(WalletApplicationStatusDto.ENABLED)

    val UPDATE_SERVICES_BODY: WalletApplicationUpdateRequestDto =
        WalletApplicationUpdateRequestDto().applications(listOf(WALLET_SERVICE_1, WALLET_SERVICE_2))

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
