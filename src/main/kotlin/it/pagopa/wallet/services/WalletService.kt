package it.pagopa.wallet.services

import it.pagopa.generated.npg.model.*
import it.pagopa.generated.wallet.model.*
import it.pagopa.wallet.audit.*
import it.pagopa.wallet.client.EcommercePaymentMethodsClient
import it.pagopa.wallet.client.NpgClient
import it.pagopa.wallet.config.OnboardingConfig
import it.pagopa.wallet.config.SessionUrlConfig
import it.pagopa.wallet.documents.wallets.Application
import it.pagopa.wallet.documents.wallets.details.CardDetails
import it.pagopa.wallet.documents.wallets.details.PayPalDetails as PayPalDetailsDocument
import it.pagopa.wallet.documents.wallets.details.WalletDetails
import it.pagopa.wallet.domain.services.ApplicationMetadata
import it.pagopa.wallet.domain.services.ServiceId
import it.pagopa.wallet.domain.services.ServiceName
import it.pagopa.wallet.domain.services.ServiceStatus
import it.pagopa.wallet.domain.wallets.*
import it.pagopa.wallet.domain.wallets.details.*
import it.pagopa.wallet.domain.wallets.details.CardDetails as DomainCardDetails
import it.pagopa.wallet.domain.wallets.details.PayPalDetails
import it.pagopa.wallet.exception.*
import it.pagopa.wallet.repositories.NpgSession
import it.pagopa.wallet.repositories.NpgSessionsTemplateWrapper
import it.pagopa.wallet.repositories.ServiceRepository
import it.pagopa.wallet.repositories.WalletRepository
import it.pagopa.wallet.util.UniqueIdUtils
import java.net.URI
import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import java.time.Instant
import java.time.OffsetDateTime
import java.util.*
import kotlinx.coroutines.reactor.mono
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.web.util.UriComponentsBuilder
import reactor.core.publisher.Mono
import reactor.kotlin.core.publisher.switchIfEmpty
import reactor.kotlin.core.publisher.toFlux
import reactor.kotlin.core.publisher.toMono

@Service
class WalletService(
    @Autowired private val walletRepository: WalletRepository,
    @Autowired private val serviceRepository: ServiceRepository,
    @Autowired private val ecommercePaymentMethodsClient: EcommercePaymentMethodsClient,
    @Autowired private val npgClient: NpgClient,
    @Autowired private val npgSessionRedisTemplate: NpgSessionsTemplateWrapper,
    @Autowired private val sessionUrlConfig: SessionUrlConfig,
    @Autowired private val uniqueIdUtils: UniqueIdUtils,
    @Autowired private val onboardingConfig: OnboardingConfig,
    @Autowired @Value("\${wallet.payment.cardReturnUrl}") private val walletPaymentReturnUrl: String
) {

    companion object {
        const val CREATE_HOSTED_ORDER_REQUEST_VERSION: String = "2"
        const val CREATE_HOSTED_ORDER_REQUEST_CURRENCY_EUR: String = "EUR"
        const val CREATE_HOSTED_ORDER_REQUEST_VERIFY_AMOUNT: String = "0"
        const val CREATE_HOSTED_ORDER_REQUEST_LANGUAGE_ITA = "ITA"
    }

    /*
     * Logger instance
     */
    var logger: Logger = LoggerFactory.getLogger(this.javaClass)

    private data class SessionCreationData(
        val paymentGatewayResponse: Fields,
        val wallet: Wallet,
        val orderId: String,
        val isAPM: Boolean
    )

    fun createWallet(
        serviceList: List<ServiceName>,
        userId: UUID,
        paymentMethodId: UUID
    ): Mono<Pair<LoggedAction<Wallet>, URI>> {
        logger.info("Create wallet with payment methodId: $paymentMethodId and userId: $userId")
        return ecommercePaymentMethodsClient
            .getPaymentMethodById(paymentMethodId.toString())
            .map {
                val creationTime = Instant.now()
                return@map Pair(
                    Wallet(
                        id = WalletId(UUID.randomUUID()),
                        userId = UserId(userId),
                        status = WalletStatusDto.CREATED,
                        paymentMethodId = PaymentMethodId(paymentMethodId),
                        version = 0,
                        creationDate = creationTime,
                        updateDate = creationTime
                    ),
                    it
                )
            }
            .flatMap { (wallet, paymentMethodResponse) ->
                walletRepository
                    .save(wallet.toDocument())
                    .map { LoggedAction(wallet, WalletAddedEvent(wallet.id.value.toString())) }
                    .map { loggedAction ->
                        Pair(
                            loggedAction,
                            paymentMethodResponse.name.let {
                                /*
                                 * Safe value of call here since EcommercePaymentMethodsClient already perform a check
                                 * against returned payment method name and WalletDetailsType enumeration
                                 */
                                when (WalletDetailsType.valueOf(it)) {
                                    WalletDetailsType.CARDS -> onboardingConfig.cardReturnUrl
                                    WalletDetailsType.PAYPAL -> onboardingConfig.apmReturnUrl
                                }
                            }
                        )
                    }
            }
    }
    fun createWalletForTransaction(
        userId: UUID,
        paymentMethodId: UUID,
        transactionId: String,
        amount: Int
    ): Mono<Pair<LoggedAction<Wallet>, Optional<URI>>> {
        logger.info(
            "Create wallet for transaction with contextual onboard for payment methodId: $paymentMethodId userId: $userId and transactionId: $transactionId"
        )
        return ecommercePaymentMethodsClient
            .getPaymentMethodById(paymentMethodId.toString())
            .map {
                val creationTime = Instant.now()
                return@map Pair(
                    Wallet(
                        id = WalletId(UUID.randomUUID()),
                        userId = UserId(userId),
                        status = WalletStatusDto.CREATED,
                        paymentMethodId = PaymentMethodId(paymentMethodId),
                        version = 0,
                        creationDate = creationTime,
                        updateDate = creationTime,
                        applications =
                            listOf(
                                Application(
                                    ServiceId(UUID.randomUUID()),
                                    ServiceName(
                                        ServiceNameDto.PAGOPA.value
                                    ), // We enter a static value since these wallets will be
                                    // created only for pagopa payments
                                    ServiceStatus.ENABLED,
                                    creationTime,
                                    ApplicationMetadata(
                                        hashMapOf(
                                            Pair("paymentWithContextualOnboard", "true"),
                                            Pair("transactionId", transactionId),
                                            Pair("amount", amount.toString())
                                        )
                                    )
                                )
                            )
                    ),
                    it
                )
            }
            .flatMap { (wallet, paymentMethodResponse) ->
                walletRepository
                    .save(wallet.toDocument())
                    .map { LoggedAction(wallet, WalletAddedEvent(wallet.id.value.toString())) }
                    .map { loggedAction ->
                        Pair(
                            loggedAction,
                            paymentMethodResponse.name.let {
                                when (WalletDetailsType.valueOf(it)) {
                                    WalletDetailsType.CARDS ->
                                        Optional.of(URI.create(walletPaymentReturnUrl))
                                    else -> {
                                        Optional.empty()
                                    }
                                }
                            }
                        )
                    }
            }
    }

    fun createSessionWallet(
        walletId: UUID,
        sessionInputDataDto: SessionInputDataDto
    ): Mono<Pair<SessionWalletCreateResponseDto, LoggedAction<Wallet>>> {
        logger.info("Create session for walletId: $walletId")
        return walletRepository
            .findById(walletId.toString())
            .switchIfEmpty { Mono.error(WalletNotFoundException(WalletId(walletId))) }
            .map { it.toDomain() }
            .filter { it.status == WalletStatusDto.CREATED }
            .switchIfEmpty { Mono.error(WalletConflictStatusException(WalletId(walletId))) }
            .flatMap {
                ecommercePaymentMethodsClient
                    .getPaymentMethodById(it.paymentMethodId.value.toString())
                    .map { paymentMethod -> paymentMethod to it }
            }
            .flatMap { (paymentMethodResponse, wallet) ->
                generateNPGUniqueIdentifiers().map { (orderId, contractId) ->
                    Triple(Pair(orderId, contractId), paymentMethodResponse, wallet)
                }
            }
            .flatMap { (uniqueIds, paymentMethod, wallet) ->
                val orderId = uniqueIds.first
                val contractId = uniqueIds.second
                val basePath = URI.create(sessionUrlConfig.basePath)
                val merchantUrl = sessionUrlConfig.basePath
                val resultUrl = basePath.resolve(sessionUrlConfig.outcomeSuffix)
                val cancelUrl = basePath.resolve(sessionUrlConfig.cancelSuffix)
                val notificationUrl =
                    UriComponentsBuilder.fromHttpUrl(sessionUrlConfig.notificationUrl)
                        .build(mapOf(Pair("walletId", wallet.id.value), Pair("orderId", orderId)))

                npgClient
                    .createNpgOrderBuild(
                        UUID.randomUUID(),
                        CreateHostedOrderRequest()
                            .version(CREATE_HOSTED_ORDER_REQUEST_VERSION)
                            .merchantUrl(merchantUrl)
                            .order(
                                Order()
                                    .orderId(orderId)
                                    .amount(CREATE_HOSTED_ORDER_REQUEST_VERIFY_AMOUNT)
                                    .currency(CREATE_HOSTED_ORDER_REQUEST_CURRENCY_EUR)
                                // TODO customerId must be valorised with the one coming from
                            )
                            .paymentSession(
                                PaymentSession()
                                    .actionType(ActionType.VERIFY)
                                    .recurrence(
                                        RecurringSettings()
                                            .action(RecurringAction.CONTRACT_CREATION)
                                            .contractId(contractId)
                                            .contractType(RecurringContractType.CIT)
                                    )
                                    .amount(CREATE_HOSTED_ORDER_REQUEST_VERIFY_AMOUNT)
                                    .language(CREATE_HOSTED_ORDER_REQUEST_LANGUAGE_ITA)
                                    .captureType(CaptureType.IMPLICIT)
                                    .paymentService(paymentMethod.name)
                                    .resultUrl(resultUrl.toString())
                                    .cancelUrl(cancelUrl.toString())
                                    .notificationUrl(notificationUrl.toString())
                            )
                    )
                    .map { hostedOrderResponse ->
                        val isAPM = paymentMethod.paymentTypeCode != "CP"

                        val newDetails =
                            when (sessionInputDataDto) {
                                is SessionInputCardDataDto -> wallet.details
                                is SessionInputPayPalDataDto ->
                                    PayPalDetails(null, sessionInputDataDto.pspId)
                                else ->
                                    throw InternalServerErrorException("Unhandled session input")
                            }

                        /*
                         * Credit card onboarding requires a two-step validation process
                         * (see WalletService#confirmPaymentCard), while for APMs
                         * we just need the gateway to notify us of the onboarding outcome
                         */
                        val newStatus =
                            if (isAPM) {
                                WalletStatusDto.VALIDATION_REQUESTED
                            } else {
                                WalletStatusDto.INITIALIZED
                            }
                        val updatedWallet =
                            wallet.copy(
                                contractId = ContractId(contractId),
                                status = newStatus,
                                details = newDetails
                            )
                        SessionCreationData(
                            hostedOrderResponse,
                            updatedWallet,
                            orderId,
                            isAPM = isAPM
                        )
                    }
            }
            .flatMap { sessionCreationData ->
                walletRepository
                    .save(sessionCreationData.wallet.toDocument())
                    .thenReturn(sessionCreationData)
            }
            .flatMap { (hostedOrderResponse, wallet, orderId, isAPM) ->
                npgSessionRedisTemplate
                    .save(
                        NpgSession(
                            orderId,
                            URLDecoder.decode(
                                hostedOrderResponse.sessionId,
                                StandardCharsets.UTF_8
                            ),
                            hostedOrderResponse.securityToken.toString(),
                            wallet.id.value.toString()
                        )
                    )
                    .toMono()
                    .map {
                        SessionWalletCreateResponseDto()
                            .orderId(orderId)
                            .sessionData(buildResponseSessionData(hostedOrderResponse, isAPM))
                    }
                    .map { it to wallet }
            }
            .map { (sessionResponseDto, wallet) ->
                sessionResponseDto to
                    LoggedAction(wallet, SessionWalletAddedEvent(wallet.id.value.toString()))
            }
    }

    private fun buildResponseSessionData(
        hostedOrderResponse: Fields,
        isAPM: Boolean
    ): SessionWalletCreateResponseSessionDataDto =
        if (isAPM) {
            if (hostedOrderResponse.state != WorkflowState.REDIRECTED_TO_EXTERNAL_DOMAIN) {
                throw NpgClientException(
                    "Got state ${hostedOrderResponse.state} instead of REDIRECTED_TO_EXTERNAL_DOMAIN for APM session initialization",
                    HttpStatus.BAD_GATEWAY
                )
            }

            SessionWalletCreateResponseAPMDataDto()
                .redirectUrl(hostedOrderResponse.url)
                .paymentMethodType("apm")
        } else {
            val cardFields = hostedOrderResponse.fields!!
            if (cardFields.isEmpty()) {
                throw NpgClientException(
                    "Received empty fields array in orders/build call to NPG!",
                    HttpStatus.BAD_GATEWAY
                )
            }

            SessionWalletCreateResponseCardDataDto()
                .paymentMethodType("cards")
                .cardFormFields(
                    cardFields
                        .stream()
                        .map { f ->
                            FieldDto()
                                .id(f.id)
                                .src(URI.create(f.src))
                                .type(f.type)
                                .propertyClass(f.propertyClass)
                        }
                        .toList()
                )
        }

    fun validateWalletSession(
        orderId: String,
        walletId: UUID
    ): Mono<Pair<WalletVerifyRequestsResponseDto, LoggedAction<Wallet>>> {
        val correlationId = UUID.randomUUID()
        return mono { npgSessionRedisTemplate.findById(orderId) }
            .switchIfEmpty { Mono.error(SessionNotFoundException(orderId)) }
            .flatMap { session ->
                walletRepository
                    .findById(walletId.toString())
                    .switchIfEmpty { Mono.error(WalletNotFoundException(WalletId(walletId))) }
                    .filter { session.walletId == it.id }
                    .switchIfEmpty {
                        Mono.error(
                            WalletSessionMismatchException(session.sessionId, WalletId(walletId))
                        )
                    }
                    .filter { it.status == WalletStatusDto.INITIALIZED.value }
                    .switchIfEmpty { Mono.error(WalletConflictStatusException(WalletId(walletId))) }
                    .flatMap { wallet ->
                        ecommercePaymentMethodsClient
                            .getPaymentMethodById(wallet.paymentMethodId)
                            .flatMap {
                                when (it.paymentTypeCode) {
                                    "CP" ->
                                        confirmPaymentCard(
                                            session.sessionId,
                                            correlationId,
                                            orderId,
                                            wallet.toDomain()
                                        )
                                    else ->
                                        throw NoCardsSessionValidateRequestException(
                                            WalletId(walletId)
                                        )
                                }
                            }
                    }
                    .flatMap { (response, wallet) ->
                        walletRepository.save(wallet.toDocument()).map {
                            response to
                                LoggedAction(wallet, WalletDetailsAddedEvent(walletId.toString()))
                        }
                    }
            }
    }

    private fun confirmPaymentCard(
        sessionId: String,
        correlationId: UUID,
        orderId: String,
        wallet: Wallet
    ): Mono<Pair<WalletVerifyRequestsResponseDto, Wallet>> =
        npgClient
            .getCardData(sessionId, correlationId)
            .flatMap {
                npgClient
                    .confirmPayment(
                        ConfirmPaymentRequest().sessionId(sessionId).amount("0"),
                        correlationId
                    )
                    .map { state -> state to it }
            }
            .doOnNext { logger.debug("State Response: {}", it.first) }
            .filter { (state) ->
                state.state == WorkflowState.GDI_VERIFICATION &&
                    state.fieldSet?.fields != null &&
                    state.fieldSet!!.fields!!.isNotEmpty() &&
                    state.fieldSet!!.fields!![0]!!.src != null
            }
            .switchIfEmpty {
                walletRepository
                    .save(wallet.copy(status = WalletStatusDto.ERROR).toDocument())
                    .flatMap { Mono.error(BadGatewayException("Invalid state received from NPG")) }
            }
            .flatMap { (state, cardData) ->
                mono { state }
                    .map {
                        WalletVerifyRequestsResponseDto()
                            .orderId(orderId)
                            .details(
                                WalletVerifyRequestCardDetailsDto()
                                    .type("CARD")
                                    .iframeUrl(
                                        Base64.getUrlEncoder()
                                            .encodeToString(
                                                it.fieldSet!!
                                                    .fields!![0]
                                                    .src!!
                                                    .toByteArray(StandardCharsets.UTF_8)
                                            )
                                    )
                            )
                    }
                    .map { response -> response to cardData }
            }
            .map { (response, data) ->
                response to
                    wallet.copy(
                        status = WalletStatusDto.VALIDATION_REQUESTED,
                        details =
                            DomainCardDetails(
                                Bin(data.bin.orEmpty()),
                                MaskedPan(
                                    data.bin.orEmpty() +
                                        ("*".repeat(
                                            16 -
                                                data.bin.orEmpty().length -
                                                data.lastFourDigits.orEmpty().length
                                        )) +
                                        data.lastFourDigits.orEmpty()
                                ),
                                ExpiryDate(data.expiringDate.orEmpty()),
                                WalletCardDetailsDto.BrandEnum.valueOf(data.circuit.orEmpty()),
                                CardHolderName("?")
                            )
                    )
            }

    fun findWallet(walletId: UUID): Mono<WalletInfoDto> {
        return walletRepository
            .findById(walletId.toString())
            .switchIfEmpty { Mono.error(WalletNotFoundException(WalletId(walletId))) }
            .map { wallet -> toWalletInfoDto(wallet) }
    }

    fun findWalletByUserId(userId: UUID): Mono<WalletsDto> {
        return walletRepository.findByUserId(userId.toString()).collectList().map { toWallets(it) }
    }

    fun findWalletAuthData(walletId: WalletId): Mono<WalletAuthDataDto> {
        return walletRepository
            .findById(walletId.value.toString())
            .switchIfEmpty { Mono.error(WalletNotFoundException(walletId)) }
            .map { wallet -> toWalletInfoAuthDataDto(wallet) }
    }

    fun notifyWallet(
        walletId: WalletId,
        orderId: String,
        securityToken: String,
        walletNotificationRequestDto: WalletNotificationRequestDto
    ): Mono<LoggedAction<Wallet>> {
        return mono { npgSessionRedisTemplate.findById(orderId) }
            .switchIfEmpty { Mono.error(SessionNotFoundException(orderId)) }
            .filter { session -> session.securityToken == securityToken }
            .switchIfEmpty {
                logger.error("Security token match failed")
                Mono.error(SecurityTokenMatchException())
            }
            .flatMap { session ->
                walletRepository
                    .findById(walletId.value.toString())
                    .switchIfEmpty { Mono.error(WalletNotFoundException(walletId)) }
                    .filter { wallet -> session.walletId == wallet.id }
                    .switchIfEmpty {
                        Mono.error(WalletSessionMismatchException(session.sessionId, walletId))
                    }
                    .map { walletDocument -> walletDocument.toDomain() }
                    .filter { wallet -> wallet.status == WalletStatusDto.VALIDATION_REQUESTED }
                    .switchIfEmpty { Mono.error(WalletConflictStatusException(walletId)) }
                    .flatMap { wallet ->
                        val (newWalletStatus, walletDetails) =
                            handleWalletNotification(
                                wallet = wallet,
                                walletNotificationRequestDto = walletNotificationRequestDto
                            )
                        logger.info(
                            "Updating wallet [{}] from status: [{}] to [{}]",
                            wallet.id.value,
                            wallet.status,
                            newWalletStatus
                        )
                        walletRepository.save(
                            wallet
                                .copy(
                                    status = newWalletStatus,
                                    validationOperationResult =
                                        walletNotificationRequestDto.operationResult,
                                    details = walletDetails
                                )
                                .toDocument()
                        )
                    }
                    .map { wallet ->
                        LoggedAction(
                            wallet.toDomain(),
                            WalletNotificationEvent(
                                walletId.value.toString(),
                                walletNotificationRequestDto.operationId,
                                walletNotificationRequestDto.operationResult.value,
                                walletNotificationRequestDto.timestampOperation.toString()
                            )
                        )
                    }
            }
    }

    private fun handleWalletNotification(
        wallet: Wallet,
        walletNotificationRequestDto: WalletNotificationRequestDto
    ): Pair<WalletStatusDto, it.pagopa.wallet.domain.wallets.details.WalletDetails<*>> {
        val operationResult = walletNotificationRequestDto.operationResult
        val operationDetails = walletNotificationRequestDto.details
        logger.info(
            "Received wallet notification request for wallet with id: [{}]. Outcome: [{}], notification details: [{}]",
            wallet.id.value,
            operationResult,
            operationDetails
        )
        return when (val walletDetails = wallet.details) {
            is it.pagopa.wallet.domain.wallets.details.CardDetails ->
                if (operationResult == WalletNotificationRequestDto.OperationResultEnum.EXECUTED) {
                    Pair(WalletStatusDto.VALIDATED, walletDetails)
                } else {
                    Pair(WalletStatusDto.ERROR, walletDetails)
                }
            is PayPalDetails ->
                if (operationResult == WalletNotificationRequestDto.OperationResultEnum.EXECUTED) {
                    if (operationDetails is WalletNotificationRequestPaypalDetailsDto) {
                        Pair(
                            WalletStatusDto.VALIDATED,
                            walletDetails.copy(
                                maskedEmail = MaskedEmail(operationDetails.maskedEmail)
                            )
                        )
                    } else {
                        logger.error(
                            "No details received for PayPal wallet, cannot retrieve maskedEmail"
                        )
                        Pair(WalletStatusDto.ERROR, walletDetails)
                    }
                } else {
                    Pair(WalletStatusDto.ERROR, walletDetails)
                }
            else ->
                throw InvalidRequestException(
                    "Unhandled wallet details for notification request: $walletDetails"
                )
        }
    }

    fun findSessionWallet(
        xUserId: UUID,
        walletId: WalletId,
        orderId: String,
    ): Mono<SessionWalletRetrieveResponseDto> {
        return mono { npgSessionRedisTemplate.findById(orderId) }
            .switchIfEmpty { Mono.error(SessionNotFoundException(orderId)) }
            .flatMap { session ->
                walletRepository
                    .findByIdAndUserId(walletId.value.toString(), xUserId.toString())
                    .switchIfEmpty { Mono.error(WalletNotFoundException(walletId)) }
                    .filter { wallet -> session.walletId == wallet.id.toString() }
                    .switchIfEmpty {
                        Mono.error(WalletSessionMismatchException(session.sessionId, walletId))
                    }
                    .map { walletDocument -> walletDocument.toDomain() }
                    .filter { wallet ->
                        wallet.status == WalletStatusDto.VALIDATION_REQUESTED ||
                            wallet.status == WalletStatusDto.VALIDATED ||
                            wallet.status == WalletStatusDto.ERROR
                    }
                    .switchIfEmpty { Mono.error(WalletConflictStatusException(walletId)) }
                    .map { wallet ->
                        val isFinalStatus =
                            wallet.status == WalletStatusDto.VALIDATED ||
                                wallet.status == WalletStatusDto.ERROR
                        SessionWalletRetrieveResponseDto()
                            .orderId(orderId)
                            .walletId(walletId.value.toString())
                            .isFinalOutcome(isFinalStatus)
                            .outcome(
                                if (isFinalStatus)
                                    wallet.validationOperationResult?.let {
                                        retrieveFinalOutcome(it)
                                    }
                                else null
                            )
                    }
            }
    }

    private fun toWallets(walletList: List<it.pagopa.wallet.documents.wallets.Wallet>): WalletsDto =
        WalletsDto().wallets(walletList.map { toWalletInfoDto(it) })

    private fun toWalletInfoDto(wallet: it.pagopa.wallet.documents.wallets.Wallet): WalletInfoDto? =
        WalletInfoDto()
            .walletId(UUID.fromString(wallet.id))
            .status(WalletStatusDto.valueOf(wallet.status))
            .paymentMethodId(wallet.paymentMethodId)
            .paymentInstrumentId(wallet.paymentInstrumentId.let { it.toString() })
            .userId(wallet.userId)
            .updateDate(OffsetDateTime.parse(wallet.updateDate.toString()))
            .creationDate(OffsetDateTime.parse(wallet.creationDate.toString()))
            .services(
                wallet.applications.map { application ->
                    ServiceDto()
                        .name(ServiceNameDto.valueOf(application.name))
                        .status(ServiceStatusDto.valueOf(application.status))
                }
            )
            .details(toWalletInfoDetailsDto(wallet.details))

    private fun toWalletInfoDetailsDto(details: WalletDetails<*>?): WalletInfoDetailsDto? {
        return when (details) {
            is CardDetails ->
                WalletCardDetailsDto()
                    .type(details.type)
                    .bin(details.bin)
                    .holder(details.holder)
                    .expiryDate(details.expiryDate)
                    .maskedPan(details.maskedPan)
            is PayPalDetailsDocument ->
                WalletPaypalDetailsDto().maskedEmail(details.maskedEmail).pspId(details.pspId)
            else -> null
        }
    }

    private fun toWalletInfoAuthDataDto(
        wallet: it.pagopa.wallet.documents.wallets.Wallet
    ): WalletAuthDataDto {
        val (brand, paymentMethodData) =
            when (wallet.details) {
                is CardDetails ->
                    wallet.details.brand to
                        WalletAuthCardDataDto().paymentMethodType("cards").bin(wallet.details.bin)
                is PayPalDetailsDocument ->
                    "PAYPAL" to WalletAuthAPMDataDto().paymentMethodType("apm")
                null ->
                    throw RuntimeException(
                        "Called getAuthData on null wallet details for wallet id: ${wallet.id}!"
                    )
                else ->
                    throw RuntimeException(
                        "Unhandled wallet details variant in getAuthData for wallet id ${wallet.id}"
                    )
            }

        return WalletAuthDataDto()
            .walletId(UUID.fromString(wallet.id))
            .contractId(wallet.contractId)
            .brand(brand)
            .paymentMethodData(paymentMethodData)
    }

    fun updateWalletServices(
        walletId: UUID,
        servicesToUpdate: List<Pair<ServiceName, ServiceStatus>>
    ): Mono<LoggedAction<WalletServiceUpdateData>> {
        return walletRepository
            .findById(walletId.toString())
            .switchIfEmpty { Mono.error(WalletNotFoundException(WalletId(walletId))) }
            .flatMap { wallet ->
                val walletApplications =
                    wallet.applications.associateBy { ServiceName(it.name) }.toMap()

                servicesToUpdate
                    .toFlux()
                    .flatMap { (serviceName, requestedStatus) ->
                        serviceRepository
                            .findByName(serviceName.name)
                            .map { Triple(it, serviceName, requestedStatus) }
                            .switchIfEmpty(Mono.error(ServiceNameNotFoundException(serviceName)))
                    }
                    .reduce(
                        Triple(
                            mutableMapOf<ServiceName, ServiceStatus>(),
                            mutableMapOf<ServiceName, ServiceStatus>(),
                            walletApplications.toMutableMap()
                        )
                    ) {
                        (
                            servicesUpdatedSuccessfully,
                            servicesWithUpdateFailed,
                            updatedApplications),
                        (service, serviceName, requestedStatus) ->
                        val serviceGlobalStatus = ServiceStatus.valueOf(service.status)
                        val walletApplication = walletApplications[serviceName]

                        if (
                            ServiceStatus.canChangeToStatus(
                                requested = requestedStatus,
                                global = serviceGlobalStatus
                            )
                        ) {
                            updatedApplications[serviceName] =
                                walletApplication?.copy(
                                    lastUpdateDate = Instant.now().toString(),
                                    status = requestedStatus.name
                                )
                                    ?: Application(
                                        id = service.id,
                                        name = serviceName.name,
                                        lastUpdateDate = Instant.now().toString(),
                                        status = requestedStatus.name,
                                        metadata = hashMapOf()
                                    )
                            servicesUpdatedSuccessfully[serviceName] = requestedStatus
                        } else {
                            servicesWithUpdateFailed[serviceName] = serviceGlobalStatus
                        }

                        Triple(
                            servicesUpdatedSuccessfully,
                            servicesWithUpdateFailed,
                            updatedApplications
                        )
                    }
                    .map {
                        (servicesUpdatedSuccessfully, servicesWithUpdateFailed, updatedApplications)
                        ->
                        WalletServiceUpdateData(
                            servicesUpdatedSuccessfully,
                            servicesWithUpdateFailed,
                            wallet.copy(
                                applications = updatedApplications.values.toList(),
                                updateDate = Instant.now()
                            )
                        )
                    }
            }
            .flatMap { walletRepository.save(it.updatedWallet).thenReturn(it) }
            .map { LoggedAction(it, WalletPatchEvent(it.updatedWallet.id)) }
    }

    /**
     * The method is used to generate the unique ids useful for the request to NPG (orderId,
     * contractId)
     *
     * @return Mono<Pair<orderId, contractId>>
     */
    private fun generateNPGUniqueIdentifiers(): Mono<Pair<String, String>> {
        return uniqueIdUtils.generateUniqueId().flatMap { orderId ->
            uniqueIdUtils.generateUniqueId().map { contractId -> orderId to contractId }
        }
    }

    /**
     * The method is used to retrieve the final outcome from validation operation result received
     * from NPG NUMBER_0 -> SUCCESS NUMBER_1 -> GENERIC_ERROR NUMBER_2 -> AUTH_ERROR NUMBER_4 ->
     * TIMEOUT NUMBER_8 -> CANCELED_BY_USER
     *
     * @param operationResult the operation result used for retrieve outcome
     * @return Mono<SessionWalletRetrieveResponseDto.OutcomeEnum>
     */
    private fun retrieveFinalOutcome(
        operationResult: WalletNotificationRequestDto.OperationResultEnum
    ): SessionWalletRetrieveResponseDto.OutcomeEnum {
        return when (operationResult) {
            WalletNotificationRequestDto.OperationResultEnum.EXECUTED ->
                SessionWalletRetrieveResponseDto.OutcomeEnum.NUMBER_0
            WalletNotificationRequestDto.OperationResultEnum.CANCELED ->
                SessionWalletRetrieveResponseDto.OutcomeEnum.NUMBER_8
            WalletNotificationRequestDto.OperationResultEnum.THREEDS_VALIDATED,
            WalletNotificationRequestDto.OperationResultEnum.DENIED_BY_RISK,
            WalletNotificationRequestDto.OperationResultEnum.THREEDS_FAILED,
            WalletNotificationRequestDto.OperationResultEnum.DECLINED ->
                SessionWalletRetrieveResponseDto.OutcomeEnum.NUMBER_2
            WalletNotificationRequestDto.OperationResultEnum.PENDING ->
                SessionWalletRetrieveResponseDto.OutcomeEnum.NUMBER_4
            else -> SessionWalletRetrieveResponseDto.OutcomeEnum.NUMBER_1
        }
    }
}
