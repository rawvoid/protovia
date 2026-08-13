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

import io.github.rawvoid.protovia.ProtoAny;
import io.github.rawvoid.protovia.codec.ProtoCodec;
import io.github.rawvoid.protovia.wire.ProtoReader;
import io.github.rawvoid.protovia.wire.ProtoWriter;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;

class WellKnownCodecTest {

    @Test
    void int32ValueOmitsZero() {
        assertEquals(0, encode(Int32Value.INSTANCE, new Int32Value(0)).length);
        assertArrayEquals(new byte[]{0x08, 0x2A}, encode(Int32Value.INSTANCE, new Int32Value(42)));
        assertEquals(new Int32Value(42), decode(Int32Value.INSTANCE, new byte[]{0x08, 0x2A}));
        assertEquals(new Int32Value(0), decode(Int32Value.INSTANCE, new byte[0]));
    }

    @Test
    void stringAndBytesOmitEmpty() {
        assertEquals(0, encode(StringValue.INSTANCE, new StringValue("")).length);
        assertArrayEquals(new byte[]{0x0A, 0x02, 'h', 'i'}, encode(StringValue.INSTANCE, new StringValue("hi")));
        assertEquals(0, encode(BytesValue.INSTANCE, new BytesValue(new byte[0])).length);
        assertEquals(new BytesValue(new byte[]{1, 2}), decode(BytesValue.INSTANCE, new byte[]{0x0A, 0x02, 1, 2}));
    }

    @Test
    void floatWritesNegativeZero() {
        byte[] bits = encode(FloatValue.INSTANCE, new FloatValue(-0.0f));
        assertTrue(bits.length > 0);
        assertEquals(-0.0f, decode(FloatValue.INSTANCE, bits).value());
    }

    @Test
    void anyOmitsEmptyFields() {
        assertEquals(0, encode(AnyCodec.INSTANCE, new ProtoAny("", new byte[0])).length);
        ProtoAny packed = new ProtoAny("type.googleapis.com/User", new byte[]{0x0A, 0x01, 'a'});
        ProtoAny back = decode(AnyCodec.INSTANCE, encode(AnyCodec.INSTANCE, packed));
        assertEquals(packed, back);
    }

    @Test
    void timestampMergeKeepsExistingNanos() {
        Instant existing = Instant.ofEpochSecond(10, 250);
        byte[] onlySeconds = encode(TimestampCodec.INSTANCE, Instant.ofEpochSecond(20, 0));
        Instant merged = TimestampCodec.INSTANCE.mergeFrom(
            new ProtoReader(onlySeconds), existing);
        assertEquals(Instant.ofEpochSecond(20, 250), merged);
    }

    @Test
    void wrapperFullNames() {
        assertEquals("google.protobuf.Int32Value", Int32Value.INSTANCE.protoFullName());
        assertEquals("google.protobuf.Any", AnyCodec.INSTANCE.protoFullName());
        assertEquals("google.protobuf.Timestamp", TimestampCodec.INSTANCE.protoFullName());
        assertEquals("google.protobuf.Duration", DurationCodec.INSTANCE.protoFullName());
    }

    private static <T> byte[] encode(ProtoCodec<T> codec, T value) {
        int size = codec.computeSize(value);
        ProtoWriter writer = new ProtoWriter(size);
        codec.writeTo(writer, value);
        return writer.finish();
    }

    private static <T> T decode(ProtoCodec<T> codec, byte[] bytes) {
        return codec.readFrom(new ProtoReader(bytes));
    }
}
