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
        assertThrows(ProtoException.class, () -> Utf8.decode(new byte[]{(byte) 0xC3, 0x28}, 0, 2));
        assertThrows(ProtoException.class, () -> Utf8.decode(new byte[]{(byte) 0x80}, 0, 1));
        assertThrows(ProtoException.class, () -> Utf8.decode(new byte[]{(byte) 0xE0, (byte) 0x80, (byte) 0x80}, 0, 3));
        assertThrows(ProtoException.class, () -> Utf8.decode(new byte[]{(byte) 0xED, (byte) 0xA0, (byte) 0x80}, 0, 3));
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
        assertEquals(new String(s.getBytes(StandardCharsets.UTF_8), StandardCharsets.UTF_8), r.readString());
    }
}
