package com.meituan.logan.web.parser;

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.StandardCharsets;

/** Decodes native unionId headers before they are persisted. */
final class UnionIdHeaderDecoder {

    private UnionIdHeaderDecoder() {
    }

    static String decode(String value) {
        if (value == null || value.isEmpty()) {
            return value;
        }
        String text = restoreUtf8(value);
        try {
            StringBuilder decoded = new StringBuilder();
            for (int i = 0; i < text.length();) {
                if (text.charAt(i) != '%') {
                    // This is a URI component, not form data: preserve literal '+'.
                    decoded.append(text.charAt(i++));
                    continue;
                }
                ByteArrayOutputStream bytes = new ByteArrayOutputStream();
                while (i < text.length() && text.charAt(i) == '%') {
                    if (i + 2 >= text.length()) {
                        return text;
                    }
                    int high = Character.digit(text.charAt(i + 1), 16);
                    int low = Character.digit(text.charAt(i + 2), 16);
                    if (high < 0 || low < 0) {
                        return text;
                    }
                    bytes.write((high << 4) | low);
                    i += 3;
                }
                decoded.append(StandardCharsets.UTF_8.newDecoder()
                        .decode(ByteBuffer.wrap(bytes.toByteArray())));
            }
            // Decode once so an encoded literal such as %2520 remains %20.
            return decoded.toString();
        } catch (CharacterCodingException e) {
            // Keep malformed values instead of inserting replacement characters.
            return text;
        }
    }

    private static String restoreUtf8(String value) {
        try {
            // Tomcat exposes raw HTTP header bytes as ISO-8859-1 characters.
            ByteBuffer bytes = StandardCharsets.ISO_8859_1.newEncoder().encode(CharBuffer.wrap(value));
            return StandardCharsets.UTF_8.newDecoder().decode(bytes).toString();
        } catch (CharacterCodingException e) {
            // Preserve already-decoded Unicode and actual Latin-1 text.
            return value;
        }
    }
}
