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

package io.github.rawvoid.protovia;

import io.github.rawvoid.protovia.codec.ProtoCodec;
import io.github.rawvoid.protovia.runtime.CodecLookup;
import io.github.rawvoid.protovia.support.User;
import io.github.rawvoid.protovia.support.internal.UserProtoCodec;
import io.github.rawvoid.protovia.wire.CodedSize;
import io.github.rawvoid.protovia.wire.ProtoReader;
import io.github.rawvoid.protovia.wire.ProtoWriter;
import io.github.rawvoid.protovia.wire.SizeCache;
import io.github.rawvoid.protovia.wkt.AnyCodec;
import io.github.rawvoid.protovia.wkt.BytesValue;
import io.github.rawvoid.protovia.wkt.DurationCodec;
import io.github.rawvoid.protovia.wkt.Int32Value;
import io.github.rawvoid.protovia.wkt.StringValue;
import io.github.rawvoid.protovia.wkt.TimestampCodec;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.FilterInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

/**
 * @author Rawvoid
 */
class ProtoviaTest {

    @BeforeEach
    void register() {
        Protovia.register(User.class, UserProtoCodec.INSTANCE);
        Protovia.register(User.Address.class, UserProtoCodec.AddressProtoCodec.INSTANCE);
    }

    @Test
    void roundTrip() {
        User user = sample();
        byte[] bytes = Protovia.toBytes(user);
        User back = Protovia.fromBytes(bytes, User.class);
        assertEquals(user, back);
        assertEquals(bytes.length, Protovia.sizeOf(user));
    }

    @Test
    void emptyMessageIsEmptyBytes() {
        User user = new User();
        user.setTags(null);
        user.setScores(null);
        byte[] bytes = Protovia.toBytes(user);
        assertEquals(0, bytes.length);
        User back = Protovia.fromBytes(bytes, User.class);
        assertNull(back.getName());
        assertEquals(0, back.getAge());
    }

    @Test
    void optionalZeroIsWritten() {
        User user = new User();
        user.setTags(null);
        user.setScores(null);
        user.setLevel(0);
        byte[] bytes = Protovia.toBytes(user);
        assertArrayEquals(new byte[]{0x30, 0x00}, bytes);
        User back = Protovia.fromBytes(bytes, User.class);
        assertEquals(0, back.getLevel());
    }

    @Test
    void defaultAgeIsOmitted() {
        User user = new User();
        user.setName("a");
        user.setAge(0);
        user.setTags(null);
        user.setScores(null);
        byte[] bytes = Protovia.toBytes(user);
        assertArrayEquals(new byte[]{0x0A, 0x01, 'a'}, bytes);
    }

    @Test
    void streamRoundTrip() {
        User user = sample();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        Protovia.write(out, user);
        User back = Protovia.read(new ByteArrayInputStream(out.toByteArray()), User.class);
        assertEquals(user, back);
    }

    @Test
    void readBoundedAtIntegerMaxValueReadsTheStream() throws Exception {
        byte[] payload = {0x0A, 0x01, 'n'};
        assertArrayEquals(payload,
            Protovia.readBounded(new ByteArrayInputStream(payload), Integer.MAX_VALUE));
    }

    @Test
    void packUnpackUserAndInstant() {
        User user = sample();
        ProtoAny packed = Protovia.pack(user);
        assertEquals("type.googleapis.com/User", packed.typeUrl());
        assertTrue(Protovia.is(packed, User.class));
        assertEquals(user, Protovia.unpack(packed, User.class));

        Instant at = Instant.parse("2020-01-02T03:04:05.006Z");
        ProtoAny time = Protovia.pack(at);
        assertEquals("type.googleapis.com/google.protobuf.Timestamp", time.typeUrl());
        assertEquals(at, Protovia.unpack(time, Instant.class));
        assertThrows(ProtoException.class, () -> Protovia.unpack(time, Int32Value.class));
    }

    @Test
    void nullMessageRejected() {
        assertThrows(NullPointerException.class, () -> Protovia.toBytes(null));
    }

    @Test
    void missingCodec() {
        assertThrows(ProtoException.class, () -> Protovia.toBytes("not-a-message"));
    }

    @Test
    void skipUnknownFieldOnRead() {
        User user = new User();
        user.setName("n");
        user.setTags(null);
        user.setScores(null);
        byte[] known = Protovia.toBytes(user);
        // append unknown field 15 string "x"
        byte[] extra = new byte[known.length + 3];
        System.arraycopy(known, 0, extra, 0, known.length);
        extra[known.length] = 0x7A; // field 15, LEN
        extra[known.length + 1] = 0x01;
        extra[known.length + 2] = 'x';
        User back = Protovia.fromBytes(extra, User.class);
        assertEquals("n", back.getName());
    }

    @Test
    void sliceFromBytes() {
        User user = sample();
        byte[] full = Protovia.toBytes(user);
        byte[] padded = new byte[full.length + 10];
        System.arraycopy(full, 0, padded, 4, full.length);
        User back = Protovia.fromBytes(padded, 4, full.length, User.class);
        assertEquals(user, back);
    }

    @Test
    void protoConfigValidationAndValueSemantics() {
        assertSame(ProtoConfig.DEFAULT,
            ProtoConfig.of(Protovia.DEFAULT_MAX_MESSAGE_SIZE, Protovia.DEFAULT_MAX_DEPTH));
        assertThrows(IllegalArgumentException.class, () -> ProtoConfig.of(0, 10));
        assertThrows(IllegalArgumentException.class, () -> ProtoConfig.of(-1, 10));
        assertThrows(IllegalArgumentException.class, () -> ProtoConfig.of(10, 0));
        assertThrows(IllegalArgumentException.class, () -> ProtoConfig.of(10, -5));
        assertThrows(IllegalArgumentException.class, () -> ProtoConfig.DEFAULT.withMaxMessageSize(0));
        assertThrows(IllegalArgumentException.class, () -> ProtoConfig.DEFAULT.withMaxDepth(0));

        ProtoConfig a = Protovia.withLimits(128, 4);
        ProtoConfig b = ProtoConfig.DEFAULT.withMaxMessageSize(128).withMaxDepth(4);
        assertEquals(a, b);
        assertEquals(a.hashCode(), b.hashCode());
        assertEquals(128, a.maxMessageSize());
        assertEquals(4, a.maxDepth());
        assertSame(a, a.withMaxMessageSize(128));
        assertSame(a, a.withMaxDepth(4));
        assertTrue(a.toString().contains("128"));
        assertTrue(a.toString().contains("4"));
        assertEquals(ProtoConfig.DEFAULT, Protovia.config());
    }

    @Test
    void protoConfigIsolatesSizeAndDepthLimitsFromGlobal() {
        Protovia.register(Node.class, NodeCodec.INSTANCE);
        ProtoConfig strict = Protovia.withLimits(4, 2);

        // Size limit: 7-byte payload rejected by strict config, allowed by global/DEFAULT
        User user = new User();
        user.setName("hello"); // 1 tag + 1 len + 5 bytes = 7 bytes
        byte[] userBytes = Protovia.toBytes(user);
        assertTrue(userBytes.length > 4);

        assertThrows(ProtoException.class, () -> strict.toBytes(user));
        assertThrows(ProtoException.class, () -> strict.fromBytes(userBytes, User.class));
        assertThrows(ProtoException.class, () -> strict.pack(user));
        assertEquals(user, Protovia.fromBytes(userBytes, User.class));
        assertEquals(user, ProtoConfig.DEFAULT.fromBytes(userBytes, User.class));

        // Depth limit: 3 levels of nesting exceeds maxDepth=2
        Node depth2 = new Node(new Node(null)); // 1 nested read
        Node depth3 = new Node(new Node(new Node(null))); // 2 nested reads
        Node depth4 = new Node(new Node(new Node(new Node(null)))); // 3 nested reads

        byte[] depth3Bytes = Protovia.toBytes(depth3);
        byte[] depth4Bytes = Protovia.toBytes(depth4);
        ProtoConfig depthOnlyStrict = Protovia.withLimits(1024, 2);
        assertEquals(depth2, depthOnlyStrict.fromBytes(Protovia.toBytes(depth2), Node.class));
        assertEquals(depth3, depthOnlyStrict.fromBytes(depth3Bytes, Node.class));
        assertThrows(ProtoException.class, () -> depthOnlyStrict.fromBytes(depth4Bytes, Node.class));
        assertEquals(depth4, Protovia.fromBytes(depth4Bytes, Node.class));
        assertEquals(depth4, ProtoConfig.DEFAULT.fromBytes(depth4Bytes, Node.class));
    }

    @Test
    void protoAnyUnpackEnforcesGlobalAndConfigLimits() {
        Protovia.register(Node.class, NodeCodec.INSTANCE);
        User user = sample();
        ProtoAny packedUser = Protovia.pack(user);
        assertTrue(packedUser.value().length > 4);

        Node deep = new Node(new Node(new Node(new Node(null)))); // 3 nested levels
        ProtoAny packedDeep = Protovia.pack(deep);

        // Per-config limits without mutating global
        ProtoConfig smallSize = Protovia.withLimits(4, 100);
        ProtoConfig smallDepth = Protovia.withLimits(1024, 2);
        assertTrue(smallSize.is(packedUser, User.class));
        assertThrows(ProtoException.class, () -> smallSize.unpack(packedUser, User.class));
        assertThrows(ProtoException.class, () -> packedUser.unpack(UserProtoCodec.INSTANCE, smallSize));
        assertThrows(ProtoException.class, () -> packedUser.unpack(UserProtoCodec.INSTANCE, 4, 100));
        assertThrows(ProtoException.class, () -> smallDepth.unpack(packedDeep, Node.class));
        assertThrows(ProtoException.class, () -> packedDeep.unpack(NodeCodec.INSTANCE, smallDepth));
        assertThrows(ProtoException.class, () -> packedDeep.unpack(NodeCodec.INSTANCE, 1024, 2));
        assertThrows(IllegalArgumentException.class, () -> packedUser.unpack(UserProtoCodec.INSTANCE, 0, 10));
        assertThrows(IllegalArgumentException.class, () -> packedUser.unpack(UserProtoCodec.INSTANCE, 10, 0));

        // Global limits still untouched
        assertEquals(user, Protovia.unpack(packedUser, User.class));
        assertEquals(deep, Protovia.unpack(packedDeep, Node.class));

        // Global setMaxMessageSize / setMaxDepth apply to both any.unpack(codec) and Protovia.unpack(any, type)
        int prevSize = Protovia.maxMessageSize();
        int prevDepth = Protovia.maxDepth();
        try {
            Protovia.setMaxMessageSize(4);
            assertEquals(4, Protovia.maxMessageSize());
            assertEquals(4, Protovia.config().maxMessageSize());
            assertThrows(ProtoException.class, () -> packedUser.unpack(UserProtoCodec.INSTANCE));
            assertThrows(ProtoException.class, () -> Protovia.unpack(packedUser, User.class));

            Protovia.setMaxMessageSize(prevSize);
            Protovia.setMaxDepth(2);
            assertEquals(2, Protovia.maxDepth());
            assertEquals(2, Protovia.config().maxDepth());
            assertThrows(ProtoException.class, () -> packedDeep.unpack(NodeCodec.INSTANCE));
            assertThrows(ProtoException.class, () -> Protovia.unpack(packedDeep, Node.class));
        } finally {
            Protovia.setMaxMessageSize(prevSize);
            Protovia.setMaxDepth(prevDepth);
        }
    }

    @Test
    void mergeFromFullAndSlice() {
        Protovia.register(MergeDoc.class, MergeDocCodec.INSTANCE);
        Protovia.register(MergeDoc.Meta.class, MergeMetaCodec.INSTANCE);

        MergeDoc base = new MergeDoc(
            "initial",
            10,
            new ArrayList<>(List.of("a", "b")),
            new MergeDoc.Meta("alice", "draft"));

        MergeDoc patch = new MergeDoc(
            "updated",
            0, // omitted scalar: keeps existing 10
            List.of("c"),
            new MergeDoc.Meta("", "published")); // author omitted: keeps "alice", status overwritten

        byte[] patchBytes = Protovia.toBytes(patch);
        MergeDoc merged = Protovia.mergeFrom(patchBytes, base);
        assertEquals("updated", merged.title);
        assertEquals(10, merged.count);
        assertEquals(List.of("a", "b", "c"), merged.tags);
        assertEquals("alice", merged.meta.author);
        assertEquals("published", merged.meta.status);

        // Slice mergeFrom
        MergeDoc patch2 = new MergeDoc(
            "",
            42,
            List.of("d"),
            new MergeDoc.Meta("bob", ""));
        byte[] sliceRaw = Protovia.toBytes(patch2);
        byte[] padded = new byte[sliceRaw.length + 8];
        System.arraycopy(sliceRaw, 0, padded, 3, sliceRaw.length);

        MergeDoc merged2 = Protovia.mergeFrom(padded, 3, sliceRaw.length, merged);
        assertEquals("updated", merged2.title);
        assertEquals(42, merged2.count);
        assertEquals(List.of("a", "b", "c", "d"), merged2.tags);
        assertEquals("bob", merged2.meta.author);
        assertEquals("published", merged2.meta.status);

        // Config limit enforced on mergeFrom
        ProtoConfig tiny = Protovia.withLimits(2, 10);
        assertThrows(ProtoException.class, () -> tiny.mergeFrom(patchBytes, base));
        assertThrows(NullPointerException.class, () -> Protovia.mergeFrom(patchBytes, null));
        assertThrows(NullPointerException.class, () -> Protovia.mergeFrom(null, base));
    }

    @Test
    void byteBufferRoundTripAndZeroMutation() {
        User user = sample();
        ByteBuffer encoded = Protovia.toByteBuffer(user);
        assertEquals(0, encoded.position());
        byte[] raw = Protovia.toBytes(user);
        assertEquals(raw.length, encoded.remaining());

        // 1. Plain heap ByteBuffer
        int heapPos = encoded.position();
        int heapLim = encoded.limit();
        assertEquals(user, Protovia.fromByteBuffer(encoded, User.class));
        assertEquals(heapPos, encoded.position());
        assertEquals(heapLim, encoded.limit());

        // 2. Sliced heap ByteBuffer with non-zero arrayOffset AND non-zero position
        byte[] padded = new byte[raw.length + 12];
        System.arraycopy(raw, 0, padded, 5, raw.length);
        ByteBuffer slicedHeap = ByteBuffer.wrap(padded, 3, raw.length + 5).slice();
        assertEquals(3, slicedHeap.arrayOffset());
        slicedHeap.position(2).limit(2 + raw.length);
        assertEquals(user, Protovia.fromByteBuffer(slicedHeap, User.class));
        assertEquals(2, slicedHeap.position());
        assertEquals(2 + raw.length, slicedHeap.limit());

        // 3. Direct ByteBuffer with non-zero position & limit
        ByteBuffer direct = ByteBuffer.allocateDirect(raw.length + 10);
        direct.position(3);
        direct.put(raw);
        direct.limit(3 + raw.length).position(3);
        assertFalse(direct.hasArray());
        assertEquals(user, Protovia.fromByteBuffer(direct, User.class));
        assertEquals(3, direct.position());
        assertEquals(3 + raw.length, direct.limit());

        // 4. Read-only ByteBuffer with non-zero position & limit
        ByteBuffer readOnly = ByteBuffer.wrap(padded).asReadOnlyBuffer();
        readOnly.position(5).limit(5 + raw.length);
        assertFalse(readOnly.hasArray());
        assertEquals(user, Protovia.fromByteBuffer(readOnly, User.class));
        assertEquals(5, readOnly.position());
        assertEquals(5 + raw.length, readOnly.limit());

        // 5. Empty direct & heap buffers
        ByteBuffer emptyDirect = ByteBuffer.allocateDirect(0);
        User emptyBack = Protovia.fromByteBuffer(emptyDirect, User.class);
        assertNull(emptyBack.getName());
        assertEquals(0, emptyBack.getAge());

        // 6. Oversized direct buffer rejected before allocation, leaving position/limit untouched
        ProtoConfig strict = Protovia.withLimits(4, 10);
        assertThrows(ProtoException.class, () -> strict.fromByteBuffer(direct, User.class));
        assertEquals(3, direct.position());
        assertEquals(3 + raw.length, direct.limit());
        assertThrows(ProtoException.class, () -> strict.fromByteBuffer(slicedHeap, User.class));
        assertEquals(2, slicedHeap.position());
        assertEquals(2 + raw.length, slicedHeap.limit());
    }

    @Test
    void delimitedStreamRoundTripAndErrors() {
        User u1 = sample();
        User u2 = new User();
        u2.setName("x".repeat(200)); // > 127 bytes to exercise multi-byte varint32 length prefix
        u2.setAge(7);
        User empty = new User();

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        Protovia.writeDelimited(out, u1);
        Protovia.writeDelimited(out, empty);
        Protovia.writeDelimited(out, u2);

        // Read back through a 1-byte-at-a-time stream to exercise partial reads
        ByteArrayInputStream rawIn = new ByteArrayInputStream(out.toByteArray());
        FilterInputStream trickleIn = new FilterInputStream(rawIn) {
            @Override
            public int read(byte[] b, int off, int len) throws IOException {
                return super.read(b, off, Math.min(1, len));
            }
        };
        assertEquals(u1, Protovia.readDelimited(trickleIn, User.class));
        User emptyRead = Protovia.readDelimited(trickleIn, User.class);
        assertNotNull(emptyRead);
        assertNull(emptyRead.getName());
        assertEquals(0, emptyRead.getAge());
        assertEquals(u2, Protovia.readDelimited(trickleIn, User.class));
        assertNull(Protovia.readDelimited(trickleIn, User.class)); // clean EOF

        // Standalone empty message is a single 0x00 byte
        ByteArrayOutputStream emptyOut = new ByteArrayOutputStream();
        Protovia.writeDelimited(emptyOut, empty);
        assertArrayEquals(new byte[]{0x00}, emptyOut.toByteArray());
        ByteArrayInputStream emptyIn = new ByteArrayInputStream(emptyOut.toByteArray());
        assertNotNull(Protovia.readDelimited(emptyIn, User.class));
        assertNull(Protovia.readDelimited(emptyIn, User.class));

        // Truncated varint header
        assertThrows(ProtoException.class, () ->
            Protovia.readDelimited(new ByteArrayInputStream(new byte[]{(byte) 0x80}), User.class));

        // Malformed varint header (> 10 continuation bytes)
        byte[] tenCont = new byte[10];
        Arrays.fill(tenCont, (byte) 0x80);
        assertThrows(ProtoException.class, () ->
            Protovia.readDelimited(new ByteArrayInputStream(tenCont), User.class));

        // Negative length header (-1 as 10-byte varint and Integer.MIN_VALUE as 5-byte varint)
        byte[] negOne = {
            (byte) 0xFF, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF,
            (byte) 0xFF, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF, 0x01
        };
        assertThrows(ProtoException.class, () ->
            Protovia.readDelimited(new ByteArrayInputStream(negOne), User.class));
        byte[] minInt = {(byte) 0x80, (byte) 0x80, (byte) 0x80, (byte) 0x80, 0x08};
        assertThrows(ProtoException.class, () ->
            Protovia.readDelimited(new ByteArrayInputStream(minInt), User.class));

        // Truncated body (claims 10 bytes, only 3 follow)
        assertThrows(ProtoException.class, () ->
            Protovia.readDelimited(new ByteArrayInputStream(new byte[]{10, 0x0A, 0x01, 'a'}), User.class));

        // Length > maxMessageSize rejected BEFORE allocating (claims ~1 GB, 0 body bytes)
        ProtoConfig strict = Protovia.withLimits(16, 10);
        byte[] hugeHeader = {(byte) 0x80, (byte) 0x80, (byte) 0x80, (byte) 0x80, 0x04}; // 1 << 30
        assertThrows(ProtoException.class, () ->
            strict.readDelimited(new ByteArrayInputStream(hugeHeader), User.class));

        // writeDelimited respects maxMessageSize without writing partial output
        ByteArrayOutputStream blocked = new ByteArrayOutputStream();
        assertThrows(ProtoException.class, () -> strict.writeDelimited(blocked, u2));
        assertEquals(0, blocked.size());
    }

    @Test
    void codecLookupClearPreservesBuiltinsUnderConcurrency() throws Exception {
        class TempType {}
        ProtoCodec<TempType> tempCodec = new ProtoCodec<>() {
            @Override
            public Class<TempType> type() {
                return TempType.class;
            }

            @Override
            public int computeSize(TempType value) {
                return 0;
            }

            @Override
            public void writeTo(ProtoWriter writer, TempType value) {
            }

            @Override
            public TempType readFrom(ProtoReader reader) {
                return new TempType();
            }
        };

        // Failed lookup does not poison ClassValue; registering later succeeds
        assertThrows(ProtoException.class, () -> CodecLookup.get(TempType.class));
        CodecLookup.register(TempType.class, tempCodec);
        assertSame(tempCodec, CodecLookup.get(TempType.class));

        CodecLookup.clear();
        assertThrows(ProtoException.class, () -> CodecLookup.get(TempType.class));
        assertSame(TimestampCodec.INSTANCE, CodecLookup.get(Instant.class));
        assertSame(DurationCodec.INSTANCE, CodecLookup.get(Duration.class));
        assertSame(AnyCodec.INSTANCE, CodecLookup.get(ProtoAny.class));
        assertSame(Int32Value.INSTANCE, CodecLookup.get(Int32Value.class));

        int readerCount = 4;
        ExecutorService pool = Executors.newFixedThreadPool(readerCount + 1);
        CountDownLatch start = new CountDownLatch(1);
        AtomicBoolean running = new AtomicBoolean(true);
        try {
            List<Future<?>> readers = new ArrayList<>();
            for (int i = 0; i < readerCount; i++) {
                readers.add(pool.submit(() -> {
                    start.await();
                    while (running.get()) {
                        assertSame(TimestampCodec.INSTANCE, CodecLookup.get(Instant.class));
                        assertSame(DurationCodec.INSTANCE, CodecLookup.get(Duration.class));
                        assertSame(AnyCodec.INSTANCE, CodecLookup.get(ProtoAny.class));
                        assertSame(Int32Value.INSTANCE, CodecLookup.get(Int32Value.class));
                    }
                    return null;
                }));
            }
            Future<?> mutator = pool.submit(() -> {
                start.await();
                for (int i = 0; i < 500; i++) {
                    CodecLookup.register(TempType.class, tempCodec);
                    CodecLookup.clear();
                }
                running.set(false);
                return null;
            });
            start.countDown();
            mutator.get(10, TimeUnit.SECONDS);
            for (Future<?> reader : readers) {
                reader.get(10, TimeUnit.SECONDS);
            }
        } finally {
            running.set(false);
            pool.shutdownNow();
            pool.awaitTermination(5, TimeUnit.SECONDS);
            Protovia.register(User.class, UserProtoCodec.INSTANCE);
            Protovia.register(User.Address.class, UserProtoCodec.AddressProtoCodec.INSTANCE);
        }
    }

    @Test
    void needsSizeCacheAndZeroSizeFastPath() {
        assertFalse(TimestampCodec.INSTANCE.needsSizeCache());
        assertFalse(DurationCodec.INSTANCE.needsSizeCache());
        assertFalse(AnyCodec.INSTANCE.needsSizeCache());
        assertFalse(Int32Value.INSTANCE.needsSizeCache());
        assertFalse(StringValue.INSTANCE.needsSizeCache());
        assertFalse(BytesValue.INSTANCE.needsSizeCache());
        assertTrue(UserProtoCodec.INSTANCE.needsSizeCache());

        class FlatValue {
            final int v;

            FlatValue(int v) {
                this.v = v;
            }
        }

        AtomicReference<SizeCache> seenCache = new AtomicReference<>();
        AtomicInteger writeCalls = new AtomicInteger();
        ProtoCodec<FlatValue> spyCodec = new ProtoCodec<>() {
            @Override
            public Class<FlatValue> type() {
                return FlatValue.class;
            }

            @Override
            public boolean needsSizeCache() {
                return false;
            }

            @Override
            public int computeSize(FlatValue value) {
                return value.v == 0 ? 0 : CodedSize.int32(1, value.v);
            }

            @Override
            public int computeSize(FlatValue value, SizeCache cache) {
                seenCache.set(cache);
                return computeSize(value);
            }

            @Override
            public void writeTo(ProtoWriter writer, FlatValue value) {
                writeCalls.incrementAndGet();
                if (value.v != 0) {
                    writer.writeInt32(1, value.v);
                }
            }

            @Override
            public FlatValue readFrom(ProtoReader reader) {
                int v = 0;
                int tag;
                while ((tag = reader.readTag()) != 0) {
                    if (tag == 8) {
                        v = reader.readInt32();
                    } else {
                        reader.skipField();
                    }
                }
                return new FlatValue(v);
            }
        };

        Protovia.register(FlatValue.class, spyCodec);

        // size == 0 fast path: passes SizeCache.NOOP, skips writeTo, returns empty byte[]
        byte[] emptyBytes = Protovia.toBytes(new FlatValue(0));
        assertEquals(0, emptyBytes.length);
        assertSame(SizeCache.NOOP, seenCache.get());
        assertEquals(0, writeCalls.get());

        // non-empty flat message: still passes SizeCache.NOOP and writes normally
        byte[] nonEmpty = Protovia.toBytes(new FlatValue(5));
        assertArrayEquals(new byte[]{0x08, 0x05}, nonEmpty);
        assertSame(SizeCache.NOOP, seenCache.get());
        assertEquals(1, writeCalls.get());

        // pack uses the same fast path
        seenCache.set(null);
        ProtoAny packed = Protovia.pack(new FlatValue(5));
        assertSame(SizeCache.NOOP, seenCache.get());
        assertArrayEquals(new byte[]{0x08, 0x05}, packed.value());
        assertEquals(5, Protovia.unpack(packed, FlatValue.class).v);
    }

    private static final class Node {
        final Node child;

        Node(Node child) {
            this.child = child;
        }

        @Override
        public boolean equals(Object obj) {
            return obj instanceof Node other && Objects.equals(child, other.child);
        }

        @Override
        public int hashCode() {
            return Objects.hashCode(child);
        }
    }

    private static final class NodeCodec implements ProtoCodec<Node> {
        static final NodeCodec INSTANCE = new NodeCodec();

        @Override
        public Class<Node> type() {
            return Node.class;
        }

        @Override
        public int computeSize(Node value) {
            return value.child == null ? 0 : CodedSize.message(1, this, value.child);
        }

        @Override
        public void writeTo(ProtoWriter writer, Node value) {
            if (value.child != null) {
                writer.writeMessage(1, this, value.child);
            }
        }

        @Override
        public Node readFrom(ProtoReader reader) {
            Node child = null;
            int tag;
            while ((tag = reader.readTag()) != 0) {
                if (tag == 10) {
                    child = reader.readMessage(this, child);
                } else {
                    reader.skipField();
                }
            }
            return new Node(child);
        }
    }

    private static final class MergeDoc {
        String title;
        int count;
        List<String> tags;
        Meta meta;

        MergeDoc(String title, int count, List<String> tags, Meta meta) {
            this.title = title;
            this.count = count;
            this.tags = tags;
            this.meta = meta;
        }

        static final class Meta {
            String author;
            String status;

            Meta(String author, String status) {
                this.author = author;
                this.status = status;
            }
        }
    }

    private static final class MergeDocCodec implements ProtoCodec<MergeDoc> {
        static final MergeDocCodec INSTANCE = new MergeDocCodec();

        @Override
        public Class<MergeDoc> type() {
            return MergeDoc.class;
        }

        @Override
        public int computeSize(MergeDoc value) {
            int size = 0;
            if (value.title != null && !value.title.isEmpty()) {
                size += CodedSize.string(1, value.title);
            }
            if (value.count != 0) {
                size += CodedSize.int32(2, value.count);
            }
            if (value.tags != null) {
                for (String t : value.tags) {
                    size += CodedSize.string(3, t);
                }
            }
            if (value.meta != null) {
                size += CodedSize.message(4, MergeMetaCodec.INSTANCE, value.meta);
            }
            return size;
        }

        @Override
        public void writeTo(ProtoWriter writer, MergeDoc value) {
            if (value.title != null && !value.title.isEmpty()) {
                writer.writeString(1, value.title);
            }
            if (value.count != 0) {
                writer.writeInt32(2, value.count);
            }
            if (value.tags != null) {
                for (String t : value.tags) {
                    writer.writeString(3, t);
                }
            }
            if (value.meta != null) {
                writer.writeMessage(4, MergeMetaCodec.INSTANCE, value.meta);
            }
        }

        @Override
        public MergeDoc readFrom(ProtoReader reader) {
            return mergeFrom(reader, new MergeDoc("", 0, new ArrayList<>(), null));
        }

        @Override
        public MergeDoc mergeFrom(ProtoReader reader, MergeDoc existing) {
            MergeDoc target = existing != null ? existing : new MergeDoc("", 0, new ArrayList<>(), null);
            int tag;
            while ((tag = reader.readTag()) != 0) {
                switch (tag) {
                    case 10 -> target.title = reader.readString();
                    case 16 -> target.count = reader.readInt32();
                    case 26 -> {
                        if (target.tags == null) {
                            target.tags = new ArrayList<>();
                        }
                        target.tags.add(reader.readString());
                    }
                    case 34 -> target.meta = reader.readMessage(MergeMetaCodec.INSTANCE, target.meta);
                    default -> reader.skipField();
                }
            }
            return target;
        }
    }

    private static final class MergeMetaCodec implements ProtoCodec<MergeDoc.Meta> {
        static final MergeMetaCodec INSTANCE = new MergeMetaCodec();

        @Override
        public Class<MergeDoc.Meta> type() {
            return MergeDoc.Meta.class;
        }

        @Override
        public int computeSize(MergeDoc.Meta value) {
            int size = 0;
            if (value.author != null && !value.author.isEmpty()) {
                size += CodedSize.string(1, value.author);
            }
            if (value.status != null && !value.status.isEmpty()) {
                size += CodedSize.string(2, value.status);
            }
            return size;
        }

        @Override
        public void writeTo(ProtoWriter writer, MergeDoc.Meta value) {
            if (value.author != null && !value.author.isEmpty()) {
                writer.writeString(1, value.author);
            }
            if (value.status != null && !value.status.isEmpty()) {
                writer.writeString(2, value.status);
            }
        }

        @Override
        public MergeDoc.Meta readFrom(ProtoReader reader) {
            return mergeFrom(reader, new MergeDoc.Meta("", ""));
        }

        @Override
        public MergeDoc.Meta mergeFrom(ProtoReader reader, MergeDoc.Meta existing) {
            MergeDoc.Meta target = existing != null ? existing : new MergeDoc.Meta("", "");
            int tag;
            while ((tag = reader.readTag()) != 0) {
                switch (tag) {
                    case 10 -> target.author = reader.readString();
                    case 18 -> target.status = reader.readString();
                    default -> reader.skipField();
                }
            }
            return target;
        }
    }

    private static User sample() {
        User user = new User();
        user.setName("Ada");
        user.setAge(36);
        user.setTags(List.of("dev", "java"));
        User.Address address = new User.Address();
        address.setCity("Paris");
        address.setStreet("Rue");
        user.setAddress(address);
        user.setScores(Map.of("math", 99, "eng", 70));
        user.setLevel(0);
        return user;
    }
}
