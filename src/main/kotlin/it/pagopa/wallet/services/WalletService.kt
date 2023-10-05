package it.pagopa.wallet.services

import it.pagopa.wallet.repositories.WalletRepository
import lombok.extern.slf4j.Slf4j
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Service

@Service @Slf4j class WalletService(@Autowired private val walletRepository: WalletRepository) {}
