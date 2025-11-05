package it.pagopa.wallet.services

import it.pagopa.generated.jwtIssuer.model.CreateTokenRequest
import it.pagopa.generated.npg.model.*
import it.pagopa.generated.wallet.model.*
import it.pagopa.wallet.audit.*
import it.pagopa.wallet.client.JwtTokenIssuerClient
import it.pagopa.wallet.client.NpgClient
import it.pagopa.wallet.client.PspDetailClient
import it.pagopa.wallet.config.OnboardingConfig
import it.pagopa.wallet.config.SessionUrlConfig
import it.pagopa.wallet.documents.wallets.details.CardDetails
import it.pagopa.wallet.documents.wallets.details.PayPalDetails as PayPalDetailsDocument
import it.pagopa.wallet.documents.wallets.details.WalletDetails
import it.pagopa.wallet.domain.applications.ApplicationStatus
import it.pagopa.wallet.domain.wallets.*
import it.pagopa.wallet.domain.wallets.details.*
import it.pagopa.wallet.domain.wallets.details.CardDetails as DomainCardDetails
import it.pagopa.wallet.exception.*
import it.pagopa.wallet.repositories.*
import it.pagopa.wallet.util.Constants
import it.pagopa.wallet.util.EitherExtension.toMono
import it.pagopa.wallet.util.TransactionId
import it.pagopa.wallet.util.UniqueIdUtils
import it.pagopa.wallet.util.WalletUtils
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
    @Autowired private val paymentMethodsService: PaymentMethodsService,
    @Autowired private val npgClient: NpgClient,
    @Autowired private val npgSessionRedisTemplate: NpgSessionsTemplateWrapper,
    @Autowired
    private val walletJwtTokenCtxOnboardingTemplateWrapper:
        WalletJwtTokenCtxOnboardingTemplateWrapper,
    @Autowired private val sessionUrlConfig: SessionUrlConfig,
    @Autowired private val uniqueIdUtils: UniqueIdUtils,
    @Autowired private val onboardingConfig: OnboardingConfig,
    @Autowired private val jwtTokenIssuerClient: JwtTokenIssuerClient,
    @Autowired
    @Value("\${wallet.payment.cardReturnUrl}")
    private val walletPaymentReturnUrl: String,
    @Autowired private val walletUtils: WalletUtils,
    private val pspDetailClient: PspDetailClient,
    @Value("\${npg.notifications.jwt.validityTimeSeconds}")
    private val tokenValidityTimeSeconds: Int
) {
    companion object {
        /** The claim transactionId */
        const val TRANSACTION_ID_CLAIM = "transactionId"

        /** The claim walletId */
        const val WALLET_ID_CLAIM = "walletId"

        /** Nog audience * */
        const val NPG_AUDIENCE = "npg"

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
                "109" to SessionWalletRetrieveResponseDto.OutcomeEnum.NUMBER_25,
                "110" to SessionWalletRetrieveResponseDto.OutcomeEnum.NUMBER_3,
                "111" to SessionWalletRetrieveResponseDto.OutcomeEnum.NUMBER_7,
                "115" to SessionWalletRetrieveResponseDto.OutcomeEnum.NUMBER_25,
                "116" to SessionWalletRetrieveResponseDto.OutcomeEnum.NUMBER_116,
                "117" to SessionWalletRetrieveResponseDto.OutcomeEnum.NUMBER_117,
                "118" to SessionWalletRetrieveResponseDto.OutcomeEnum.NUMBER_3,
                "119" to SessionWalletRetrieveResponseDto.OutcomeEnum.NUMBER_2,
                "120" to SessionWalletRetrieveResponseDto.OutcomeEnum.NUMBER_2,
                "121" to SessionWalletRetrieveResponseDto.OutcomeEnum.NUMBER_121,
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
                "904" to SessionWalletRetrieveResponseDto.OutcomeEnum.NUMBER_25,
                "906" to SessionWalletRetrieveResponseDto.OutcomeEnum.NUMBER_25,
                "907" to SessionWalletRetrieveResponseDto.OutcomeEnum.NUMBER_25,
                "908" to SessionWalletRetrieveResponseDto.OutcomeEnum.NUMBER_25,
                "909" to SessionWalletRetrieveResponseDto.OutcomeEnum.NUMBER_25,
                "911" to SessionWalletRetrieveResponseDto.OutcomeEnum.NUMBER_25,
                "913" to SessionWalletRetrieveResponseDto.OutcomeEnum.NUMBER_25,
                "999" to SessionWalletRetrieveResponseDto.OutcomeEnum.NUMBER_25,
            )
        val walletExpiryDateFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyyMM")
        val npgExpiryDateFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("MM/yy")
        val pagopaWalletApplicationId = WalletApplicationId("PAGOPA")
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
        paymentMethodId: UUID,
        onboardingChannel: OnboardingChannel
    ): Mono<Pair<LoggedAction<Wallet>, URI>> {
        logger.info("Create wallet with payment methodId: $paymentMethodId and userId: $userId")

        return walletApplicationList
            .toFlux()
            .flatMap { requestedApplication ->
                applicationRepository
                    .findById(requestedApplication.id)
                    .switchIfEmpty(
                        Mono.error(ApplicationNotFoundException(requestedApplication.id)))
            }
            .map { application ->
                WalletApplication(
                    WalletApplicationId(application.id),
                    parseWalletApplicationStatus(ApplicationStatus.valueOf(application.status)),
                    Instant.now(),
                    Instant.now(),
                    WalletApplicationMetadata.empty())
            }
            .collectList()
            .flatMap { apps ->
                paymentMethodsService.getPaymentMethodById(paymentMethodId.toString()).map {
                    val creationTime = Instant.now()
                    return@map Pair(
                        Wallet(
                            id = WalletId(UUID.randomUUID()),
                            userId = UserId(userId),
                            status = WalletStatusDto.CREATED,
                            paymentMethodId = PaymentMethodId(paymentMethodId),
                            applications = apps,
                            version = 0,
                            clients =
                                Client.WellKnown.values().associateWith { _ ->
                                    Client(Client.Status.ENABLED)
                                },
                            creationDate = creationTime,
                            updateDate = creationTime,
                            onboardingChannel = onboardingChannel),
                        it)
                }
            }
            .flatMap { (wallet, paymentMethodResponse) ->
                walletRepository
                    .save(wallet.toDocument())
                    .map { LoggedAction(wallet, WalletAddedEvent(wallet.id.value.toString())) }
                    .map { loggedAction ->
                        Pair(
                            loggedAction,
                            paymentMethodResponse.paymentTypeCode.let {
                                /*
                                 * Safe not null operator here since EcommercePaymentMethodsClient already perform a check
                                 * against returned payment method name and WalletDetailsType enumeration
                                 */
                                when (WalletDetailsType.fromPaymentTypeCode(it)) {
                                    WalletDetailsType.CARDS -> onboardingConfig.cardReturnUrl
                                    WalletDetailsType.PAYPAL -> onboardingConfig.apmReturnUrl
                                    else ->
                                        throw RuntimeException("Unhandled payment type code: [$it]")
                                }
                            })
                    }
            }
    }

    private fun parseWalletApplicationStatus(status: ApplicationStatus) =
        when (status) {
            ApplicationStatus.ENABLED -> WalletApplicationStatus.ENABLED
            else -> WalletApplicationStatus.DISABLED
        }

    fun createWalletForTransaction(
        userId: UUID,
        paymentMethodId: UUID,
        transactionId: TransactionId,
        amount: Int,
        onboardingChannel: OnboardingChannel,
        ecommerceSessionToken: String
    ): Mono<Pair<LoggedAction<Wallet>, Optional<URI>>> {
        logger.info(
            "Create wallet for transaction with contextual onboard for payment methodId: $paymentMethodId userId: $userId and transactionId: $transactionId")
        val creationTime = Instant.now()
        val applicationIdForPayments = "PAGOPA"
        return applicationRepository
            .findById(applicationIdForPayments)
            .switchIfEmpty(Mono.error(ApplicationNotFoundException(applicationIdForPayments)))
            .map { application ->
                WalletApplication(
                    WalletApplicationId(
                        application
                            .id), // We enter a static value since these wallets will be created
                    // only for
                    // pagopa payments
                    parseWalletApplicationStatus(ApplicationStatus.valueOf(application.status)),
                    creationTime,
                    creationTime,
                    WalletApplicationMetadata(
                        hashMapOf(
                            Pair(
                                WalletApplicationMetadata.Metadata.PAYMENT_WITH_CONTEXTUAL_ONBOARD,
                                "true"),
                            Pair(
                                WalletApplicationMetadata.Metadata.TRANSACTION_ID,
                                transactionId.trimmedUUIDString),
                            Pair(WalletApplicationMetadata.Metadata.AMOUNT, amount.toString()))))
            }
            .flatMap { walletApplication ->
                paymentMethodsService.getPaymentMethodById(paymentMethodId.toString()).map {
                    return@map Pair(
                        Wallet(
                            id = WalletId(UUID.randomUUID()),
                            userId = UserId(userId),
                            status = WalletStatusDto.CREATED,
                            paymentMethodId = PaymentMethodId(paymentMethodId),
                            version = 0,
                            creationDate = creationTime,
                            updateDate = creationTime,
                            applications = listOf(walletApplication),
                            clients =
                                Client.WellKnown.values().associateWith { _ ->
                                    Client(Client.Status.ENABLED)
                                },
                            onboardingChannel = onboardingChannel),
                        it)
                }
            }
            .flatMap { (wallet, paymentMethodResponse) ->
                walletRepository
                    .save(wallet.toDocument())
                    .map { wallet ->
                        walletJwtTokenCtxOnboardingTemplateWrapper.save(
                            WalletJwtTokenCtxOnboardingDocument(wallet.id, ecommerceSessionToken))
                    }
                    .map { LoggedAction(wallet, WalletAddedEvent(wallet.id.value.toString())) }
                    .map { loggedAction ->
                        Pair(
                            loggedAction,
                            paymentMethodResponse.paymentTypeCode.let {
                                when (WalletDetailsType.fromPaymentTypeCode(it)) {
                                    WalletDetailsType.CARDS ->
                                        Optional.of(URI.create(walletPaymentReturnUrl))

                                    else -> {
                                        Optional.empty()
                                    }
                                }
                            })
                    }
            }
    }

    fun createSessionWallet(
        xUserId: UserId,
        walletId: WalletId,
        sessionInputDataDto: SessionInputDataDto
    ): Mono<Pair<SessionWalletCreateResponseDto, LoggedAction<Wallet>>> {
        logger.info("Create session for walletId: $walletId")
        return walletRepository
            .findByIdAndUserId(walletId.value.toString(), xUserId.id.toString())
            .switchIfEmpty { Mono.error(WalletNotFoundException(walletId)) }
            .map { it.toDomain() }
            .flatMap {
                paymentMethodsService
                    .getPaymentMethodById(it.paymentMethodId.value.toString())
                    .map { paymentMethod -> paymentMethod to it }
            }
            .flatMap { (paymentMethod, wallet) ->
                val isTransactionWithContextualOnboard =
                    wallet
                        .getApplicationMetadata(
                            pagopaWalletApplicationId,
                            WalletApplicationMetadata.Metadata.PAYMENT_WITH_CONTEXTUAL_ONBOARD)
                        .toBoolean()
                val isApm = paymentMethod.paymentTypeCode != "CP"
                val allowedStatusesForRetryCreate = setOf(WalletStatusDto.CREATED)
                // CHK-3294 hotfix step 1 - avoid retry for order/build given a walletId
                // if (isTransactionWithContextualOnboard) {
                // allow for retry on createSessionWallet only for onboarding
                //                      setOf(WalletStatusDto.CREATED)
                //  } else {
                // if (isApm) {
                //  setOf(WalletStatusDto.CREATED, WalletStatusDto.VALIDATION_REQUESTED)
                //       } else {
                //  setOf(WalletStatusDto.CREATED, WalletStatusDto.INITIALIZED)
                //       }
                //   }
                logger.info(
                    "Wallet: [${walletId.value}], isApm: [$isApm], isTransactionWithContextualOnboard: [$isTransactionWithContextualOnboard] -> allowed statuses for retry create -> $allowedStatusesForRetryCreate")
                Mono.just(paymentMethod)
                    .zipWith(
                        wallet
                            .expectInStatus(*allowedStatusesForRetryCreate.toTypedArray())
                            .toMono())
                    .map { Pair(it.t1, it.t2) }
            }
            .flatMap { (paymentMethodResponse, wallet) ->
                generateNPGUniqueIdentifiers().map { (orderId, contractId) ->
                    Triple(Pair(orderId, contractId), paymentMethodResponse, wallet)
                }
            }
            .flatMap { (uniqueIds, paymentMethod, wallet) ->
                buildOutcomeUrl(wallet).map { outcomeUrls ->
                    Triple(
                        uniqueIds,
                        paymentMethod,
                        Triple(wallet, outcomeUrls.first, outcomeUrls.second))
                }
            }
            .flatMap { (uniqueIds, paymentMethod, walletData) ->
                val wallet = walletData.first
                val resultUrl = walletData.second
                val cancelUrl = walletData.third

                val isTransactionWithContextualOnboard =
                    wallet
                        .getApplicationMetadata(
                            pagopaWalletApplicationId,
                            WalletApplicationMetadata.Metadata.PAYMENT_WITH_CONTEXTUAL_ONBOARD)
                        .toBoolean()
                val orderId = uniqueIds.first
                val amount =
                    if (isTransactionWithContextualOnboard) {
                        wallet.getApplicationMetadata(
                            pagopaWalletApplicationId, WalletApplicationMetadata.Metadata.AMOUNT)
                    } else {
                        null
                    }
                val contractId = uniqueIds.second
                val merchantUrl = sessionUrlConfig.basePath
                logger.info(
                    "About to create session for wallet: [${walletId.value}] with orderId: [${orderId}]")
                createTokenAndBuildNotificationUri(
                        isTransactionWithContextualOnboard,
                        walletId.value,
                        orderId,
                        wallet.getApplicationMetadata(
                            pagopaWalletApplicationId,
                            WalletApplicationMetadata.Metadata.TRANSACTION_ID))
                    .flatMap { notificationUrl ->
                        npgClient.createNpgOrderBuild(
                            correlationId = walletId.value,
                            createHostedOrderRequest =
                                CreateHostedOrderRequest()
                                    .version(CREATE_HOSTED_ORDER_REQUEST_VERSION)
                                    .merchantUrl(merchantUrl)
                                    .order(
                                        Order()
                                            .orderId(orderId)
                                            .amount(
                                                if (isTransactionWithContextualOnboard) {
                                                    amount
                                                } else {
                                                    CREATE_HOSTED_ORDER_REQUEST_VERIFY_AMOUNT
                                                })
                                            .currency(CREATE_HOSTED_ORDER_REQUEST_CURRENCY_EUR)
                                        // TODO customerId must be valorised with the one coming
                                        // from
                                        )
                                    .paymentSession(
                                        PaymentSession()
                                            .actionType(
                                                if (isTransactionWithContextualOnboard) {
                                                    ActionType.PAY
                                                } else {
                                                    ActionType.VERIFY
                                                })
                                            .recurrence(
                                                RecurringSettings()
                                                    .action(RecurringAction.CONTRACT_CREATION)
                                                    .contractId(contractId)
                                                    .contractType(RecurringContractType.CIT))
                                            .amount(
                                                if (isTransactionWithContextualOnboard) {
                                                    amount
                                                } else {
                                                    CREATE_HOSTED_ORDER_REQUEST_VERIFY_AMOUNT
                                                })
                                            .language(CREATE_HOSTED_ORDER_REQUEST_LANGUAGE_ITA)
                                            .captureType(CaptureType.IMPLICIT)
                                            .paymentService(
                                                WalletDetailsType.fromPaymentTypeCode(
                                                        paymentMethod.paymentTypeCode)!!
                                                    .toString())
                                            .resultUrl(resultUrl.toString())
                                            .cancelUrl(cancelUrl.toString())
                                            .notificationUrl(notificationUrl.toString())),
                            pspId =
                                when (sessionInputDataDto) {
                                    is SessionInputCardDataDto -> null
                                    is SessionInputPayPalDataDto -> sessionInputDataDto.pspId
                                    else ->
                                        throw InternalServerErrorException(
                                            "Unhandled session input")
                                })
                    }
                    .flatMap { hostedOrderResponse ->
                        val isAPM = paymentMethod.paymentTypeCode != "CP"
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

                        val newDetails =
                            when (sessionInputDataDto) {
                                    is SessionInputCardDataDto -> wallet.details.toMono()
                                    is SessionInputPayPalDataDto ->
                                        createPaypalDetails(
                                            sessionInputDataDto, wallet.paymentMethodId)

                                    else ->
                                        Mono.error(
                                            InternalServerErrorException("Unhandled session input"))
                                }
                                .map { Optional.of(it) }
                                .defaultIfEmpty(Optional.empty())

                        newDetails.map {
                            SessionCreationData(
                                hostedOrderResponse,
                                wallet.copy(
                                    contractId = ContractId(contractId),
                                    status = newStatus,
                                    details = it.orElse(null),
                                    applications =
                                        if (isTransactionWithContextualOnboard) {
                                            updateMetadataWithContextualOnboardDetails(
                                                wallet, hostedOrderResponse.sessionId, orderId)
                                        } else {
                                            wallet.applications
                                        }),
                                orderId,
                                isAPM = isAPM)
                        }
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
                            hostedOrderResponse.sessionId!!,
                            hostedOrderResponse.securityToken.toString(),
                            wallet.id.value.toString()))
                    .map {
                        SessionWalletCreateResponseDto()
                            .orderId(orderId)
                            .sessionData(buildResponseSessionData(hostedOrderResponse, isAPM))
                    }
                    .map { Triple(it, wallet, orderId) } // Include orderId in the Triple
            }
            .map { (sessionResponseDto, wallet, orderId) ->
                sessionResponseDto to
                    LoggedAction(
                        wallet,
                        SessionWalletCreatedEvent(
                            walletId = wallet.id.value.toString(),
                            auditWallet = AuditWalletCreated(orderId = orderId)))
            }
    }

    private fun updateMetadataWithContextualOnboardDetails(
        wallet: Wallet,
        sessionId: String?,
        orderId: String
    ): List<WalletApplication> {
        return wallet.applications.map { application ->
            val newMetadataMap =
                application.metadata.data +
                    mapOf(
                        WalletApplicationMetadata.Metadata.SESSION_ID to sessionId,
                        WalletApplicationMetadata.Metadata.ORDER_ID to orderId)
            application.copy(metadata = application.metadata.copy(data = newMetadataMap))
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
                    HttpStatus.BAD_GATEWAY)
            }

            SessionWalletCreateResponseAPMDataDto()
                .redirectUrl(hostedOrderResponse.url)
                .paymentMethodType("apm")
        } else {
            val cardFields = hostedOrderResponse.fields!!
            if (cardFields.isEmpty()) {
                throw NpgClientException(
                    "Received empty fields array in orders/build call to NPG!",
                    HttpStatus.BAD_GATEWAY)
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
                        .toList())
        }

    fun validateWalletSession(
        orderId: String,
        walletId: WalletId,
        userId: UserId
    ): Mono<Pair<WalletVerifyRequestsResponseDto, LoggedAction<Wallet>>> {
        return npgSessionRedisTemplate
            .findById(orderId)
            .switchIfEmpty { Mono.error(SessionNotFoundException(orderId)) }
            .flatMap { session ->
                walletRepository
                    .findByIdAndUserId(walletId.value.toString(), userId.id.toString())
                    .switchIfEmpty { Mono.error(WalletNotFoundException(walletId)) }
                    .filter { session.walletId == it.id }
                    .switchIfEmpty {
                        Mono.error(WalletSessionMismatchException(session.sessionId, walletId))
                    }
                    .flatMap { it.toDomain().expectInStatus(WalletStatusDto.INITIALIZED).toMono() }
                    .flatMap { wallet ->
                        paymentMethodsService
                            .getPaymentMethodById(wallet.paymentMethodId.value.toString())
                            .flatMap {
                                when (it.paymentTypeCode) {
                                    "CP" ->
                                        handleCardPayment(
                                            session.sessionId, walletId.value, orderId, wallet)
                                    else -> throw NoCardsSessionValidateRequestException(walletId)
                                }
                            }
                    }
                    .flatMap { (response, wallet) ->
                        walletRepository.save(wallet.toDocument()).map {
                            response to
                                LoggedAction(
                                    wallet, WalletDetailsAddedEvent(walletId.value.toString()))
                        }
                    }
            }
    }

    private fun handleCardPayment(
        sessionId: String,
        walletId: UUID,
        orderId: String,
        wallet: Wallet
    ): Mono<Pair<WalletVerifyRequestsResponseDto, Wallet>> {
        val isTransactionWithContextualOnboard =
            wallet
                .getApplicationMetadata(
                    pagopaWalletApplicationId,
                    WalletApplicationMetadata.Metadata.PAYMENT_WITH_CONTEXTUAL_ONBOARD)
                .toBoolean()

        return if (isTransactionWithContextualOnboard) {
            validateCardForContextualOnboard(sessionId, walletId, orderId, wallet)
        } else {
            confirmPaymentCard(sessionId, walletId, orderId, wallet)
        }
    }

    private fun validateCardForContextualOnboard(
        sessionId: String,
        correlationId: UUID,
        orderId: String,
        wallet: Wallet
    ): Mono<Pair<WalletVerifyRequestsResponseDto, Wallet>> =
        npgClient
            .getCardData(URLDecoder.decode(sessionId, StandardCharsets.UTF_8), correlationId)
            .doOnNext { logger.info("Validating wallet for contextual card onboard") }
            .map { authData ->
                authData to
                    WalletVerifyRequestsResponseDto()
                        .orderId(orderId)
                        .details(WalletVerifyRequestContextualCardDetailsDto().type("CARD_CTX"))
            }
            .map { (data, response) ->
                response to
                    wallet.copy(
                        status = WalletStatusDto.VALIDATION_REQUESTED,
                        details =
                            DomainCardDetails(
                                Bin(data.bin.orEmpty()),
                                LastFourDigits(data.lastFourDigits.orEmpty()),
                                ExpiryDate(gatewayToWalletExpiryDate(data.expiringDate.orEmpty())),
                                CardBrand(data.circuit.orEmpty()),
                                PaymentInstrumentGatewayId("?")))
            }

    private fun confirmPaymentCard(
        sessionId: String,
        correlationId: UUID,
        orderId: String,
        wallet: Wallet
    ): Mono<Pair<WalletVerifyRequestsResponseDto, Wallet>> =
        npgClient
            .getCardData(URLDecoder.decode(sessionId, StandardCharsets.UTF_8), correlationId)
            .flatMap {
                npgClient
                    .confirmPayment(
                        ConfirmPaymentRequest().sessionId(sessionId).amount("0"), correlationId)
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
                                                    .toByteArray(StandardCharsets.UTF_8))))
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
                                CardBrand(data.circuit.orEmpty()),
                                PaymentInstrumentGatewayId("?")))
            }

    private fun gatewayToWalletExpiryDate(expiryDate: String) =
        YearMonth.parse(expiryDate, npgExpiryDateFormatter).format(walletExpiryDateFormatter)

    fun findWallet(walletId: UUID, userId: UUID): Mono<WalletInfoDto> {
        return walletRepository
            .findByIdAndUserId(walletId.toString(), userId.toString())
            .switchIfEmpty { Mono.error(WalletNotFoundException(WalletId(walletId))) }
            .map { wallet -> toWalletInfoDto(wallet) }
    }

    fun findWalletByUserId(userId: UUID): Mono<WalletsDto> {
        return walletRepository
            .findByUserIdAndStatus(userId.toString(), WalletStatusDto.VALIDATED)
            .collectList()
            .map { toWallets(it) }
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
        securityToken: String?,
        walletNotificationRequestDto: WalletNotificationRequestDto
    ): Mono<LoggedAction<Wallet>> {
        return npgSessionRedisTemplate
            .findById(orderId)
            .switchIfEmpty { Mono.error(SessionNotFoundException(orderId)) }
            .filter { session ->
                // perform security token check only if input security token have been received
                // correctly
                if (securityToken != null) {
                    session.securityToken == securityToken
                } else {
                    true
                }
            }
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
            }
            .map { walletDocument -> walletDocument.toDomain() }
            .flatMap { it.expectInStatus(WalletStatusDto.VALIDATION_REQUESTED).toMono() }
            .flatMap { wallet ->
                val paymentInstrumentGatewayId =
                    getPaymentInstrumentGatewayId(walletNotificationRequestDto)
                mono { walletNotificationRequestDto.operationResult }
                    .flatMap {
                        if (it == WalletNotificationRequestDto.OperationResultEnum.EXECUTED) {
                            isWalletAlreadyOnboardedForUserId(
                                walletId = wallet.id,
                                userId = wallet.userId,
                                walletDetails = wallet.details,
                                paymentInstrumentGatewayId = paymentInstrumentGatewayId)
                        } else {
                            logger.debug(
                                "Wallet onboarding operation result: [{}], no need to check if wallet was already onboarded",
                                it)
                            mono { false }
                        }
                    }
                    .map { Triple(wallet, it, paymentInstrumentGatewayId) }
            }
            .flatMap { (wallet, isWalletAlreadyOnboarded, paymentInstrumentGatewayId) ->
                val previousStatus = wallet.status
                val walletNotificationProcessingResult =
                    if (isWalletAlreadyOnboarded) {
                        logger.warn(
                            "Wallet already onboarded for userId [{}] and walletId [{}] - [{}], Updating from status: [{}] to [{}]",
                            wallet.userId,
                            walletId,
                            Constants.WALLET_ALREADY_ONBOARDED_FOR_USER_ERROR_CODE,
                            previousStatus,
                            WalletStatusDto.ERROR)
                        val walletDetails = wallet.details
                        val updatedWalletDetails =
                            if (walletDetails
                                is it.pagopa.wallet.domain.wallets.details.CardDetails) {
                                paymentInstrumentGatewayId?.let {
                                    walletDetails.copy(
                                        paymentInstrumentGatewayId = PaymentInstrumentGatewayId(it))
                                }
                            } else {
                                walletDetails
                            }
                        WalletNotificationProcessingResult(
                            walletDetails = updatedWalletDetails,
                            newWalletStatus = WalletStatusDto.ERROR,
                            errorCode = Constants.WALLET_ALREADY_ONBOARDED_FOR_USER_ERROR_CODE)
                    } else {

                        handleWalletNotification(
                            wallet = wallet,
                            walletNotificationRequestDto = walletNotificationRequestDto)
                    }
                val (newWalletStatus, newWalletDetails, errorCode) =
                    walletNotificationProcessingResult
                logger.debug(
                    "Updating from status: [{}] to [{}] for wallet with id: [{}]",
                    previousStatus,
                    newWalletStatus,
                    wallet.id.value)
                walletRepository.save(
                    wallet
                        .copy(
                            status = newWalletStatus,
                            validationOperationResult =
                                walletNotificationRequestDto.operationResult,
                            validationErrorCode = errorCode,
                            details = newWalletDetails)
                        .toDocument())
            }
            .map { wallet ->
                val domainWallet = wallet.toDomain()
                LoggedAction(
                    domainWallet,
                    WalletOnboardCompletedEvent(
                        walletId = domainWallet.id.value.toString(),
                        auditWallet =
                            domainWallet.let {
                                val auditWallet = domainWallet.toAudit()
                                auditWallet.validationOperationId =
                                    walletNotificationRequestDto.operationId
                                auditWallet.validationOperationTimestamp =
                                    walletNotificationRequestDto.timestampOperation.toString()
                                return@let auditWallet
                            }))
            }
    }

    private fun getPaymentInstrumentGatewayId(
        walletNotificationRequestDto: WalletNotificationRequestDto
    ): String? =
        when (val detail = walletNotificationRequestDto.details) {
            is WalletNotificationRequestCardDetailsDto -> detail.paymentInstrumentGatewayId
            else -> null
        }

    fun deleteWallet(walletId: WalletId, userId: UserId): Mono<LoggedAction<Unit>> =
        walletRepository
            .findByIdAndUserId(walletId.value.toString(), userId.id.toString())
            .switchIfEmpty { Mono.error(WalletNotFoundException(walletId)) }
            .filter { it.status != WalletStatusDto.DELETED.toString() }
            .switchIfEmpty { Mono.error(WalletAlreadyDeletedException(walletId)) }
            .flatMap { walletRepository.save(it.copy(status = WalletStatusDto.DELETED.toString())) }
            .map { LoggedAction(Unit, WalletDeletedEvent(walletId.value.toString())) }

    fun patchWalletStateToError(
        walletId: WalletId,
        reason: String?
    ): Mono<it.pagopa.wallet.documents.wallets.Wallet> {
        logger.info("Patching wallet state to error for [{}]", walletId.value.toString())
        return walletRepository
            .findById(walletId.value.toString())
            .switchIfEmpty { Mono.error(WalletNotFoundException(walletId)) }
            .map { it.toDomain() }
            .flatMap {
                if (it.isTransientStatus()) {
                    logger.info(
                        "Patching Wallet [{}] in error state with reason: [{}]",
                        walletId.value.toString(),
                        reason)
                    walletRepository.save(it.error(reason).toDocument())
                } else {
                    logger.info(
                        "Wallet [{}] already in the final state {}",
                        walletId.value.toString(),
                        it.status.value)
                    Mono.just(it.toDocument())
                }
            }
            .doOnError {
                logger.error("Failed to patch wallet state for [${walletId.value.toString()}]", it)
            }
    }

    private fun handleWalletNotification(
        wallet: Wallet,
        walletNotificationRequestDto: WalletNotificationRequestDto
    ): WalletNotificationProcessingResult {
        val operationResult = walletNotificationRequestDto.operationResult
        val operationDetails = walletNotificationRequestDto.details
        logger.info(
            "Received wallet notification request for wallet with id: [{}]. Outcome: [{}], notification details: [{}]",
            wallet.id.value,
            operationResult,
            operationDetails)
        return when (val walletDetails = wallet.details) {
            is it.pagopa.wallet.domain.wallets.details.CardDetails ->
                if (operationDetails is WalletNotificationRequestCardDetailsDto) {
                    if (operationResult ==
                        WalletNotificationRequestDto.OperationResultEnum.EXECUTED) {

                        WalletNotificationProcessingResult(
                            newWalletStatus = WalletStatusDto.VALIDATED,
                            walletDetails =
                                walletDetails.copy(
                                    paymentInstrumentGatewayId =
                                        PaymentInstrumentGatewayId(
                                            operationDetails.paymentInstrumentGatewayId)),
                            errorCode = walletNotificationRequestDto.errorCode)
                    } else {
                        WalletNotificationProcessingResult(
                            newWalletStatus = WalletStatusDto.ERROR,
                            walletDetails =
                                if (operationDetails.paymentInstrumentGatewayId != null) {
                                    walletDetails.copy(
                                        paymentInstrumentGatewayId =
                                            PaymentInstrumentGatewayId(
                                                operationDetails.paymentInstrumentGatewayId))
                                } else {
                                    walletDetails
                                },
                            errorCode = walletNotificationRequestDto.errorCode,
                        )
                    }
                } else {
                    logger.error(
                        "No details received for Card wallet, cannot retrieve paymentInstrumentGatewayId for [${wallet.id.value}]")
                    WalletNotificationProcessingResult(
                        newWalletStatus = WalletStatusDto.ERROR,
                        walletDetails = walletDetails,
                        errorCode = walletNotificationRequestDto.errorCode)
                }

            is PayPalDetails ->
                if (operationResult == WalletNotificationRequestDto.OperationResultEnum.EXECUTED) {
                    if (operationDetails is WalletNotificationRequestPaypalDetailsDto) {
                        WalletNotificationProcessingResult(
                            newWalletStatus = WalletStatusDto.VALIDATED,
                            walletDetails =
                                walletDetails.copy(
                                    maskedEmail = MaskedEmail(operationDetails.maskedEmail)),
                            errorCode = walletNotificationRequestDto.errorCode)
                    } else {
                        logger.error(
                            "No details received for PayPal wallet, cannot retrieve maskedEmail")
                        WalletNotificationProcessingResult(
                            newWalletStatus = WalletStatusDto.ERROR,
                            walletDetails = walletDetails,
                            errorCode = walletNotificationRequestDto.errorCode)
                    }
                } else {
                    WalletNotificationProcessingResult(
                        newWalletStatus = WalletStatusDto.ERROR,
                        walletDetails = walletDetails,
                        errorCode = walletNotificationRequestDto.errorCode)
                }

            else ->
                throw InvalidRequestException(
                    "Unhandled wallet details for notification request: $walletDetails")
        }
    }

    fun findSessionWallet(
        xUserId: UUID,
        walletId: WalletId,
        orderId: String,
    ): Mono<SessionWalletRetrieveResponseDto> {
        return npgSessionRedisTemplate
            .findById(orderId)
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
                    .flatMap {
                        it.expectInStatus(
                                WalletStatusDto.VALIDATION_REQUESTED,
                                WalletStatusDto.VALIDATED,
                                WalletStatusDto.ERROR)
                            .toMono()
                    }
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
                                        walletDetailType = wallet.details?.type)
                                } else {
                                    null
                                })
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
            .applications(
                wallet.applications.map { application ->
                    WalletApplicationInfoDto()
                        .name(application.id)
                        .status(WalletApplicationStatusDto.valueOf(application.status))
                })
            .clients(
                wallet.clients.entries.associate { (clientKey, clientInfo) ->
                    clientKey to buildWalletClientDto(clientInfo)
                })
            .details(toWalletInfoDetailsDto(wallet.details))
            .paymentMethodAsset(walletUtils.getLogo(wallet.toDomain()))

    private fun buildWalletClientDto(
        clientInfo: it.pagopa.wallet.documents.wallets.Client
    ): WalletClientDto = WalletClientDto().status(WalletClientStatusDto.valueOf(clientInfo.status))

    private fun toWalletInfoDetailsDto(details: WalletDetails<*>?): WalletInfoDetailsDto? {
        return when (details) {
            is CardDetails ->
                WalletCardDetailsDto()
                    .type(details.type)
                    .expiryDate(details.expiryDate)
                    .lastFourDigits(details.lastFourDigits)
                    .brand(CardBrand(details.brand).value)

            is PayPalDetailsDocument ->
                WalletPaypalDetailsDto()
                    .maskedEmail(details.maskedEmail)
                    .pspId(details.pspId)
                    .pspBusinessName(details.pspBusinessName)

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
                        "Called getAuthData on null wallet details for wallet id: ${wallet.id}!")

                else ->
                    throw RuntimeException(
                        "Unhandled wallet details variant in getAuthData for wallet id ${wallet.id}")
            }

        val contextualOnboardDetails =
            wallet.applications
                .find { application -> application.id == "PAGOPA" }
                ?.metadata
                ?.takeIf {
                    it[WalletApplicationMetadata.Metadata.PAYMENT_WITH_CONTEXTUAL_ONBOARD.value]
                        .toBoolean() &&
                        WalletStatusDto.valueOf(wallet.status) != WalletStatusDto.VALIDATED
                }
                ?.let { metadata ->
                    ContextualOnboardDetailsDto().apply {
                        amount(
                            metadata[WalletApplicationMetadata.Metadata.AMOUNT.value]
                                ?.toLongOrNull())
                        transactionId(
                            metadata[WalletApplicationMetadata.Metadata.TRANSACTION_ID.value])
                        orderId(metadata[WalletApplicationMetadata.Metadata.ORDER_ID.value])
                        sessionId(metadata[WalletApplicationMetadata.Metadata.SESSION_ID.value])
                    }
                }

        return WalletAuthDataDto()
            .walletId(UUID.fromString(wallet.id))
            .contractId(wallet.contractId)
            .brand(CardBrand(brand).value)
            .paymentMethodData(paymentMethodData)
            .contextualOnboardDetails(contextualOnboardDetails)
    }

    fun updateWalletApplications(
        walletId: WalletId,
        userId: UserId,
        applicationsToUpdate: List<Pair<WalletApplicationId, WalletApplicationStatus>>
    ): Mono<LoggedAction<WalletApplicationUpdateData>> {
        return walletRepository
            .findByIdAndUserId(walletId.value.toString(), userId.id.toString())
            .switchIfEmpty { Mono.error(WalletNotFoundException(walletId)) }
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
                                Mono.error(ApplicationNotFoundException(applicationId.id)))
                    }
                    .reduce(
                        Triple(
                            mutableMapOf<WalletApplicationId, WalletApplicationStatus>(),
                            mutableMapOf<WalletApplicationId, WalletApplicationStatus>(),
                            walletApplications.toMutableMap())) {
                            (
                                applicationsUpdatedSuccessfully,
                                applicationsWithUpdateFailed,
                                updatedApplications),
                            (application, applicationId, requestedStatus) ->
                            val applicationGlobalStatus =
                                WalletApplicationStatus.valueOf(application.status)
                            val walletApplication = walletApplications[applicationId]

                            if (WalletApplicationStatus.canChangeToStatus(
                                requested = requestedStatus, global = applicationGlobalStatus)) {
                                updatedApplications[applicationId] =
                                    walletApplication?.copy(
                                        updateDate = Instant.now().toString(),
                                        status = requestedStatus.name)
                                        ?: it.pagopa.wallet.documents.wallets.WalletApplication(
                                            id = applicationId.id,
                                            status = requestedStatus.name,
                                            creationDate = Instant.now().toString(),
                                            updateDate = Instant.now().toString(),
                                            metadata = hashMapOf())
                                applicationsUpdatedSuccessfully[applicationId] = requestedStatus
                            } else {
                                applicationsWithUpdateFailed[applicationId] =
                                    applicationGlobalStatus
                            }

                            Triple(
                                applicationsUpdatedSuccessfully,
                                applicationsWithUpdateFailed,
                                updatedApplications)
                        }
                    .map {
                        (
                            applicationsUpdatedSuccessfully,
                            applicationsWithUpdateFailed,
                            updatedApplications) ->
                        WalletApplicationUpdateData(
                            applicationsUpdatedSuccessfully,
                            applicationsWithUpdateFailed,
                            wallet.copy(
                                applications = updatedApplications.values.toList(),
                                updateDate = Instant.now()))
                    }
            }
            .flatMap { walletRepository.save(it.updatedWallet).thenReturn(it) }
            .map {
                LoggedAction(
                    it,
                    WalletApplicationsUpdatedEvent(
                        it.updatedWallet.id,
                        it.updatedWallet.applications.map { app ->
                            AuditWalletApplication(
                                app.id,
                                app.status,
                                app.creationDate,
                                app.updateDate,
                                app.metadata.mapKeys { m -> m.key })
                        }))
            }
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
                    if (errorCode == Constants.WALLET_ALREADY_ONBOARDED_FOR_USER_ERROR_CODE) {
                        SessionWalletRetrieveResponseDto.OutcomeEnum.NUMBER_15
                    } else {
                        SessionWalletRetrieveResponseDto.OutcomeEnum.NUMBER_0
                    }

                WalletNotificationRequestDto.OperationResultEnum.AUTHORIZED ->
                    SessionWalletRetrieveResponseDto.OutcomeEnum.NUMBER_25

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
                    SessionWalletRetrieveResponseDto.OutcomeEnum.NUMBER_25

                WalletNotificationRequestDto.OperationResultEnum.CANCELED ->
                    SessionWalletRetrieveResponseDto.OutcomeEnum.NUMBER_8

                WalletNotificationRequestDto.OperationResultEnum.VOIDED ->
                    SessionWalletRetrieveResponseDto.OutcomeEnum.NUMBER_25

                WalletNotificationRequestDto.OperationResultEnum.REFUNDED ->
                    SessionWalletRetrieveResponseDto.OutcomeEnum.NUMBER_25

                WalletNotificationRequestDto.OperationResultEnum.FAILED ->
                    SessionWalletRetrieveResponseDto.OutcomeEnum.NUMBER_25

                null -> SessionWalletRetrieveResponseDto.OutcomeEnum.NUMBER_1
            }
        logger.info(
            "Npg notification gateway status: [{}], errorCode: [{}] for wallet type: [{}] decoded as IO outcome: [{}]",
            operationResult,
            errorCode,
            walletDetailType,
            outcome)
        return outcome
    }

    private fun decodeCardsOnboardingNpgErrorCode(errorCode: String?) =
        if (errorCode != null) {
            NPG_CARDS_ONBOARDING_ERROR_CODE_MAPPING.getOrDefault(
                errorCode, SessionWalletRetrieveResponseDto.OutcomeEnum.NUMBER_25)
        } else {
            SessionWalletRetrieveResponseDto.OutcomeEnum.NUMBER_25
        }

    private fun createTokenAndBuildNotificationUri(
        isTransactionWithContextualOnboard: Boolean,
        walletId: UUID,
        orderId: String,
        transactionId: String?
    ): Mono<URI> {
        return jwtTokenIssuerClient
            .createToken(
                CreateTokenRequest()
                    .audience(NPG_AUDIENCE)
                    .duration(tokenValidityTimeSeconds)
                    .privateClaims(
                        if (!isTransactionWithContextualOnboard) {
                            mapOf(WALLET_ID_CLAIM to walletId.toString())
                        } else {
                            mapOf(
                                WALLET_ID_CLAIM to walletId.toString(),
                                TRANSACTION_ID_CLAIM to transactionId)
                        }))
            .map { response ->
                buildNotificationUrl(
                    isTransactionWithContextualOnboard,
                    walletId,
                    orderId,
                    transactionId,
                    response.token)
            }
    }

    private fun buildOutcomeUrl(wallet: Wallet): Mono<Pair<URI, URI>> {
        val isTransactionWithContextualOnboard =
            wallet
                .getApplicationMetadata(
                    pagopaWalletApplicationId,
                    WalletApplicationMetadata.Metadata.PAYMENT_WITH_CONTEXTUAL_ONBOARD)
                .toBoolean()
        if (isTransactionWithContextualOnboard) {
            val transactionId =
                wallet
                    .getApplicationMetadata(
                        pagopaWalletApplicationId,
                        WalletApplicationMetadata.Metadata.TRANSACTION_ID)
                    .toString()
            return walletJwtTokenCtxOnboardingTemplateWrapper
                .findById(wallet.id.value.toString())
                .map { session ->
                    Pair(
                        UriComponentsBuilder.fromUriString(
                                sessionUrlConfig.trxWithContextualOnboardingBasePath.plus(
                                    sessionUrlConfig
                                        .trxWithContextualOnboardingOutcomeSuffix)) // append query
                            // param to
                            // prevent
                            // caching
                            .queryParam("t", Instant.now().toEpochMilli())
                            .build(
                                mapOf(
                                    "clientId" to "IO",
                                    "transactionId" to transactionId,
                                    "sessionToken" to session.jwtToken)),
                        UriComponentsBuilder.fromUriString(
                                sessionUrlConfig.trxWithContextualOnboardingBasePath.plus(
                                    sessionUrlConfig
                                        .trxWithContextualOnboardingCancelSuffix)) // append query
                            // param to
                            // prevent
                            // caching
                            .queryParam("t", Instant.now().toEpochMilli())
                            .build(
                                mapOf(
                                    "clientId" to "IO",
                                    "transactionId" to transactionId,
                                    "sessionToken" to session.jwtToken)))
                }
        } else {
            val basePath = URI.create(sessionUrlConfig.basePath)
            return Mono.just(
                Pair(
                    basePath.resolve(sessionUrlConfig.outcomeSuffix),
                    basePath.resolve(sessionUrlConfig.cancelSuffix)))
        }
    }

    private fun buildNotificationUrl(
        isTransactionWithContextualOnboard: Boolean,
        walletId: UUID,
        orderId: String,
        transactionId: String?,
        sessionToken: String
    ): URI {
        return if (isTransactionWithContextualOnboard) {
            UriComponentsBuilder.fromHttpUrl(
                    sessionUrlConfig.trxWithContextualOnboardNotificationUrl)
                .build(
                    mapOf(
                        Pair("transactionId", transactionId),
                        Pair("walletId", walletId),
                        Pair("orderId", orderId),
                        Pair("sessionToken", sessionToken)))
        } else {
            UriComponentsBuilder.fromHttpUrl(sessionUrlConfig.notificationUrl)
                .build(
                    mapOf(
                        Pair("walletId", walletId),
                        Pair("orderId", orderId),
                        Pair("sessionToken", sessionToken)))
        }
    }

    /**
     * The method is used to check if a wallet is already onboard given a userId. A wallet CARD is
     * already onboarded if there is already a wallet for a given user with the same
     * paymentInstrumentGatewayId. This check is disabled for wallet PAYPAL
     *
     * @param walletId wallet identifier
     * @param userId user identifier
     * @param walletDetails it.pagopa.wallet.domain.wallets.details.WalletDetails<*>
     * @return Mono<Boolean>: true if already onboarded, false otherwise
     */
    private fun isWalletAlreadyOnboardedForUserId(
        walletId: WalletId,
        userId: UserId,
        walletDetails: it.pagopa.wallet.domain.wallets.details.WalletDetails<*>?,
        paymentInstrumentGatewayId: String?
    ): Mono<Boolean> =
        when (walletDetails) {
            is it.pagopa.wallet.domain.wallets.details.CardDetails -> {
                logger.debug(
                    "Already onboard check CARD wallet for userId [{}] and walletId [{}]",
                    userId,
                    walletId)
                if (paymentInstrumentGatewayId != null) {
                    walletRepository
                        .findByUserIdAndDetailsPaymentInstrumentGatewayIdForWalletStatus(
                            userId = userId.id.toString(),
                            paymentInstrumentGatewayId = paymentInstrumentGatewayId,
                            status = WalletStatusDto.VALIDATED)
                        .hasElement()
                } else {
                    Mono.error(
                        InvalidRequestException(
                            "Invalid paymentInstrumentGatewayId null for card wallet notification"))
                }
            }

            is PayPalDetails -> {
                logger.debug(
                    "Already onboard check DISABLED for PAYPAL for userId [{}] and walletId [{}]",
                    userId,
                    walletId)
                mono { false }
            }

            else -> {
                val errorDescription =
                    "Unhandled already onboard check for userId [${userId}] and walletId [${walletId}]"
                throw InvalidRequestException(errorDescription)
            }
        }

    private fun createPaypalDetails(
        sessionInputPayPalDataDto: SessionInputPayPalDataDto,
        paymentMethodId: PaymentMethodId,
    ) =
        pspDetailClient
            .getPspDetails(sessionInputPayPalDataDto.pspId, paymentMethodId)
            .map { PayPalDetails(null, sessionInputPayPalDataDto.pspId, it.pspBusinessName ?: "") }
            .switchIfEmpty { Mono.error(PspNotFoundException(sessionInputPayPalDataDto.pspId)) }
}
