package pt.vcc.smartshopcart.core.barcode

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BarcodesTest {

    @Test
    fun `aceita EAN-13 e EAN-8 com digito de controlo valido`() {
        assertEquals("3017620422003", Barcodes.normalize("3017620422003"))
        assertEquals("96385074", Barcodes.normalize(" 96385074 "))
        assertEquals("5601234567892", Barcodes.normalize("5601234567892"))
    }

    @Test
    fun `converte UPC-A para EAN-13 preservando zeros`() {
        assertEquals("0036000291452", Barcodes.normalize("036000291452"))
    }

    @Test
    fun `rejeita digito de controlo errado, comprimentos e caracteres invalidos`() {
        assertNull(Barcodes.normalize("3017620422004"))
        assertNull(Barcodes.normalize("12345"))
        assertNull(Barcodes.normalize("30176204220O3"))
        assertNull(Barcodes.normalize(""))
        assertNull(Barcodes.normalize("036000291453"))
    }

    @Test
    fun `identifica codigos internos de loja`() {
        assertTrue(Barcodes.isInternalStoreCode("2000000000008"))
        assertTrue(Barcodes.isInternalStoreCode(Barcodes.normalize("2000000000008")!!))
        assertFalse(Barcodes.isInternalStoreCode("3017620422003"))
        assertFalse(Barcodes.isInternalStoreCode("0036000291452"))
    }
}