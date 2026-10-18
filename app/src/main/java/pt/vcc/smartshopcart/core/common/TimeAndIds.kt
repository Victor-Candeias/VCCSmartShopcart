package pt.vcc.smartshopcart.core.common

import java.util.UUID
import javax.inject.Inject

/** Fonte de tempo (epoch millis UTC, D10) substituível nos testes. */
fun interface TimeProvider {
    fun now(): Long
}

fun interface IdProvider {
    fun newId(): String
}

class SystemTimeProvider @Inject constructor() : TimeProvider {
    override fun now(): Long = System.currentTimeMillis()
}

class UuidIdProvider @Inject constructor() : IdProvider {
    override fun newId(): String = UUID.randomUUID().toString()
}
