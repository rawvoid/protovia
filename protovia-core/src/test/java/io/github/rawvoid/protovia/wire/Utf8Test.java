/*
 * Copyright 2026 Rawvoid(https://github.com/rawvoid)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.github.rawvoid.protovia.wire;

import io.github.rawvoid.protovia.ProtoException;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;

/**
 * @author Rawvoid
 */
class Utf8Test {

    @Test
    void encodedLengthMatchesGetBytes() {
        String[] samples = {
            "",
            "ascii",
            "héllo 世界",
            "𝄞",
            "a\uD800b",
            "\uD800",
            "\uDFFF",
            "\uD800\uDFFF"
        };
        for (String s : samples) {
            assertEquals(s.getBytes(StandardCharsets.UTF_8).length, Utf8.encodedLength(s), s);
        }
    }

    @Test
    void encodeMatchesGetBytes() {
        String[] samples = {
            "",
            "ascii only",
            "héllo 世界 café",
            "𝄞 music",
            "unpaired \uD800 surrogate",
            "trailing \uDFFF"
        };
        for (String s : samples) {
            byte[] expected = s.getBytes(StandardCharsets.UTF_8);
            byte[] out = new byte[expected.length];
            int end = Utf8.encode(s, out, 0, out.length);
            assertEquals(expected.length, end, s);
            assertArrayEquals(expected, out, s);
        }
    }

    @Test
    void decodeRoundTrip() {
        String[] samples = {"", "ascii", "héllo 世界", "𝄞", "mix ASCII and 中文 and 𝄞"};
        for (String s : samples) {
            byte[] utf8 = s.getBytes(StandardCharsets.UTF_8);
            assertEquals(s, Utf8.decode(utf8, 0, utf8.length));
        }
    }

    @Test
    void decodeRejectsInvalidSequences() {
        byte[][] invalid = {
            // Invalid continuation
            {(byte) 0xC3, 0x28},
            // Stray continuation bytes
            {(byte) 0x80},
            {(byte) 0xBF},
            {0x61, (byte) 0x80, 0x62},
            // Overlong encodings (2, 3, 4 bytes)
            {(byte) 0xC0, (byte) 0x80},
            {(byte) 0xC1, (byte) 0xBF},
            {(byte) 0xE0, (byte) 0x80, (byte) 0x80},
            {(byte) 0xE0, (byte) 0x9F, (byte) 0xBF},
            {(byte) 0xF0, (byte) 0x80, (byte) 0x80, (byte) 0x80},
            {(byte) 0xF0, (byte) 0x8F, (byte) 0xBF, (byte) 0xBF},
            // UTF-16 surrogates (U+D800..U+DFFF)
            {(byte) 0xED, (byte) 0xA0, (byte) 0x80},
            {(byte) 0xED, (byte) 0xB0, (byte) 0x80},
            {(byte) 0xED, (byte) 0xBF, (byte) 0xBF},
            // Out-of-range (> U+10FFFF)
            {(byte) 0xF4, (byte) 0x90, (byte) 0x80, (byte) 0x80},
            {(byte) 0xF5, (byte) 0x80, (byte) 0x80, (byte) 0x80},
            {(byte) 0xFF},
            // Truncated sequences
            {(byte) 0xC2},
            {(byte) 0xE2},
            {(byte) 0xE2, (byte) 0x82},
            {(byte) 0xF0},
            {(byte) 0xF0, (byte) 0x9F},
            {(byte) 0xF0, (byte) 0x9F, (byte) 0x98},
            // Valid literal U+FFFD mixed with invalid byte
            {(byte) 0xEF, (byte) 0xBF, (byte) 0xBD, (byte) 0x80},
            {(byte) 0x80, (byte) 0xEF, (byte) 0xBF, (byte) 0xBD}
        };
        for (byte[] seq : invalid) {
            assertThrows(ProtoException.class, () -> Utf8.decode(seq, 0, seq.length));
        }
    }

    @Test
    void decodePreservesValidReplacementCharacter() {
        byte[] literalFffd = {(byte) 0xEF, (byte) 0xBF, (byte) 0xBD};
        assertEquals("\uFFFD", Utf8.decode(literalFffd, 0, literalFffd.length));

        // Sub-slice with non-zero offset
        byte[] padded = {0x78, (byte) 0xEF, (byte) 0xBF, (byte) 0xBD, 0x79};
        assertEquals("\uFFFD", Utf8.decode(padded, 1, 3));

        // Mixed ASCII + literal U+FFFD + multibyte
        String mixed = "abc\uFFFDxyz\uFFFD世界\uFFFD𝄞";
        byte[] utf8 = mixed.getBytes(StandardCharsets.UTF_8);
        assertEquals(mixed, Utf8.decode(utf8, 0, utf8.length));
    }

    @Test
    void decodeRejectsInvalidSliceBounds() {
        byte[] buf = new byte[4];
        assertThrows(ProtoException.class, () -> Utf8.decode(buf, -1, 1));
        assertThrows(ProtoException.class, () -> Utf8.decode(buf, 0, -1));
        assertThrows(ProtoException.class, () -> Utf8.decode(buf, 3, 2));
        assertThrows(ProtoException.class, () -> Utf8.decode(buf, Integer.MAX_VALUE, Integer.MAX_VALUE));
    }

    @Test
    void unpairedSurrogateEncodesAsAsciiQuestionMark() {
        assertEquals(1, Utf8.encodedLength("\uD800"));
        assertEquals(1, Utf8.encodedLength("\uDFFF"));
        assertEquals(3, Utf8.encodedLength("a\uD800b"));

        byte[] out1 = new byte[1];
        assertEquals(1, Utf8.encode("\uD800", out1, 0, out1.length));
        assertArrayEquals(new byte[]{0x3F}, out1);

        byte[] out3 = new byte[3];
        assertEquals(3, Utf8.encode("a\uDFFFb", out3, 0, out3.length));
        assertArrayEquals(new byte[]{'a', 0x3F, 'b'}, out3);
    }

    @Test
    void writeStringSizeMatchesUnpairedSurrogate() {
        String s = "x\uD800y";
        int size = CodedSize.string(1, s);
        ProtoWriter w = new ProtoWriter(size);
        w.writeString(1, s);
        byte[] actual = w.finish();
        assertEquals(size, actual.length);

        ProtoReader r = new ProtoReader(actual);
        assertEquals(WireType.tag(1, WireType.LEN), r.readTag());
        assertEquals("x?y", r.readString());
    }
}
