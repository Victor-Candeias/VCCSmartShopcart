package pt.vcc.smartshopcart.domain.repository

/** Executa vários passos de repositório de forma atómica. */
interface TransactionRunner {
    suspend fun <T> run(block: suspend () -> T): T
}
