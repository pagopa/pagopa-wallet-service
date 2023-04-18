package it.pagopa.wallet.controller

import it.pagopa.wallet.services.WalletService
import lombok.extern.slf4j.Slf4j
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.web.bind.annotation.RestController

@RestController
@Slf4j
class WalletController(@Autowired private val walletService: WalletService) {}
