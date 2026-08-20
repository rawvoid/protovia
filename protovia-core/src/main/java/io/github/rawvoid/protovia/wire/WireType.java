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

/**
 * Official protobuf wire types and field-number checks.
 *
 * @author Rawvoid
 */
public final class WireType {

    public static final int VARINT = 0;
    public static final int I64 = 1;
    public static final int LEN = 2;
    public static final int START_GROUP = 3;
    public static final int END_GROUP = 4;
    public static final int I32 = 5;

    public static final int MAX_FIELD_NUMBER = 536_870_911;
    public static final int RESERVED_NUMBER_MIN = 19_000;
    public static final int RESERVED_NUMBER_MAX = 19_999;

    private WireType() {
    }

    /**
     * @param fieldNumber protobuf field number
     * @param wireType    one of {@link #VARINT}, {@link #I64}, {@link #LEN}, {@link #I32}, …
     * @return encoded tag
     */
    public static int tag(int fieldNumber, int wireType) {
        return (fieldNumber << 3) | wireType;
    }

    public static int fieldNumber(int tag) {
        return tag >>> 3;
    }

    public static int getWireType(int tag) {
        return tag & 0x7;
    }

    public static boolean isValidFieldNumber(int number) {
        return number >= 1
            && number <= MAX_FIELD_NUMBER
            && (number < RESERVED_NUMBER_MIN || number > RESERVED_NUMBER_MAX);
    }

    /**
     * Inclusive {@code [from, to]} of valid field numbers that does not
     * cross {@code [19000, 19999]}.
     */
    public static boolean isValidFieldNumberRange(int from, int to) {
        if (from > to) {
            return false;
        }
        if (!isValidFieldNumber(from) || !isValidFieldNumber(to)) {
            return false;
        }
        return to < RESERVED_NUMBER_MIN || from > RESERVED_NUMBER_MAX;
    }
}
