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

package io.github.rawvoid.protovia.wkt;

import io.github.rawvoid.protovia.codec.ProtoCodec;

import java.util.Arrays;

/**
 * {@code google.protobuf.BytesValue}.
 *
 * @param value wrapped bytes; {@code null} is stored as an empty array
 * @author Rawvoid
 */
public record BytesValue(byte[] value) {
    public static final ProtoCodec<BytesValue> INSTANCE = WrapperCodec.bytes();

    public BytesValue {
        if (value == null) {
            value = new byte[0];
        }
    }

    @Override
    public boolean equals(Object obj) {
        return obj instanceof BytesValue other && Arrays.equals(value, other.value);
    }

    @Override
    public int hashCode() {
        return Arrays.hashCode(value);
    }
}
