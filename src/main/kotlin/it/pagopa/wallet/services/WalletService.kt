package it.pagopa.wallet.services

import it.pagopa.generated.npg.model.*
import it.pagopa.generated.wallet.model.*
import it.pagopa.wallet.audit.*
import it.pagopa.wallet.client.EcommercePaymentMethodsClient
import it.pagopa.wallet.client.NpgClient
import it.pagopa.wallet.config.OnboardingConfig
import it.pagopa.wallet.config.SessionUrlConfig
import it.pagopa.wallet.documents.wallets.details.CardDetails
import it.pagopa.wallet.documents.wallets.details.PayPalDetails as PayPalDetailsDocument
import it.pagopa.wallet.documents.wallets.details.WalletDetails
import it.pagopa.wallet.domain.wallets.*
import it.pagopa.wallet.domain.wallets.details.*
import it.pagopa.wallet.domain.wallets.details.CardDetails as DomainCardDetails
import it.pagopa.wallet.domain.wallets.details.PayPalDetails
import it.pagopa.wallet.exception.*
import it.pagopa.wallet.repositories.ApplicationRepository
import it.pagopa.wallet.repositories.NpgSession
import it.pagopa.wallet.repositories.NpgSessionsTemplateWrapper
import it.pagopa.wallet.repositories.WalletRepository
import it.pagopa.wallet.util.JwtTokenUtils
import it.pagopa.wallet.util.TransactionId
import it.pagopa.wallet.util.UniqueIdUtils
import java.net.URI
import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import java.time.Instant
import java.time.OffsetDateTime
import java.time.YearMonth
import java.time.format.DateTimeFormatter
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
    @Autowired private val applicationRepository: ApplicationRepository,
    @Autowired private val ecommercePaymentMethodsClient: EcommercePaymentMethodsClient,
    @Autowired private val npgClient: NpgClient,
    @Autowired private val npgSessionRedisTemplate: NpgSessionsTemplateWrapper,
    @Autowired private val sessionUrlConfig: SessionUrlConfig,
    @Autowired private val uniqueIdUtils: UniqueIdUtils,
    @Autowired private val onboardingConfig: OnboardingConfig,
    @Autowired private val jwtTokenUtils: JwtTokenUtils,
    @Autowired @Value("\${wallet.payment.cardReturnUrl}") private val walletPaymentReturnUrl: String
) {

    companion object {
        const val CREATE_HOSTED_ORDER_REQUEST_VERSION: String = "2"
        const val CREATE_HOSTED_ORDER_REQUEST_CURRENCY_EUR: String = "EUR"
        const val CREATE_HOSTED_ORDER_REQUEST_VERIFY_AMOUNT: String = "0"
        const val CREATE_HOSTED_ORDER_REQUEST_LANGUAGE_ITA = "ITA"
        val NPG_CARDS_ONBOARDING_ERROR_CODE_MAPPING =
            mapOf(
                "100" to SessionWalletRetrieveResponseDto.OutcomeEnum.NUMBER_2,
                "101" to SessionWalletRetrieveResponseDto.OutcomeEnum.NUMBER_7,
                "102" to SessionWalletRetrieveResponseDto.OutcomeEnum.NUMBER_2,
                "104" to SessionWalletRetrieveResponseDto.OutcomeEnum.NUMBER_3,
                "106" to SessionWalletRetrieveResponseDto.OutcomeEnum.NUMBER_2,
                "109" to SessionWalletRetrieveResponseDto.OutcomeEnum.NUMBER_1,
                "110" to SessionWalletRetrieveResponseDto.OutcomeEnum.NUMBER_3,
                "111" to SessionWalletRetrieveResponseDto.OutcomeEnum.NUMBER_7,
                "115" to SessionWalletRetrieveResponseDto.OutcomeEnum.NUMBER_1,
                "116" to SessionWalletRetrieveResponseDto.OutcomeEnum.NUMBER_2,
                "117" to SessionWalletRetrieveResponseDto.OutcomeEnum.NUMBER_2,
                "118" to SessionWalletRetrieveResponseDto.OutcomeEnum.NUMBER_3,
                "119" to SessionWalletRetrieveResponseDto.OutcomeEnum.NUMBER_2,
                "120" to SessionWalletRetrieveResponseDto.OutcomeEnum.NUMBER_2,
                "121" to SessionWalletRetrieveResponseDto.OutcomeEnum.NUMBER_2,
                "122" to SessionWalletRetrieveResponseDto.OutcomeEnum.NUMBER_2,
                "123" to SessionWalletRetrieveResponseDto.OutcomeEnum.NUMBER_2,
                "124" to SessionWalletRetrieveResponseDto.OutcomeEnum.NUMBER_2,
                "125" to SessionWalletRetrieveResponseDto.OutcomeEnum.NUMBER_3,
                "126" to SessionWalletRetrieveResponseDto.OutcomeEnum.NUMBER_2,
                "129" to SessionWalletRetrieveResponseDto.OutcomeEnum.NUMBER_2,
                "200" to SessionWalletRetrieveResponseDto.OutcomeEnum.NUMBER_2,
                "202" to SessionWalletRetrieveResponseDto.OutcomeEnum.NUMBER_2,
                "204" to SessionWalletRetrieveResponseDto.OutcomeEnum.NUMBER_2,
                "208" to SessionWalletRetrieveResponseDto.OutcomeEnum.NUMBER_3,
                "209" to SessionWalletRetrieveResponseDto.OutcomeEnum.NUMBER_3,
                "210" to SessionWalletRetrieveResponseDto.OutcomeEnum.NUMBER_3,
                "413" to SessionWalletRetrieveResponseDto.OutcomeEnum.NUMBER_2,
                "888" to SessionWalletRetrieveResponseDto.OutcomeEnum.NUMBER_2,
                "902" to SessionWalletRetrieveResponseDto.OutcomeEnum.NUMBER_2,
                "903" to SessionWalletRetrieveResponseDto.OutcomeEnum.NUMBER_2,
                "904" to SessionWalletRetrieveResponseDto.OutcomeEnum.NUMBER_1,
                "906" to SessionWalletRetrieveResponseDto.OutcomeEnum.NUMBER_1,
                "907" to SessionWalletRetrieveResponseDto.OutcomeEnum.NUMBER_1,
                "908" to SessionWalletRetrieveResponseDto.OutcomeEnum.NUMBER_1,
                "909" to SessionWalletRetrieveResponseDto.OutcomeEnum.NUMBER_1,
                "911" to SessionWalletRetrieveResponseDto.OutcomeEnum.NUMBER_1,
                "913" to SessionWalletRetrieveResponseDto.OutcomeEnum.NUMBER_1,
                "999" to SessionWalletRetrieveResponseDto.OutcomeEnum.NUMBER_1,
            )
        val walletExpiryDateFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyyMM")
        val npgExpiryDateFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("MM/yy")
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
        walletApplicationList: List<WalletApplicationId>,
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
        transactionId: TransactionId,
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
                                WalletApplication(
                                    WalletApplicationId(
                                        ServiceNameDto.PAGOPA.value
                                    ), // TODO We enter a static value since these wallets will be
                                    // created only for pagopa payments
                                    WalletApplicationStatus.ENABLED,
                                    creationTime,
                                    creationTime,
                                    WalletApplicationMetadata(
                                        hashMapOf(
                                            Pair(
                                                WalletApplicationMetadata.Metadata
                                                    .PAYMENT_WITH_CONTEXTUAL_ONBOARD
                                                    .value,
                                                "true"
                                            ),
                                            Pair(
                                                WalletApplicationMetadata.Metadata.TRANSACTION_ID
                                                    .value,
                                                transactionId.value().toString()
                                            ),
                                            Pair(
                                                WalletApplicationMetadata.Metadata.AMOUNT.value,
                                                amount.toString()
                                            )
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
                val pagopaApplication =
                    wallet.applications.singleOrNull { application ->
                        application.id == WalletApplicationId(ServiceNameDto.PAGOPA.value) &&
                            application.status == WalletApplicationStatus.ENABLED
                    }
                val isTransactionWithContextualOnboard =
                    isWalletForTransactionWithContextualOnboard(pagopaApplication)
                val orderId = uniqueIds.first
                val amount =
                    if (isTransactionWithContextualOnboard)
                        pagopaApplication
                            ?.metadata
                            ?.data
                            ?.get(WalletApplicationMetadata.Metadata.AMOUNT.value)
                    else null
                val contractId = uniqueIds.second
                val basePath = URI.create(sessionUrlConfig.basePath)
                val merchantUrl = sessionUrlConfig.basePath
                val resultUrl = basePath.resolve(sessionUrlConfig.outcomeSuffix)
                val cancelUrl = basePath.resolve(sessionUrlConfig.cancelSuffix)
                val notificationUrl =
                    buildNotificationUrl(
                        isTransactionWithContextualOnboard,
                        wallet.id.value,
                        orderId,
                        pagopaApplication
                            ?.metadata
                            ?.data
                            ?.get(WalletApplicationMetadata.Metadata.TRANSACTION_ID.value)
                    )

                npgClient
                    .createNpgOrderBuild(
                        UUID.randomUUID(),
                        CreateHostedOrderRequest()
                            .version(CREATE_HOSTED_ORDER_REQUEST_VERSION)
                            .merchantUrl(merchantUrl)
                            .order(
                                Order()
                                    .orderId(orderId)
                                    .amount(
                                        if (isTransactionWithContextualOnboard) amount
                                        else CREATE_HOSTED_ORDER_REQUEST_VERIFY_AMOUNT
                                    )
                                    .currency(CREATE_HOSTED_ORDER_REQUEST_CURRENCY_EUR)
                                // TODO customerId must be valorised with the one coming from
                            )
                            .paymentSession(
                                PaymentSession()
                                    .actionType(
                                        if (isTransactionWithContextualOnboard) ActionType.PAY
                                        else ActionType.VERIFY
                                    )
                                    .recurrence(
                                        RecurringSettings()
                                            .action(RecurringAction.CONTRACT_CREATION)
                                            .contractId(contractId)
                                            .contractType(RecurringContractType.CIT)
                                    )
                                    .amount(
                                        if (isTransactionWithContextualOnboard) amount
                                        else CREATE_HOSTED_ORDER_REQUEST_VERIFY_AMOUNT
                                    )
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
                                LastFourDigits(data.lastFourDigits.orEmpty()),
                                ExpiryDate(gatewayToWalletExpiryDate(data.expiringDate.orEmpty())),
                                WalletCardDetailsDto.BrandEnum.valueOf(data.circuit.orEmpty()),
                                PaymentInstrumentGatewayId("?")
                            )
                    )
            }

    private fun gatewayToWalletExpiryDate(expiryDate: String) =
        YearMonth.parse(expiryDate, npgExpiryDateFormatter).format(walletExpiryDateFormatter)

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
                                    validationErrorCode = walletNotificationRequestDto.errorCode,
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
                                walletNotificationRequestDto.timestampOperation.toString(),
                                walletNotificationRequestDto.errorCode?.toString()
                            )
                        )
                    }
            }
    }

    fun deleteWallet(walletId: WalletId): Mono<LoggedAction<Unit>> =
        walletRepository
            .findById(walletId.value.toString())
            .switchIfEmpty { Mono.error(WalletNotFoundException(walletId)) }
            .flatMap { walletRepository.save(it.copy(status = WalletStatusDto.DELETED.toString())) }
            .map { LoggedAction(Unit, WalletDeletedEvent(walletId.value.toString())) }

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
                    .filter { wallet -> session.walletId == wallet.id }
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
                                if (isFinalStatus) {
                                    retrieveFinalOutcome(
                                        operationResult = wallet.validationOperationResult,
                                        errorCode = wallet.validationErrorCode,
                                        walletDetailType = wallet.details?.type
                                    )
                                } else {
                                    null
                                }
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
            .userId(wallet.userId)
            .updateDate(OffsetDateTime.parse(wallet.updateDate.toString()))
            .creationDate(OffsetDateTime.parse(wallet.creationDate.toString()))
            .services(
                wallet.applications.map { application ->
                    ServiceDto()
                        .name(ServiceNameDto.valueOf(application.id))
                        .status(ApplicationStatusDto.valueOf(application.status))
                }
            )
            .details(toWalletInfoDetailsDto(wallet.details))

    private fun toWalletInfoDetailsDto(details: WalletDetails<*>?): WalletInfoDetailsDto? {
        return when (details) {
            is CardDetails ->
                WalletCardDetailsDto()
                    .type(details.type)
                    .bin(details.bin)
                    .expiryDate(details.expiryDate)
                    .lastFourDigits(details.lastFourDigits)
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
        applicationsToUpdate: List<Pair<WalletApplicationId, WalletApplicationStatus>>
    ): Mono<LoggedAction<WalletServiceUpdateData>> {
        return walletRepository
            .findById(walletId.toString())
            .switchIfEmpty { Mono.error(WalletNotFoundException(WalletId(walletId))) }
            .flatMap { wallet ->
                val walletApplications =
                    wallet.applications.associateBy { WalletApplicationId(it.id) }.toMap()

                applicationsToUpdate
                    .toFlux()
                    .flatMap { (applicationId, requestedStatus) ->
                        applicationRepository
                            .findById(applicationId.id)
                            .map { Triple(it, applicationId, requestedStatus) }
                            .switchIfEmpty(
                                Mono.error(ApplicationNotFoundException(applicationId.id))
                            )
                    }
                    .reduce(
                        Triple(
                            mutableMapOf<WalletApplicationId, WalletApplicationStatus>(),
                            mutableMapOf<WalletApplicationId, WalletApplicationStatus>(),
                            walletApplications.toMutableMap()
                        )
                    ) {
                        (
                            servicesUpdatedSuccessfully,
                            servicesWithUpdateFailed,
                            updatedApplications),
                        (application, applicationId, requestedStatus) ->
                        val serviceGlobalStatus =
                            WalletApplicationStatus.valueOf(application.status)
                        val walletApplication = walletApplications[applicationId]

                        if (
                            WalletApplicationStatus.canChangeToStatus(
                                requested = requestedStatus,
                                global = serviceGlobalStatus
                            )
                        ) {
                            updatedApplications[applicationId] =
                                walletApplication?.copy(
                                    updateDate = Instant.now().toString(),
                                    status = requestedStatus.name
                                )
                                    ?: it.pagopa.wallet.documents.wallets.WalletApplication(
                                        id = applicationId.id,
                                        status = requestedStatus.name,
                                        creationDate = Instant.now().toString(),
                                        updateDate = Instant.now().toString(),
                                        metadata = hashMapOf()
                                    )
                            servicesUpdatedSuccessfully[applicationId] = requestedStatus
                        } else {
                            servicesWithUpdateFailed[applicationId] = serviceGlobalStatus
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
     * @param errorCode the optional error code returned by NPG during onboarding status
     *   notification
     * @return Mono<SessionWalletRetrieveResponseDto.OutcomeEnum>
     */
    private fun retrieveFinalOutcome(
        operationResult: WalletNotificationRequestDto.OperationResultEnum?,
        errorCode: String?,
        walletDetailType: WalletDetailsType?
    ): SessionWalletRetrieveResponseDto.OutcomeEnum {
        val outcome =
            when (operationResult) {
                WalletNotificationRequestDto.OperationResultEnum.EXECUTED ->
                    SessionWalletRetrieveResponseDto.OutcomeEnum.NUMBER_0
                WalletNotificationRequestDto.OperationResultEnum.AUTHORIZED ->
                    SessionWalletRetrieveResponseDto.OutcomeEnum.NUMBER_1
                WalletNotificationRequestDto.OperationResultEnum.DECLINED ->
                    if (walletDetailType == WalletDetailsType.CARDS) {
                        decodeCardsOnboardingNpgErrorCode(errorCode)
                    } else {
                        SessionWalletRetrieveResponseDto.OutcomeEnum.NUMBER_2
                    }
                WalletNotificationRequestDto.OperationResultEnum.DENIED_BY_RISK ->
                    SessionWalletRetrieveResponseDto.OutcomeEnum.NUMBER_2
                WalletNotificationRequestDto.OperationResultEnum.THREEDS_VALIDATED ->
                    SessionWalletRetrieveResponseDto.OutcomeEnum.NUMBER_2
                WalletNotificationRequestDto.OperationResultEnum.THREEDS_FAILED ->
                    SessionWalletRetrieveResponseDto.OutcomeEnum.NUMBER_2
                WalletNotificationRequestDto.OperationResultEnum.PENDING ->
                    SessionWalletRetrieveResponseDto.OutcomeEnum.NUMBER_1
                WalletNotificationRequestDto.OperationResultEnum.CANCELED ->
                    SessionWalletRetrieveResponseDto.OutcomeEnum.NUMBER_8
                WalletNotificationRequestDto.OperationResultEnum.VOIDED ->
                    SessionWalletRetrieveResponseDto.OutcomeEnum.NUMBER_1
                WalletNotificationRequestDto.OperationResultEnum.REFUNDED ->
                    SessionWalletRetrieveResponseDto.OutcomeEnum.NUMBER_1
                WalletNotificationRequestDto.OperationResultEnum.FAILED ->
                    SessionWalletRetrieveResponseDto.OutcomeEnum.NUMBER_1
                null -> SessionWalletRetrieveResponseDto.OutcomeEnum.NUMBER_1
            }
        logger.info(
            "Npg notification gateway status: [{}], errorCode: [{}] for wallet type: [{}] decoded as IO outcome: [{}]",
            operationResult,
            errorCode,
            walletDetailType,
            outcome
        )
        return outcome
    }

    private fun decodeCardsOnboardingNpgErrorCode(errorCode: String?) =
        if (errorCode != null) {
            NPG_CARDS_ONBOARDING_ERROR_CODE_MAPPING.getOrDefault(
                errorCode,
                SessionWalletRetrieveResponseDto.OutcomeEnum.NUMBER_1
            )
        } else {
            SessionWalletRetrieveResponseDto.OutcomeEnum.NUMBER_1
        }

    private fun isWalletForTransactionWithContextualOnboard(
        application: it.pagopa.wallet.domain.wallets.WalletApplication?
    ): Boolean {
        if (application != null) {
            return application.metadata.data[
                    WalletApplicationMetadata.Metadata.PAYMENT_WITH_CONTEXTUAL_ONBOARD.value]
                .toBoolean()
        }
        return false
    }

    private fun buildNotificationUrl(
        isTransactionWithContextualOnboard: Boolean,
        walletId: UUID,
        orderId: String,
        transactionId: String?
    ): URI {
        return if (!isTransactionWithContextualOnboard) {
            UriComponentsBuilder.fromHttpUrl(sessionUrlConfig.notificationUrl)
                .build(mapOf(Pair("walletId", walletId), Pair("orderId", orderId)))
        } else {
            UriComponentsBuilder.fromHttpUrl(
                    sessionUrlConfig.trxWithContextualOnboardNotificationUrl
                )
                .build(
                    mapOf(
                        Pair("transactionId", transactionId),
                        Pair("walletId", walletId),
                        Pair("orderId", orderId),
                        Pair(
                            "sessionToken",
                            transactionId?.let {
                                jwtTokenUtils
                                    .generateJwtTokenForNpgNotifications(
                                        walletId.toString(),
                                        transactionId
                                    )
                                    .fold({ throw it }, { token -> token })
                            }
                        )
                    )
                )
        }
    }
}
