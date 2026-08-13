package io.github.rawvoid.protovia.wire;

import io.github.rawvoid.protovia.codec.ProtoCodec;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
                writer.writeMessage(2, child, value.child);
            }
        }

        @Override
        public Nested readFrom(ProtoReader reader) {
            throw new UnsupportedOperationException();
        }
    }
}
