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

import java.util.AbstractList;
import java.util.RandomAccess;

/**
 * Shared {@link java.util.List} scaffolding for packed scalar storage backed by a primitive array.
 *
 * @param <E> boxed element type
 * @author Rawvoid
 */
abstract class AbstractPrimitiveList<E> extends AbstractList<E> implements RandomAccess {

    private static final int DEFAULT_CAPACITY = 10;
    static final int MAX_ARRAY_SIZE = Integer.MAX_VALUE - 8;

    int size;

    @Override
    public final int size() {
        return size;
    }

    @Override
    public final void clear() {
        size = 0;
        modCount++;
    }

    final void checkElementIndex(int index) {
        if (index < 0 || index >= size) {
            throw new IndexOutOfBoundsException("index=" + index + " size=" + size);
        }
    }

    final void checkPositionIndex(int index) {
        if (index < 0 || index > size) {
            throw new IndexOutOfBoundsException("index=" + index + " size=" + size);
        }
    }

    final void checkRange(int fromIndex, int toIndex) {
        if (fromIndex < 0 || toIndex > size || fromIndex > toIndex) {
            throw new IndexOutOfBoundsException(
                "fromIndex=" + fromIndex + " toIndex=" + toIndex + " size=" + size);
        }
    }

    static int checkedCapacity(int currentSize, int additional) {
        int target = currentSize + additional;
        if (target < 0) {
            throw new OutOfMemoryError("list capacity overflow");
        }
        return target;
    }

    static int grow(int current, int minCapacity) {
        if (minCapacity < 0) {
            throw new OutOfMemoryError("list capacity overflow");
        }
        if (current <= 0) {
            return Math.max(DEFAULT_CAPACITY, minCapacity);
        }
        int grown = current > (MAX_ARRAY_SIZE >>> 1) ? MAX_ARRAY_SIZE : current << 1;
        return Math.max(grown, minCapacity);
    }
}
