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

/**
 * {@code List<Long>} backed by {@code long[]}.
 *
 * @author Rawvoid
 */
public final class LongArrayList extends AbstractPrimitiveList<Long> {

    private long[] values;

    public LongArrayList() {
        this(10);
    }

    public LongArrayList(int capacity) {
        this.values = new long[Math.max(capacity, 1)];
    }

    public LongArrayList(Collection<? extends Long> values) {
        this(values.size());
        for (Long value : values) {
            addLong(value);
        }
    }

    public void addLong(long value) {
        ensureCapacity(size + 1);
        values[size++] = value;
        modCount++;
    }

    public long getLong(int index) {
        checkElementIndex(index);
        return values[index];
    }

    public long[] toLongArray() {
        return Arrays.copyOf(values, size);
    }

    public void ensureCapacity(int minCapacity) {
        if (minCapacity > values.length) {
            values = Arrays.copyOf(values, grow(values.length, minCapacity));
        }
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
        long old = values[index];
        values[index] = value;
        return old;
    }

    @Override
    public void add(int index, Long value) {
        checkPositionIndex(index);
        ensureCapacity(size + 1);
        System.arraycopy(values, index, values, index + 1, size - index);
        values[index] = value;
        size++;
        modCount++;
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
}
