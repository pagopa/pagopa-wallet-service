package it.pagopa.wallet

import it.pagopa.generated.ecommerce.paymentmethods.model.PaymentMethodManagementType
import it.pagopa.generated.ecommerce.paymentmethods.model.PaymentMethodResponse
import it.pagopa.generated.ecommerce.paymentmethods.model.PaymentMethodStatus
import it.pagopa.generated.ecommerce.paymentmethodshandler.model.FeeRange
import it.pagopa.generated.ecommerce.paymentmethodshandler.model.PaymentMethodResponse as PaymentMethodHandlerResponse
import it.pagopa.generated.wallet.model.*
import it.pagopa.generated.wallet.model.WalletNotificationRequestDto.OperationResultEnum
import it.pagopa.wallet.documents.applications.Application
import it.pagopa.wallet.documents.wallets.Client as ClientDocument
import it.pagopa.wallet.documents.wallets.Wallet
import it.pagopa.wallet.documents.wallets.WalletApplication as WalletApplicationDocument
import it.pagopa.wallet.documents.wallets.details.CardDetails as CardDetailsDocument
import it.pagopa.wallet.documents.wallets.details.PayPalDetails as PayPalDetailsDocument
import it.pagopa.wallet.documents.wallets.details.WalletDetails
import it.pagopa.wallet.domain.applications.ApplicationDescription
import it.pagopa.wallet.domain.applications.ApplicationId
import it.pagopa.wallet.domain.applications.ApplicationStatus
import it.pagopa.wallet.domain.methods.PaymentMethodInfo
import it.pagopa.wallet.domain.wallets.*
import it.pagopa.wallet.domain.wallets.WalletApplication
import it.pagopa.wallet.domain.wallets.details.*
import it.pagopa.wallet.util.TransactionId
import java.time.Instant
import java.time.LocalDate
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
    val WALLET_APPLICATION_PAGOPA_ID = WalletApplicationId("PAGOPA")
    val OTHER_WALLET_APPLICATION_ID = WalletApplicationId("PARI")
    val PAYMENT_METHOD_ID_CARDS = PaymentMethodId(UUID.randomUUID())
    val PAYMENT_METHOD_ID_APM = PaymentMethodId(UUID.randomUUID())
    val VALIDATION_OPERATION_ID: UUID = UUID.randomUUID()
    val TEST_DEFAULT_CLIENTS: Map<Client.Id, Client> =
        mapOf(
            Client.WellKnown.IO to Client(Client.Status.ENABLED),
            Client.Unknown("unknownClient") to Client(Client.Status.DISABLED))
    private val APPLICATION_METADATA_HASHMAP: HashMap<String, String> = hashMapOf()
    private val APPLICATION_METADATA_HASHMAP_CONTEXTUAL_ONBOARD: HashMap<String, String> =
        hashMapOf(
            "transactionId" to "transactionId",
            "amount" to "1200",
            "paymentWithContextualOnboard" to "true")
    private val APPLICATION_METADATA_HASHMAP_NO_CONTEXTUAL_ONBOARD: HashMap<String, String> =
        hashMapOf(
            "transactionId" to "transactionId",
            "amount" to "1200",
            "paymentWithContextualOnboard" to "false")
    val APPLICATION_METADATA =
        WalletApplicationMetadata(
            APPLICATION_METADATA_HASHMAP.mapKeys {
                WalletApplicationMetadata.Metadata.fromMetadataValue(it.key)
            })
    val CONTRACT_ID = ContractId("W49357937935R869i")
    val BIN = Bin("42424242")
    val LAST_FOUR_DIGITS = LastFourDigits("5555")
    val EXP_DATE = ExpiryDate("203012")
    val BRAND = CardBrand("MC")
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
            errorReason = null,
            applications = listOf(),
            details = null,
            clients =
                TEST_DEFAULT_CLIENTS.entries.associate { it.key.name to it.value.toDocument() },
            version = 0,
            creationDate = creationDate,
            updateDate = creationDate,
            onboardingChannel = OnboardingChannel.IO.toString())
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
            errorReason = null,
            applications,
            details = null,
            clients =
                mapOf(
                    Client.WellKnown.IO.name to ClientDocument(Client.Status.ENABLED.name),
                ),
            version = 0,
            creationDate = creationDate,
            updateDate = creationDate,
            onboardingChannel = OnboardingChannel.IO.toString())
    }

    fun newWalletDocumentForPaymentWithContextualOnboardToBeSaved(
        paymentMethodId: PaymentMethodId,
        application: Application,
        client: Client.Id = Client.WellKnown.IO
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
                                    true.toString()),
                                Pair(
                                    WalletApplicationMetadata.Metadata.TRANSACTION_ID.value,
                                    TransactionId(TRANSACTION_ID).trimmedUUIDString),
                                Pair(
                                    WalletApplicationMetadata.Metadata.AMOUNT.value,
                                    AMOUNT.toString())))),
                clients =
                    Client.WellKnown.values().associate {
                        it.name to
                            ClientDocument(
                                if (it == client) {
                                    Client.Status.ENABLED.name
                                } else {
                                    Client.Status.DISABLED.name
                                })
                    })
    }

    private fun parseWalletApplicationStatus(status: String): String =
        when (status) {
            ApplicationStatus.ENABLED.name -> WalletApplicationStatus.ENABLED.name
            else -> WalletApplicationStatus.DISABLED.name
        }

    fun walletDocumentCreatedStatus(
        paymentMethodId: PaymentMethodId,
        clients: Map<Client.Id, Client> = TEST_DEFAULT_CLIENTS
    ): Wallet {
        return Wallet(
            id = WALLET_UUID.value.toString(),
            userId = USER_ID.id.toString(),
            status = WalletStatusDto.CREATED.name,
            paymentMethodId = paymentMethodId.value.toString(),
            contractId = CONTRACT_ID.contractId,
            validationOperationResult = null,
            validationErrorCode = null,
            errorReason = null,
            applications = listOf(),
            details = null,
            clients = clients.entries.associate { it.key.name to it.value.toDocument() },
            version = 0,
            creationDate = creationDate,
            updateDate = creationDate,
            onboardingChannel = OnboardingChannel.IO.toString())
    }

    fun walletDocumentInitializedStatus(
        paymentMethodId: PaymentMethodId,
        clients: Map<Client.Id, Client> = TEST_DEFAULT_CLIENTS
    ): Wallet {
        return Wallet(
            id = WALLET_UUID.value.toString(),
            userId = USER_ID.id.toString(),
            status = WalletStatusDto.INITIALIZED.name,
            paymentMethodId = paymentMethodId.value.toString(),
            contractId = CONTRACT_ID.contractId,
            validationOperationResult = null,
            validationErrorCode = null,
            errorReason = null,
            applications = listOf(),
            details = null,
            clients = clients.entries.associate { it.key.name to it.value.toDocument() },
            version = 0,
            creationDate = creationDate,
            updateDate = creationDate,
            onboardingChannel = OnboardingChannel.IO.toString())
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
                            PAYMENT_INSTRUMENT_GATEWAY_ID.paymentInstrumentGatewayId)
                    } else {
                        PayPalDetailsDocument(
                            maskedEmail = null, pspId = PSP_ID, pspBusinessName = PSP_BUSINESS_NAME)
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
                                    true.toString()),
                                Pair(
                                    WalletApplicationMetadata.Metadata.TRANSACTION_ID.value,
                                    TransactionId(TRANSACTION_ID).trimmedUUIDString),
                                Pair(
                                    WalletApplicationMetadata.Metadata.AMOUNT.value,
                                    AMOUNT.toString())))))
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
                            this.creationDate.toString(),
                            this.creationDate.toString(),
                            hashMapOf(
                                Pair(
                                    WalletApplicationMetadata.Metadata
                                        .PAYMENT_WITH_CONTEXTUAL_ONBOARD
                                        .value,
                                    true.toString()),
                                Pair(
                                    WalletApplicationMetadata.Metadata.TRANSACTION_ID.value,
                                    TransactionId(TRANSACTION_ID).trimmedUUIDString),
                                Pair(
                                    WalletApplicationMetadata.Metadata.AMOUNT.value,
                                    AMOUNT.toString())))))
    }

    fun walletDocumentStatusValidatedCard(
        brand: CardBrand = BRAND,
        clients: Map<Client.Id, Client> = TEST_DEFAULT_CLIENTS
    ): Wallet {
        return Wallet(
            id = WALLET_UUID.value.toString(),
            userId = USER_ID.id.toString(),
            status = WalletStatusDto.VALIDATED.name,
            paymentMethodId = PAYMENT_METHOD_ID_CARDS.value.toString(),
            contractId = CONTRACT_ID.contractId,
            validationOperationResult = OperationResultEnum.EXECUTED.value,
            validationErrorCode = null,
            errorReason = null,
            applications =
                listOf(
                    WalletApplicationDocument(
                        WALLET_APPLICATION_ID.id,
                        WalletApplicationStatus.DISABLED.toString(),
                        TIMESTAMP.toString(),
                        TIMESTAMP.toString(),
                        APPLICATION_METADATA_HASHMAP)),
            details =
                CardDetailsDocument(
                    TYPE.toString(),
                    BIN.bin,
                    LAST_FOUR_DIGITS.lastFourDigits,
                    EXP_DATE.expDate,
                    brand.value,
                    PAYMENT_INSTRUMENT_GATEWAY_ID.paymentInstrumentGatewayId),
            clients = clients.entries.associate { it.key.name to it.value.toDocument() },
            version = 0,
            creationDate = creationDate,
            updateDate = creationDate,
            onboardingChannel = OnboardingChannel.IO.toString())
    }

    fun walletDocumentStatusValidatedCardWithContextualOnboard(
        brand: CardBrand = BRAND,
        clients: Map<Client.Id, Client> = TEST_DEFAULT_CLIENTS,
        contextualOnboard: Boolean = true
    ): Wallet {
        return Wallet(
            id = WALLET_UUID.value.toString(),
            userId = USER_ID.id.toString(),
            status = WalletStatusDto.VALIDATED.name,
            paymentMethodId = PAYMENT_METHOD_ID_CARDS.value.toString(),
            contractId = CONTRACT_ID.contractId,
            validationOperationResult = OperationResultEnum.EXECUTED.value,
            validationErrorCode = null,
            errorReason = null,
            applications =
                listOf(
                    WalletApplicationDocument(
                        WALLET_APPLICATION_ID.id,
                        WalletApplicationStatus.DISABLED.toString(),
                        TIMESTAMP.toString(),
                        TIMESTAMP.toString(),
                        if (contextualOnboard) APPLICATION_METADATA_HASHMAP_CONTEXTUAL_ONBOARD
                        else APPLICATION_METADATA_HASHMAP_NO_CONTEXTUAL_ONBOARD)),
            details =
                CardDetailsDocument(
                    TYPE.toString(),
                    BIN.bin,
                    LAST_FOUR_DIGITS.lastFourDigits,
                    EXP_DATE.expDate,
                    brand.value,
                    PAYMENT_INSTRUMENT_GATEWAY_ID.paymentInstrumentGatewayId),
            clients = clients.entries.associate { it.key.name to it.value.toDocument() },
            version = 0,
            creationDate = creationDate,
            updateDate = creationDate,
            onboardingChannel = OnboardingChannel.IO.toString())
    }

    fun walletDocumentStatusValidatedAPM(
        paypalEmail: String?,
        clients: Map<Client.Id, Client> = TEST_DEFAULT_CLIENTS
    ): Wallet {
        return Wallet(
            id = WALLET_UUID.value.toString(),
            userId = USER_ID.id.toString(),
            status = WalletStatusDto.VALIDATED.name,
            paymentMethodId = PAYMENT_METHOD_ID_CARDS.value.toString(),
            contractId = CONTRACT_ID.contractId,
            validationOperationResult = OperationResultEnum.EXECUTED.value,
            validationErrorCode = null,
            errorReason = null,
            applications =
                listOf(
                    WalletApplicationDocument(
                        WALLET_APPLICATION_ID.id,
                        WalletApplicationStatus.DISABLED.toString(),
                        TIMESTAMP.toString(),
                        TIMESTAMP.toString(),
                        APPLICATION_METADATA_HASHMAP)),
            details =
                PayPalDetailsDocument(
                    maskedEmail = paypalEmail, pspId = PSP_ID, pspBusinessName = PSP_BUSINESS_NAME),
            clients = clients.entries.associate { it.key.name to it.value.toDocument() },
            version = 0,
            creationDate = creationDate,
            updateDate = creationDate,
            onboardingChannel = OnboardingChannel.IO.toString())
    }

    fun walletDocumentVerifiedWithCardDetails(
        bin: String,
        lastFourDigits: String,
        expiryDate: String,
        paymentInstrumentGatewayId: String,
        brand: String,
        clients: Map<Client.Id, Client> = TEST_DEFAULT_CLIENTS
    ): Wallet {
        return Wallet(
            id = WALLET_UUID.value.toString(),
            userId = USER_ID.id.toString(),
            status = WalletStatusDto.VALIDATION_REQUESTED.name,
            paymentMethodId = PAYMENT_METHOD_ID_CARDS.value.toString(),
            contractId = CONTRACT_ID.contractId,
            validationOperationResult = null,
            validationErrorCode = null,
            errorReason = null,
            applications = listOf(),
            details =
                CardDetailsDocument(
                    WalletDetailsType.CARDS.name,
                    bin,
                    lastFourDigits,
                    expiryDate,
                    brand,
                    paymentInstrumentGatewayId),
            clients = clients.entries.associate { it.key.name to it.value.toDocument() },
            version = 0,
            creationDate = creationDate,
            updateDate = creationDate,
            onboardingChannel = OnboardingChannel.IO.toString())
    }

    fun walletDocumentVerifiedWithAPM(
        details: WalletDetails<*>,
        clients: Map<Client.Id, Client> = TEST_DEFAULT_CLIENTS
    ): Wallet {
        return Wallet(
            id = WALLET_UUID.value.toString(),
            userId = USER_ID.id.toString(),
            status = WalletStatusDto.VALIDATION_REQUESTED.name,
            paymentMethodId = PAYMENT_METHOD_ID_CARDS.value.toString(),
            contractId = CONTRACT_ID.contractId,
            validationOperationResult = null,
            validationErrorCode = null,
            errorReason = null,
            applications = listOf(),
            details = details,
            clients = clients.entries.associate { it.key.name to it.value.toDocument() },
            version = 0,
            creationDate = creationDate,
            updateDate = creationDate,
            onboardingChannel = OnboardingChannel.IO.toString())
    }

    fun walletDocumentWithError(
        operationResultEnum: OperationResultEnum,
        errorCode: String? = null,
        details: WalletDetails<*>? = null,
        clients: Map<Client.Id, Client> = TEST_DEFAULT_CLIENTS
    ): Wallet {
        return Wallet(
            id = WALLET_UUID.value.toString(),
            userId = USER_ID.id.toString(),
            status = WalletStatusDto.ERROR.name,
            paymentMethodId = PAYMENT_METHOD_ID_CARDS.value.toString(),
            contractId = CONTRACT_ID.contractId,
            validationOperationResult = operationResultEnum.value,
            validationErrorCode = errorCode,
            errorReason = null,
            applications = listOf(),
            details = details,
            clients = clients.entries.associate { it.key.name to it.value.toDocument() },
            version = 0,
            creationDate = creationDate,
            updateDate = creationDate,
            onboardingChannel = OnboardingChannel.IO.toString())
    }

    fun walletDocumentValidated(clients: Map<Client.Id, Client> = TEST_DEFAULT_CLIENTS): Wallet {
        return Wallet(
            id = WALLET_UUID.value.toString(),
            userId = USER_ID.id.toString(),
            status = WalletStatusDto.VALIDATED.name,
            paymentMethodId = PAYMENT_METHOD_ID_CARDS.value.toString(),
            contractId = CONTRACT_ID.contractId,
            validationOperationResult = OperationResultEnum.EXECUTED.toString(),
            validationErrorCode = null,
            errorReason = null,
            applications = listOf(),
            details = null,
            clients = clients.entries.associate { it.key.name to it.value.toDocument() },
            version = 0,
            creationDate = creationDate,
            updateDate = creationDate,
            onboardingChannel = OnboardingChannel.IO.toString())
    }

    fun walletDocumentEmptyCreatedStatus(): Wallet {
        return Wallet(
            id = WALLET_UUID.value.toString(),
            userId = USER_ID.id.toString(),
            status = WalletStatusDto.CREATED.name,
            paymentMethodId = PAYMENT_METHOD_ID_CARDS.value.toString(),
            contractId = CONTRACT_ID.contractId,
            validationOperationResult = null,
            validationErrorCode = null,
            errorReason = null,
            applications = listOf(),
            details = null,
            clients =
                TEST_DEFAULT_CLIENTS.entries.associate { it.key.name to it.value.toDocument() },
            version = 0,
            creationDate = creationDate,
            updateDate = creationDate,
            onboardingChannel = OnboardingChannel.IO.toString())
    }

    fun walletDocumentEmptyApplicationsNullDetails(): Wallet {
        return Wallet(
            id = WALLET_UUID.value.toString(),
            userId = USER_ID.id.toString(),
            status = WalletStatusDto.CREATED.name,
            paymentMethodId = PAYMENT_METHOD_ID_CARDS.value.toString(),
            contractId = CONTRACT_ID.contractId,
            validationOperationResult = null,
            validationErrorCode = null,
            errorReason = null,
            applications = listOf(),
            details = null,
            clients =
                TEST_DEFAULT_CLIENTS.entries.associate { it.key.name to it.value.toDocument() },
            version = 0,
            creationDate = creationDate,
            updateDate = creationDate,
            onboardingChannel = OnboardingChannel.IO.toString())
    }

    fun walletDocumentEmptyContractId(): Wallet {
        return Wallet(
            id = WALLET_UUID.value.toString(),
            userId = USER_ID.id.toString(),
            status = WalletStatusDto.CREATED.name,
            paymentMethodId = PAYMENT_METHOD_ID_CARDS.value.toString(),
            contractId = null,
            validationOperationResult = null,
            validationErrorCode = null,
            errorReason = null,
            applications = listOf(),
            details = null,
            clients =
                TEST_DEFAULT_CLIENTS.entries.associate { it.key.name to it.value.toDocument() },
            version = 0,
            creationDate = creationDate,
            updateDate = creationDate,
            onboardingChannel = OnboardingChannel.IO.toString())
    }

    fun walletDocumentWithEmptyValidationOperationResult(): Wallet {
        return Wallet(
            id = WALLET_UUID.value.toString(),
            userId = USER_ID.id.toString(),
            status = WalletStatusDto.CREATED.name,
            paymentMethodId = PAYMENT_METHOD_ID_CARDS.value.toString(),
            contractId = null,
            validationOperationResult = OperationResultEnum.EXECUTED.value,
            validationErrorCode = null,
            errorReason = null,
            applications = listOf(),
            details = null,
            clients =
                TEST_DEFAULT_CLIENTS.entries.associate { it.key.name to it.value.toDocument() },
            version = 0,
            creationDate = creationDate,
            updateDate = creationDate,
            onboardingChannel = OnboardingChannel.IO.toString())
    }

    fun walletDocumentNullDetails(): Wallet {
        return Wallet(
            id = WALLET_UUID.value.toString(),
            userId = USER_ID.id.toString(),
            status = WalletStatusDto.CREATED.name,
            paymentMethodId = PAYMENT_METHOD_ID_CARDS.value.toString(),
            contractId = CONTRACT_ID.contractId,
            validationOperationResult = null,
            validationErrorCode = null,
            errorReason = null,
            applications =
                listOf(
                    WalletApplicationDocument(
                        WALLET_APPLICATION_ID.id,
                        WalletApplicationStatus.DISABLED.toString(),
                        TIMESTAMP.toString(),
                        TIMESTAMP.toString(),
                        APPLICATION_METADATA.data.mapKeys { it.key.value })),
            details = null,
            clients =
                TEST_DEFAULT_CLIENTS.entries.associate { it.key.name to it.value.toDocument() },
            version = 0,
            creationDate = creationDate,
            updateDate = creationDate,
            onboardingChannel = OnboardingChannel.IO.toString())
    }

    fun walletDomain(): it.pagopa.wallet.domain.wallets.Wallet {
        return WALLET_DOMAIN
    }

    fun walletDocument(): Wallet {
        return Wallet(
            id = WALLET_UUID.value.toString(),
            userId = USER_ID.id.toString(),
            status = WalletStatusDto.CREATED.name,
            paymentMethodId = PAYMENT_METHOD_ID_CARDS.value.toString(),
            contractId = CONTRACT_ID.contractId,
            validationOperationResult = OperationResultEnum.EXECUTED.value,
            validationErrorCode = null,
            errorReason = null,
            applications =
                listOf(
                    WalletApplicationDocument(
                        WALLET_APPLICATION_ID.id,
                        WalletApplicationStatus.DISABLED.toString(),
                        TIMESTAMP.toString(),
                        TIMESTAMP.toString(),
                        APPLICATION_METADATA_HASHMAP),
                    WalletApplicationDocument(
                        OTHER_WALLET_APPLICATION_ID.id,
                        WalletApplicationStatus.DISABLED.toString(),
                        TIMESTAMP.toString(),
                        TIMESTAMP.toString(),
                        mapOf())),
            details =
                CardDetailsDocument(
                    TYPE.toString(),
                    BIN.bin,
                    LAST_FOUR_DIGITS.lastFourDigits,
                    EXP_DATE.expDate,
                    BRAND.value,
                    PAYMENT_INSTRUMENT_GATEWAY_ID.paymentInstrumentGatewayId),
            clients =
                TEST_DEFAULT_CLIENTS.entries.associate { it.key.name to it.value.toDocument() },
            version = 0,
            creationDate = creationDate,
            updateDate = creationDate,
            onboardingChannel = OnboardingChannel.IO.toString())
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
                        APPLICATION_METADATA),
                    WalletApplication(
                        OTHER_WALLET_APPLICATION_ID,
                        WalletApplicationStatus.DISABLED,
                        TIMESTAMP,
                        TIMESTAMP,
                        WalletApplicationMetadata(mapOf()))),
            contractId = CONTRACT_ID,
            validationOperationResult = OperationResultEnum.EXECUTED,
            validationErrorCode = null,
            details =
                CardDetails(BIN, LAST_FOUR_DIGITS, EXP_DATE, BRAND, PAYMENT_INSTRUMENT_GATEWAY_ID),
            clients = TEST_DEFAULT_CLIENTS,
            version = 0,
            creationDate = creationDate,
            updateDate = creationDate,
            onboardingChannel = OnboardingChannel.IO)

    fun walletDomainEmptyServicesNullDetailsNoPaymentInstrument():
        it.pagopa.wallet.domain.wallets.Wallet {
        return Wallet(
            id = WALLET_UUID,
            userId = USER_ID,
            status = WalletStatusDto.CREATED,
            paymentMethodId = PAYMENT_METHOD_ID_CARDS,
            applications = listOf(),
            contractId = CONTRACT_ID,
            validationOperationResult = null,
            validationErrorCode = null,
            details = null,
            clients = TEST_DEFAULT_CLIENTS,
            version = 0,
            creationDate = creationDate,
            updateDate = creationDate,
            onboardingChannel = OnboardingChannel.IO)
    }

    fun walletInfoDto(): WalletInfoDto =
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
                    .type("CARDS")
                    .brand("MASTERCARD")
                    .expiryDate(EXP_DATE.expDate))

    fun walletInfoDtoAPM(): WalletInfoDto =
        WalletInfoDto()
            .walletId(WALLET_UUID.value)
            .status(WalletStatusDto.CREATED)
            .creationDate(OffsetDateTime.ofInstant(TIMESTAMP, ZoneId.systemDefault()))
            .updateDate(OffsetDateTime.ofInstant(TIMESTAMP, ZoneId.systemDefault()))
            .paymentMethodId(PAYMENT_METHOD_ID_APM.value.toString())
            .userId(USER_ID.id.toString())
            .applications(listOf())
            .details(
                WalletPaypalDetailsDto().type("PAYPAL").maskedEmail("maskedEmail").pspId(PSP_ID))

    fun walletCardAuthDataDto(): WalletAuthDataDto =
        WalletAuthDataDto()
            .walletId(WALLET_UUID.value)
            .contractId(CONTRACT_ID.contractId)
            .brand(BRAND.value)
            .paymentMethodData(WalletAuthCardDataDto().bin(BIN.bin).paymentMethodType("cards"))

    fun walletCardAuthDataContextualOnboardDto(): WalletAuthDataDto =
        WalletAuthDataDto()
            .walletId(WALLET_UUID.value)
            .contractId(CONTRACT_ID.contractId)
            .brand(BRAND.value)
            .paymentMethodData(WalletAuthCardDataDto().bin(BIN.bin).paymentMethodType("cards"))
            .contextualOnboardDetails(
                ContextualOnboardDetailsDto().amount(1200).transactionId("transactionId"))

    fun walletAPMAuthDataDto(): WalletAuthDataDto =
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
            TIMESTAMP.toString())

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
    const val PSP_BUSINESS_NAME = "pspBusinessName"

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
            .description("CARDS description")
            .methodManagement(PaymentMethodManagementType.ONBOARDABLE)
            .ranges(listOf())
    }

    fun getValidAPMPaymentMethod(): PaymentMethodResponse {
        return PaymentMethodResponse()
            .id(PAYMENT_METHOD_ID_APM.value.toString())
            .paymentTypeCode("PPAL")
            .status(PaymentMethodStatus.ENABLED)
            .name("PAYPAL")
            .description("PAYPAL description")
            .methodManagement(PaymentMethodManagementType.ONBOARDABLE)
            .ranges(listOf())
    }

    fun getDisabledCardsPaymentMethod(): PaymentMethodResponse {
        return PaymentMethodResponse()
            .id(PAYMENT_METHOD_ID_CARDS.value.toString())
            .paymentTypeCode("CP")
            .status(PaymentMethodStatus.DISABLED)
            .name("CARDS")
            .description("CARDS description")
            .methodManagement(PaymentMethodManagementType.ONBOARDABLE)
            .ranges(listOf())
    }

    fun getInvalidPaymentMethod(): PaymentMethodResponse {
        return PaymentMethodResponse()
            .id(PAYMENT_METHOD_ID_CARDS.value.toString())
            .paymentTypeCode("RICO")
            .status(PaymentMethodStatus.ENABLED)
            .name("INVALID")
            .description("INVALID description")
            .methodManagement(PaymentMethodManagementType.ONBOARDABLE)
            .ranges(listOf())
    }

    fun getUniqueId(): String {
        return "W49357937935R869i"
    }

    val CARD_ID_4 = UUID.randomUUID().toString()

    val NOTIFY_WALLET_REQUEST_OK_OPERATION_RESULT: WalletNotificationRequestDto =
        WalletNotificationRequestDto()
            .operationResult(OperationResultEnum.EXECUTED)
            .timestampOperation(OffsetDateTime.now())
            .operationId("validationOperationId")
            .details(
                WalletNotificationRequestCardDetailsDto()
                    .type("CARD")
                    .paymentInstrumentGatewayId(CARD_ID_4))

    val NOTIFY_WALLET_REQUEST_OK_OPERATION_RESULT_WITH_PAYPAL_DETAILS:
        WalletNotificationRequestDto =
        WalletNotificationRequestDto()
            .operationResult(OperationResultEnum.EXECUTED)
            .timestampOperation(OffsetDateTime.now())
            .operationId("validationOperationId")
            .details(
                WalletNotificationRequestPaypalDetailsDto()
                    .type("PAYPAL")
                    .maskedEmail(MASKED_EMAIL.value))

    val NOTIFY_WALLET_REQUEST_KO_OPERATION_RESULT_WITH_ERRORS: WalletNotificationRequestDto =
        WalletNotificationRequestDto()
            .operationResult(OperationResultEnum.DECLINED)
            .timestampOperation(OffsetDateTime.now())
            .operationId("validationOperationId")
            .errorCode("WG001")

    val NOTIFY_WALLET_REQUEST_KO_OPERATION_RESULT: WalletNotificationRequestDto =
        WalletNotificationRequestDto()
            .operationResult(OperationResultEnum.DECLINED)
            .timestampOperation(OffsetDateTime.now())
            .operationId("validationOperationId")

    fun PaymentMethodResponse.toPaymentMethodHandlerResponse(): PaymentMethodHandlerResponse {
        return this.let {
            PaymentMethodHandlerResponse()
                .id(it.id)
                .name(mapOf("IT" to it.name))
                .description(mapOf("IT" to it.description))
                .status(
                    when (it.status) {
                        PaymentMethodStatus.ENABLED ->
                            PaymentMethodHandlerResponse.StatusEnum.ENABLED

                        PaymentMethodStatus.DISABLED ->
                            PaymentMethodHandlerResponse.StatusEnum.DISABLED

                        else -> PaymentMethodHandlerResponse.StatusEnum.DISABLED
                    })
                .validityDateFrom(LocalDate.of(2000, 1, 1))
                .paymentTypeCode(
                    PaymentMethodHandlerResponse.PaymentTypeCodeEnum.valueOf(it.paymentTypeCode))
                .addPaymentMethodTypesItem(PaymentMethodHandlerResponse.PaymentMethodTypesEnum.APP)
                .feeRange(FeeRange().min(0).max(1000000))
                .paymentMethodAsset(it.asset)
                .methodManagement(
                    PaymentMethodHandlerResponse.MethodManagementEnum.valueOf(
                        it.methodManagement.toString()))
                .paymentMethodsBrandAssets(it.brandAssets)
        }
    }

    fun PaymentMethodResponse.toPaymentMethodInfo(): PaymentMethodInfo =
        PaymentMethodInfo(
            id = this.id,
            enabled = this.status == PaymentMethodStatus.ENABLED,
            paymentTypeCode = this.paymentTypeCode)

    fun PaymentMethodHandlerResponse.toPaymentMethodInfo(): PaymentMethodInfo =
        PaymentMethodInfo(
            id = this.id,
            enabled = this.status == PaymentMethodHandlerResponse.StatusEnum.ENABLED,
            paymentTypeCode = this.paymentTypeCode.toString())
}
