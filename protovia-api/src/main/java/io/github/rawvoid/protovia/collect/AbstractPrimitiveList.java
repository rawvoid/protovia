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

    int size;

    @Override
    public final int size() {
        return size;
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

    static int grow(int current, int minCapacity) {
        return Math.max(current * 2, minCapacity);
    }
}
