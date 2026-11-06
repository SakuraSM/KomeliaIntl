package snd.komelia.ui.common

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ServerUrlValidatorTest {
    @Test fun rejectsAddressesThatTheConnectionParserCannotRead() {
        assertEquals(ServerUrlValidationError.INVALID_URL, validateServerUrl("http://localhost:256a10.0.2.2:18764"))
        assertEquals(ServerUrlValidationError.INVALID_PORT, validateServerUrl("http://localhost:abc"))
        assertEquals(ServerUrlValidationError.INVALID_PORT, validateServerUrl("http://localhost:65536"))
    }

    @Test fun acceptsOrdinaryServersAndBracketedIpv6() {
        assertNull(validateServerUrl("http://10.0.2.2:18764"))
        assertNull(validateServerUrl("https://example.invalid/komga"))
        assertNull(validateServerUrl("http://[::1]:8080"))
    }
}
