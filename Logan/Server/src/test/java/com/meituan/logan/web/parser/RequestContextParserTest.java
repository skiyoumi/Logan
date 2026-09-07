package com.meituan.logan.web.parser;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class RequestContextParserTest {

    @Test
    public void restoresUtf8HeaderDecodedAsLatin1() {
        assertEquals("yzkdcg_\u7ba1\u7406\u5458",
                RequestContextParser.decodeHeaderValue("yzkdcg_\u00e7\u00ae\u00a1\u00e7\u0090\u0086\u00e5\u0091\u0098"));
    }

    @Test
    public void preservesAsciiHeader() {
        assertEquals("user-123", RequestContextParser.decodeHeaderValue("user-123"));
    }

    @Test
    public void preservesHeaderThatIsAlreadyUnicode() {
        assertEquals("yzkdcg_\u7ba1\u7406\u5458",
                RequestContextParser.decodeHeaderValue("yzkdcg_\u7ba1\u7406\u5458"));
    }
}
