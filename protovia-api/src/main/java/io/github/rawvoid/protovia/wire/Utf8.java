package io.github.rawvoid.protovia.wire;

import io.github.rawvoid.protovia.ProtoException;

import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;

public final class Utf8 {

    private Utf8() {
    }

    /**
     * Byte length of {@code value} encoded as UTF-8. Surrogate pairs become 4 bytes.
     */
    public static int encodedLength(String value) {
        int length = value.length();
        int utf8 = length;
        int i = 0;
        while (i < length && value.charAt(i) < 0x80) {
            i++;
        }
        for (; i < length; i++) {
            char c = value.charAt(i);
            if (c < 0x800) {
                utf8 += (0x7F - c) >>> 31;
            } else {
                utf8 += 2;
                if (Character.isSurrogate(c)) {
                    int cp = Character.codePointAt(value, i);
                    if (cp < Character.MIN_SUPPLEMENTARY_CODE_POINT) {
                        throw new ProtoException("unpaired surrogate at index " + i);
                    }
                    i++;
                    utf8--;
                }
            }
        }
        return utf8;
    }

    public static String decode(byte[] bytes, int offset, int length) {
        CharsetDecoder decoder = StandardCharsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT);
        try {
            return decoder.decode(ByteBuffer.wrap(bytes, offset, length)).toString();
        } catch (CharacterCodingException e) {
            throw new ProtoException("invalid UTF-8 in string field", e);
        }
    }
}
