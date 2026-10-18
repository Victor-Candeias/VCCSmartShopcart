package pt.vcc.smartshopcart.core.ui.components

import org.junit.Assert.assertEquals
import org.junit.Test

class KeypadInputTest {
    private fun type(vararg keys: KeypadKey) = keys.fold("") { acc, key -> KeypadInput.apply(acc, key) }

    private fun d(v: Int) = KeypadKey.Digit(v)

    @Test
    fun `virgula inicial e no maximo dois decimais`() {
        assertEquals("0,49", type(KeypadKey.Comma, d(4), d(9), d(9)))
        assertEquals("1,4", type(d(1), KeypadKey.Comma, KeypadKey.Comma, d(4)))
    }

    @Test
    fun `zero a esquerda e substituido e limite de inteiros`() {
        assertEquals("7", type(d(0), d(7)))
        assertEquals("12345", type(d(1), d(2), d(3), d(4), d(5), d(6)))
    }

    @Test
    fun `apagar remove o ultimo caracter`() {
        assertEquals("1,", type(d(1), KeypadKey.Comma, d(5), KeypadKey.Backspace))
        assertEquals("", type(KeypadKey.Backspace))
    }
}
