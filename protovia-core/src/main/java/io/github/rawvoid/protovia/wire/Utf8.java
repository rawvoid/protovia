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

import java.nio.charset.StandardCharsets;
import java.util.Arrays;

/**
 * UTF-8 encode/decode matching official {@code protobuf-java}:
 * encoding uses ASCII fast paths and direct writes into the output buffer (replacing unpaired
 * surrogates with ASCII {@code '?'}, matching {@link String#getBytes(java.nio.charset.Charset)});
 * decoding uses the {@code protobuf-java} 4.36 {@code ServerProcessor} pattern via JDK UTF-8
 * decoding plus {@code U+FFFD} round-trip validation.
 *
 * @author Rawvoid
 */
public final class Utf8 {

    /**
     * Maximum UTF-8 bytes produced by one UTF-16 {@code char} (supplementary planes use two chars).
     */
    public static final int MAX_BYTES_PER_CHAR = 3;

    private Utf8() {
    }

    /**
     * Byte length of {@code value} encoded as UTF-8. Unpaired surrogates are replaced with ASCII
     * {@code '?'} ({@code 0x3F}, 1 byte), matching {@code String.getBytes(StandardCharsets.UTF_8)}
     * and official {@code protobuf-java} (not {@code U+FFFD}).
     */
    public static int encodedLength(String value) {
        int utf16Length = value.length();
        int utf8Length = utf16Length;
        int i = 0;
        while (i < utf16Length && value.charAt(i) < 0x80) {
            i++;
        }
        for (; i < utf16Length; i++) {
            char c = value.charAt(i);
            if (c < 0x800) {
                utf8Length += (0x7F - c) >>> 31;
            } else {
                int extra = encodedLengthGeneral(value, i);
                if (extra < 0) {
                    return value.getBytes(StandardCharsets.UTF_8).length;
                }
                utf8Length += extra;
                break;
            }
        }
        if (utf8Length < utf16Length) {
            throw new ProtoException("UTF-8 length does not fit in int: " + (utf8Length + (1L << 32)));
        }
        return utf8Length;
    }

    /**
     * Encodes {@code in} into {@code out[offset..offset+length)}.
     *
     * @return the offset immediately after the last written byte
     */
    public static int encode(String in, byte[] out, int offset, int length) {
        int utf16Length = in.length();
        int j = offset;
        int i = 0;
        int limit = offset + length;
        for (char c; i < utf16Length && i + j < limit && (c = in.charAt(i)) < 0x80; i++) {
            out[j + i] = (byte) c;
        }
        if (i == utf16Length) {
            return j + utf16Length;
        }
        j += i;
        for (; i < utf16Length; i++) {
            char c = in.charAt(i);
            if (c < 0x80 && j < limit) {
                out[j++] = (byte) c;
            } else if (c < 0x800 && j <= limit - 2) {
                out[j++] = (byte) (0xC0 | (c >>> 6));
                out[j++] = (byte) (0x80 | (0x3F & c));
            } else if ((c < Character.MIN_SURROGATE || Character.MAX_SURROGATE < c) && j <= limit - 3) {
                out[j++] = (byte) (0xE0 | (c >>> 12));
                out[j++] = (byte) (0x80 | (0x3F & (c >>> 6)));
                out[j++] = (byte) (0x80 | (0x3F & c));
            } else if (j <= limit - 4) {
                char low;
                if (i + 1 == in.length() || !Character.isSurrogatePair(c, low = in.charAt(++i))) {
                    return encodeNaive(in, out, offset, length);
                }
                int codePoint = Character.toCodePoint(c, low);
                out[j++] = (byte) (0xF0 | (codePoint >>> 18));
                out[j++] = (byte) (0x80 | (0x3F & (codePoint >>> 12)));
                out[j++] = (byte) (0x80 | (0x3F & (codePoint >>> 6)));
                out[j++] = (byte) (0x80 | (0x3F & codePoint));
            } else {
                if (Character.isSurrogate(c) && (i + 1 == in.length() || !Character.isSurrogatePair(c, in.charAt(i + 1)))) {
                    return encodeNaive(in, out, offset, length);
                }
                throw new ProtoException("Not enough space in output buffer to encode UTF-8 string");
            }
        }
        return j;
    }

    public static String decode(byte[] bytes, int offset, int length) {
        int end = offset + length;
        if ((offset | length | end | (bytes.length - end)) < 0) {
            throw new ProtoException(
                "invalid UTF-8 slice offset=" + offset + " length=" + length + " buffer=" + bytes.length);
        }
        if (length == 0) {
            return "";
        }
        String s = new String(bytes, offset, length, StandardCharsets.UTF_8);
        if (s.indexOf('\uFFFD') < 0) {
            return s;
        }
        byte[] reencoded = s.getBytes(StandardCharsets.UTF_8);
        if (Arrays.equals(reencoded, 0, reencoded.length, bytes, offset, end)) {
            return s;
        }
        throw invalidUtf8();
    }

    private static int encodedLengthGeneral(String string, int start) {
        int utf16Length = string.length();
        int extra = 0;
        for (int i = start; i < utf16Length; i++) {
            char c = string.charAt(i);
            if (c < 0x800) {
                extra += (0x7F - c) >>> 31;
            } else {
                extra += 2;
                if (Character.MIN_SURROGATE <= c && c <= Character.MAX_SURROGATE) {
                    int cp = Character.codePointAt(string, i);
                    if (cp < Character.MIN_SUPPLEMENTARY_CODE_POINT) {
                        return -1;
                    }
                    i++;
                }
            }
        }
        return extra;
    }

    private static int encodeNaive(String in, byte[] out, int offset, int length) {
        byte[] bytes = in.getBytes(StandardCharsets.UTF_8);
        if (bytes.length > length) {
            throw new ProtoException("Not enough space in output buffer to encode UTF-8 string");
        }
        System.arraycopy(bytes, 0, out, offset, bytes.length);
        return offset + bytes.length;
    }

    private static ProtoException invalidUtf8() {
        return new ProtoException("invalid UTF-8 in string field");
    }
}
