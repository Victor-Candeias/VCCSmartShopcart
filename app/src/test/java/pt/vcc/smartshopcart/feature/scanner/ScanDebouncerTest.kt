package pt.vcc.smartshopcart.feature.scanner

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ScanDebouncerTest {

    @Test
    fun `mesmo codigo e ignorado durante 3 segundos`() {
        val debouncer = ScanDebouncer()
        assertTrue(debouncer.accept("3017620422003", 10_000))
        assertFalse(debouncer.accept("3017620422003", 12_999))
        assertTrue(debouncer.accept("3017620422003", 13_000))
    }

    @Test
    fun `codigo diferente e aceite logo e marcar reinicia a janela`() {
        val debouncer = ScanDebouncer()
        assertTrue(debouncer.accept("3017620422003", 0))
        assertTrue(debouncer.accept("5601234567892", 100))
        debouncer.mark("5601234567892", 5_000)
        assertFalse(debouncer.accept("5601234567892", 7_000))
    }
}
