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
 * {@code List<Boolean>} backed by {@code boolean[]}.
 *
 * @author Rawvoid
 */
public final class BooleanArrayList extends AbstractPrimitiveList<Boolean> {

    private static final boolean[] EMPTY = new boolean[0];

    private boolean[] values;

    public BooleanArrayList() {
        this.values = EMPTY;
    }

    public BooleanArrayList(int capacity) {
        if (capacity < 0) {
            throw new IllegalArgumentException("Illegal capacity: " + capacity);
        }
        this.values = capacity == 0 ? EMPTY : new boolean[capacity];
    }

    public BooleanArrayList(Collection<? extends Boolean> values) {
        Objects.requireNonNull(values, "values");
        if (values instanceof BooleanArrayList other) {
            this.size = other.size;
            this.values = other.size == 0 ? EMPTY : Arrays.copyOf(other.values, other.size);
            return;
        }
        if (values.isEmpty()) {
            this.values = EMPTY;
            return;
        }
        int n = values.size();
        this.values = n > 0 ? new boolean[n] : EMPTY;
        for (Boolean value : values) {
            addBoolean(value);
        }
    }

    public void addBoolean(boolean value) {
        if (size == values.length) {
            values = Arrays.copyOf(values, grow(values.length, size + 1));
        }
        values[size++] = value;
        modCount++;
    }

    public boolean getBoolean(int index) {
        checkElementIndex(index);
        return values[index];
    }

    public boolean[] toBooleanArray() {
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
    public boolean add(Boolean value) {
        addBoolean(value);
        return true;
    }

    @Override
    public Boolean get(int index) {
        return getBoolean(index);
    }

    @Override
    public Boolean set(int index, Boolean value) {
        checkElementIndex(index);
        boolean unboxed = value;
        boolean old = values[index];
        values[index] = unboxed;
        return old;
    }

    @Override
    public void add(int index, Boolean value) {
        checkPositionIndex(index);
        boolean unboxed = value;
        if (size == values.length) {
            values = Arrays.copyOf(values, grow(values.length, size + 1));
        }
        System.arraycopy(values, index, values, index + 1, size - index);
        values[index] = unboxed;
        size++;
        modCount++;
    }

    @Override
    public boolean addAll(Collection<? extends Boolean> c) {
        if (c instanceof BooleanArrayList other) {
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
        for (Boolean value : c) {
            addBoolean(value);
            modified = true;
        }
        return modified;
    }

    @Override
    public Boolean remove(int index) {
        checkElementIndex(index);
        boolean old = values[index];
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
        if (!(o instanceof Boolean v)) {
            return -1;
        }
        boolean target = v;
        for (int i = 0; i < size; i++) {
            if (values[i] == target) {
                return i;
            }
        }
        return -1;
    }

    @Override
    public int lastIndexOf(Object o) {
        if (!(o instanceof Boolean v)) {
            return -1;
        }
        boolean target = v;
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
        if (o instanceof BooleanArrayList other) {
            return size == other.size && Arrays.equals(values, 0, size, other.values, 0, other.size);
        }
        if (!(o instanceof List<?> other) || size != other.size()) {
            return false;
        }
        if (other instanceof RandomAccess) {
            for (int i = 0; i < size; i++) {
                if (!(other.get(i) instanceof Boolean v) || v != values[i]) {
                    return false;
                }
            }
            return true;
        }
        int i = 0;
        for (Object elem : other) {
            if (i >= size || !(elem instanceof Boolean v) || v != values[i++]) {
                return false;
            }
        }
        return i == size;
    }

    @Override
    public int hashCode() {
        int h = 1;
        for (int i = 0; i < size; i++) {
            h = 31 * h + Boolean.hashCode(values[i]);
        }
        return h;
    }
}
