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

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CodedSizeTest {

    @Test
    void uint32MatchesBitWidth() {
        int[] values = {
            0,
            1,
            0x7F,
            0x80,
            0x3FFF,
            0x4000,
            0x1FFFFF,
            0x200000,
            0x0FFFFFFF,
            0x10000000,
            -1,
            Integer.MIN_VALUE,
            Integer.MAX_VALUE
        };
        for (int value : values) {
            assertEquals(referenceUint32(value), CodedSize.uint32(value), Integer.toHexString(value));
        }
    }

    @Test
    void uint64MatchesBitWidth() {
        long[] values = {
            0L,
            1L,
            0x7FL,
            0x80L,
            0x3FFFL,
            0x4000L,
            0x1FFFFFL,
            0x200000L,
            0xFFFFFFFL,
            0x10000000L,
            0x7FFFFFFFFL,
            0x800000000L,
            0x3FFFFFFFFFFL,
            0x40000000000L,
            0x1FFFFFFFFFFFFL,
            0x2000000000000L,
            0xFFFFFFFFFFFFFFL,
            0x100000000000000L,
            0x7FFFFFFFFFFFFFFFL,
            -1L,
            Long.MIN_VALUE,
            Long.MAX_VALUE
        };
        for (long value : values) {
            assertEquals(referenceUint64(value), CodedSize.uint64(value), Long.toHexString(value));
        }
    }

    @Test
    void int32NegativeIsTenBytes() {
        assertEquals(10, CodedSize.int32(-1));
        assertEquals(10, CodedSize.int32(Integer.MIN_VALUE));
        assertEquals(1, CodedSize.int32(0));
        assertEquals(1, CodedSize.int32(127));
        assertEquals(2, CodedSize.int32(128));
    }

    private static int referenceUint32(int value) {
        if ((value & ~0x7F) == 0) {
            return 1;
        }
        if ((value & ~0x3FFF) == 0) {
            return 2;
        }
        if ((value & ~0x1FFFFF) == 0) {
            return 3;
        }
        if ((value & ~0xFFFFFFF) == 0) {
            return 4;
        }
        return 5;
    }

    private static int referenceUint64(long value) {
        if ((value & ~0x7FL) == 0L) {
            return 1;
        }
        if ((value & ~0x3FFFL) == 0L) {
            return 2;
        }
        if ((value & ~0x1FFFFFL) == 0L) {
            return 3;
        }
        if ((value & ~0xFFFFFFFL) == 0L) {
            return 4;
        }
        if ((value & ~0x7FFFFFFFFL) == 0L) {
            return 5;
        }
        if ((value & ~0x3FFFFFFFFFFL) == 0L) {
            return 6;
        }
        if ((value & ~0x1FFFFFFFFFFFFL) == 0L) {
            return 7;
        }
        if ((value & ~0xFFFFFFFFFFFFFFL) == 0L) {
            return 8;
        }
        if ((value & ~0x7FFFFFFFFFFFFFFFL) == 0L) {
            return 9;
        }
        return 10;
    }
}
