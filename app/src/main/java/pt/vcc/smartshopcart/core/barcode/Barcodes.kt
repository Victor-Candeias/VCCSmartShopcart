package pt.vcc.smartshopcart.core.barcode

/**
 * Normalização e validação de códigos de barras (D21): aceita EAN-8, EAN-13 e UPC-A
 * (convertido para EAN-13 com `0` à esquerda), sempre com dígito de controlo válido.
 * Os códigos são `String` para preservar os zeros à esquerda.
 */
object Barcodes {

    /** Devolve o código normalizado ou `null` se não for um EAN-8/EAN-13/UPC-A válido. */
    fun normalize(raw: String): String? {
        val digits = raw.trim()
        if (digits.isEmpty() || !digits.all { it in '0'..'9' }) return null
        val code = when (digits.length) {
            8, 13 -> digits
            12 -> "0$digits"
            else -> return null
        }
        return code.takeIf { hasValidCheckDigit(it) }
    }

    fun hasValidCheckDigit(code: String): Boolean {
        if (code.length < 2 || !code.all { it in '0'..'9' }) return false
        val body = code.dropLast(1)
        // Pesos 3,1,3,... a partir do dígito mais à direita do corpo (GS1).
        val sum = body.reversed().withIndex().sumOf { (i, c) -> (c - '0') * if (i % 2 == 0) 3 else 1 }
        val check = (10 - sum % 10) % 10
        return check == code.last() - '0'
    }

    /** Códigos de circulação restrita (EAN-13 com prefixo 20–29): etiquetas internas da loja (D7). */
    fun isInternalStoreCode(code: String): Boolean = code.length == 13 && code[0] == '2'
}
