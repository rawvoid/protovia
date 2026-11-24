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

package io.github.rawvoid.protovia.collect;

import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.RandomAccess;

/**
 * {@code List<Long>} backed by {@code long[]}.
 *
 * @author Rawvoid
 */
public final class LongArrayList extends AbstractPrimitiveList<Long> {

    private static final long[] EMPTY = new long[0];

    private long[] values;

    public LongArrayList() {
        this.values = EMPTY;
    }

    public LongArrayList(int capacity) {
        if (capacity < 0) {
            throw new IllegalArgumentException("Illegal capacity: " + capacity);
        }
        this.values = capacity == 0 ? EMPTY : new long[capacity];
    }

    public LongArrayList(Collection<? extends Long> values) {
        Objects.requireNonNull(values, "values");
        if (values instanceof LongArrayList other) {
            this.size = other.size;
            this.values = other.size == 0 ? EMPTY : Arrays.copyOf(other.values, other.size);
            return;
        }
        if (values.isEmpty()) {
            this.values = EMPTY;
            return;
        }
        int n = values.size();
        this.values = n > 0 ? new long[n] : EMPTY;
        for (Long value : values) {
            addLong(value);
        }
    }

    public void addLong(long value) {
        if (size == values.length) {
            values = Arrays.copyOf(values, grow(values.length, size + 1));
        }
        values[size++] = value;
        modCount++;
    }

    public long getLong(int index) {
        checkElementIndex(index);
        return values[index];
    }

    public long[] toLongArray() {
        return size == 0 ? EMPTY : Arrays.copyOf(values, size);
    }

    public void ensureCapacity(int minCapacity) {
        if (minCapacity > values.length) {
            values = Arrays.copyOf(values, grow(values.length, minCapacity));
        }
    }

    int capacity() {
        return values.length;
    }

    @Override
    public boolean add(Long value) {
        addLong(value);
        return true;
    }

    @Override
    public Long get(int index) {
        return getLong(index);
    }

    @Override
    public Long set(int index, Long value) {
        checkElementIndex(index);
        long unboxed = value;
        long old = values[index];
        values[index] = unboxed;
        return old;
    }

    @Override
    public void add(int index, Long value) {
        checkPositionIndex(index);
        long unboxed = value;
        if (size == values.length) {
            values = Arrays.copyOf(values, grow(values.length, size + 1));
        }
        System.arraycopy(values, index, values, index + 1, size - index);
        values[index] = unboxed;
        size++;
        modCount++;
    }

    @Override
    public boolean addAll(Collection<? extends Long> c) {
        if (c instanceof LongArrayList other) {
            int n = other.size;
            if (n == 0) {
                return false;
            }
            ensureCapacity(checkedCapacity(size, n));
            System.arraycopy(other.values, 0, values, size, n);
            size += n;
            modCount++;
            return true;
        }
        int n = c.size();
        if (n > 0) {
            ensureCapacity(checkedCapacity(size, n));
        }
        boolean modified = false;
        for (Long value : c) {
            addLong(value);
            modified = true;
        }
        return modified;
    }

    @Override
    public Long remove(int index) {
        checkElementIndex(index);
        long old = values[index];
        int moved = size - index - 1;
        if (moved > 0) {
            System.arraycopy(values, index + 1, values, index, moved);
        }
        size--;
        modCount++;
        return old;
    }

    @Override
    protected void removeRange(int fromIndex, int toIndex) {
        checkRange(fromIndex, toIndex);
        modCount++;
        int removed = toIndex - fromIndex;
        if (removed > 0) {
            int moved = size - toIndex;
            if (moved > 0) {
                System.arraycopy(values, toIndex, values, fromIndex, moved);
            }
            size -= removed;
        }
    }

    @Override
    public int indexOf(Object o) {
        if (!(o instanceof Long v)) {
            return -1;
        }
        long target = v;
        for (int i = 0; i < size; i++) {
            if (values[i] == target) {
                return i;
            }
        }
        return -1;
    }

    @Override
    public int lastIndexOf(Object o) {
        if (!(o instanceof Long v)) {
            return -1;
        }
        long target = v;
        for (int i = size - 1; i >= 0; i--) {
            if (values[i] == target) {
                return i;
            }
        }
        return -1;
    }

    @Override
    public boolean contains(Object o) {
        return indexOf(o) >= 0;
    }

    @Override
    public boolean equals(Object o) {
        if (o == this) {
            return true;
        }
        if (o instanceof LongArrayList other) {
            return size == other.size && Arrays.equals(values, 0, size, other.values, 0, other.size);
        }
        if (!(o instanceof List<?> other) || size != other.size()) {
            return false;
        }
        if (other instanceof RandomAccess) {
            for (int i = 0; i < size; i++) {
                if (!(other.get(i) instanceof Long v) || v != values[i]) {
                    return false;
                }
            }
            return true;
        }
        int i = 0;
        for (Object elem : other) {
            if (i >= size || !(elem instanceof Long v) || v != values[i++]) {
                return false;
            }
        }
        return i == size;
    }

    @Override
    public int hashCode() {
        int h = 1;
        for (int i = 0; i < size; i++) {
            h = 31 * h + Long.hashCode(values[i]);
        }
        return h;
    }
}
