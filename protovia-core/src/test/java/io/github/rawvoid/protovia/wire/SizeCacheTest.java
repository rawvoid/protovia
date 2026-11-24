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

import io.github.rawvoid.protovia.codec.ProtoCodec;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;

import static org.junit.jupiter.api.Assertions.*;

/**
 * @author Rawvoid
 */
class SizeCacheTest {

    @Test
    void reserveBeforeRecurseMatchesWriteOrder() {
        SizeCache cache = new SizeCache();
        int parentSlot = cache.reserve();
        int childSlot = cache.reserve();
        cache.set(childSlot, 3);
        cache.set(parentSlot, 5);
        cache.push(7);

        assertTrue(cache.hasNext());
        assertEquals(5, cache.take());
        assertEquals(3, cache.take());
        assertEquals(7, cache.take());
        assertFalse(cache.hasNext());
    }

    @Test
    void noopAlwaysFallsBack() {
        assertFalse(SizeCache.NOOP.hasNext());
        SizeCache.NOOP.push(4);
        assertEquals(9, SizeCache.NOOP.take(() -> 9));
        assertThrows(IllegalStateException.class, SizeCache.NOOP::take);

        assertFalse(SizeCache.NOOP.hasNextObject());
        SizeCache.NOOP.pushObject("ignored");
        assertFalse(SizeCache.NOOP.hasNextObject());
        assertThrows(IllegalStateException.class, SizeCache.NOOP::takeObject);
        SizeCache.NOOP.reset();
    }

    @Test
    void lazyAllocationDefersBackingArraysUntilUsed() throws Exception {
        Field slotsField = SizeCache.class.getDeclaredField("slots");
        slotsField.setAccessible(true);
        Field objectsField = SizeCache.class.getDeclaredField("objects");
        objectsField.setAccessible(true);

        SizeCache unused = new SizeCache();
        assertNull(slotsField.get(unused));
        assertNull(objectsField.get(unused));
        assertFalse(unused.hasNext());
        assertFalse(unused.hasNextObject());
        assertThrows(IllegalStateException.class, unused::take);
        assertThrows(IllegalStateException.class, unused::takeObject);
        unused.reset();
        assertNull(slotsField.get(unused));
        assertNull(objectsField.get(unused));

        SizeCache sizesOnly = new SizeCache();
        sizesOnly.push(42);
        assertNotNull(slotsField.get(sizesOnly));
        assertNull(objectsField.get(sizesOnly));
        assertEquals(42, sizesOnly.take());

        SizeCache objectsOnly = new SizeCache();
        objectsOnly.pushObject("hello");
        assertNull(slotsField.get(objectsOnly));
        assertNotNull(objectsField.get(objectsOnly));
        assertEquals("hello", objectsOnly.takeObject());
    }

    @Test
    void objectCacheFifoGrowthResetAndProtoWriterForwarding() throws Exception {
        SizeCache cache = new SizeCache();
        for (int i = 0; i < 20; i++) {
            cache.push(i * 10);
            cache.pushObject("obj-" + i);
        }
        cache.pushObject(null);

        ProtoWriter writer = new ProtoWriter(0, cache);
        for (int i = 0; i < 20; i++) {
            assertTrue(writer.hasCachedSize());
            assertEquals(i * 10, writer.takeSize());
            assertTrue(writer.hasCachedObject());
            String value = writer.takeObject();
            assertEquals("obj-" + i, value);
        }
        assertTrue(writer.hasCachedObject());
        assertNull(writer.takeObject());
        assertFalse(writer.hasCachedSize());
        assertFalse(writer.hasCachedObject());
        assertThrows(IllegalStateException.class, writer::takeObject);

        // Push unread objects, then reset and verify references are cleared and cache is reusable
        cache.push(7);
        cache.pushObject("stale-1");
        cache.pushObject("stale-2");
        cache.reset();
        assertFalse(cache.hasNext());
        assertFalse(cache.hasNextObject());
        assertThrows(IllegalStateException.class, cache::take);
        assertThrows(IllegalStateException.class, cache::takeObject);

        Field objectsField = SizeCache.class.getDeclaredField("objects");
        objectsField.setAccessible(true);
        Object[] backing = (Object[]) objectsField.get(cache);
        for (Object slot : backing) {
            assertNull(slot);
        }

        // Reuse after reset
        cache.push(99);
        cache.pushObject("fresh");
        assertTrue(cache.hasNext());
        assertTrue(cache.hasNextObject());
        assertEquals(99, cache.take());
        assertEquals("fresh", cache.takeObject());

        // ProtoWriter with SizeCache.NOOP
        ProtoWriter noopWriter = new ProtoWriter(0);
        assertFalse(noopWriter.hasCachedObject());
        assertThrows(IllegalStateException.class, noopWriter::takeObject);
    }

    @Test
    void nestedMessageComputeSizeRunsOnce() {
        CountingCodec child = new CountingCodec();
        ParentCodec parent = new ParentCodec(child);
        Nested inner = new Nested(150);
        Nested root = new Nested(1);
        root.child = inner;

        SizeCache cache = new SizeCache();
        int size = parent.computeSize(root, cache);
        ProtoWriter writer = new ProtoWriter(size, cache);
        parent.writeTo(writer, root);
        writer.finish();

        assertEquals(1, child.sizeCalls);
        assertEquals(size, writer.capacity());
    }

    @Test
    void handwrittenWriteMessageDoesNotStealSiblingSlot() {
        HandwrittenNested handwritten = new HandwrittenNested();
        Nested grand = new Nested(9);
        Nested mid = new Nested(3);
        mid.child = grand;

        SizeCache cache = new SizeCache();
        int slot = cache.reserve();
        cache.set(slot, handwritten.computeSize(mid));
        cache.push(99);

        ProtoWriter writer = new ProtoWriter(64, cache);
        writer.writeMessage(1, handwritten, mid);
        assertEquals(handwritten.computeSize(mid), writer.takeSize());
        assertEquals(99, writer.takeSize());
    }

    static final class Nested {
        final int a;
        Nested child;

        Nested(int a) {
            this.a = a;
        }
    }

    static final class CountingCodec implements ProtoCodec<Nested> {
        int sizeCalls;

        @Override
        public Class<Nested> type() {
            return Nested.class;
        }

        @Override
        public int computeSize(Nested value) {
            return computeSize(value, SizeCache.NOOP);
        }

        @Override
        public int computeSize(Nested value, SizeCache cache) {
            sizeCalls++;
            int size = 0;
            if (value.a != 0) {
                size += CodedSize.int32(1, value.a);
            }
            return size;
        }

        @Override
        public void writeTo(ProtoWriter writer, Nested value) {
            if (value.a != 0) {
                writer.writeInt32(1, value.a);
            }
        }

        @Override
        public Nested readFrom(ProtoReader reader) {
            throw new UnsupportedOperationException();
        }
    }

    static final class HandwrittenNested implements ProtoCodec<Nested> {
        @Override
        public Class<Nested> type() {
            return Nested.class;
        }

        @Override
        public int computeSize(Nested value) {
            int size = 0;
            if (value.a != 0) {
                size += CodedSize.int32(1, value.a);
            }
            if (value.child != null) {
                size += CodedSize.message(2, this, value.child);
            }
            return size;
        }

        @Override
        public void writeTo(ProtoWriter writer, Nested value) {
            if (value.a != 0) {
                writer.writeInt32(1, value.a);
            }
            if (value.child != null) {
                writer.writeMessage(2, this, value.child);
            }
        }

        @Override
        public Nested readFrom(ProtoReader reader) {
            throw new UnsupportedOperationException();
        }
    }

    static final class ParentCodec implements ProtoCodec<Nested> {
        private final CountingCodec child;

        ParentCodec(CountingCodec child) {
            this.child = child;
        }

        @Override
        public Class<Nested> type() {
            return Nested.class;
        }

        @Override
        public int computeSize(Nested value) {
            return computeSize(value, SizeCache.NOOP);
        }

        @Override
        public int computeSize(Nested value, SizeCache cache) {
            int size = 0;
            if (value.a != 0) {
                size += CodedSize.int32(1, value.a);
            }
            if (value.child != null) {
                int slot = cache.reserve();
                int childSize = child.computeSize(value.child, cache);
                cache.set(slot, childSize);
                size += CodedSize.message(2, childSize);
            }
            return size;
        }

        @Override
        public void writeTo(ProtoWriter writer, Nested value) {
            if (value.a != 0) {
                writer.writeInt32(1, value.a);
            }
            if (value.child != null) {
                writer.writeTag(2, WireType.LEN);
                int childSize = writer.takeSize(() -> child.computeSize(value.child));
                writer.writeUInt32NoTag(childSize);
                child.writeTo(writer, value.child);
            }
        }

        @Override
        public Nested readFrom(ProtoReader reader) {
            throw new UnsupportedOperationException();
        }
    }
}
