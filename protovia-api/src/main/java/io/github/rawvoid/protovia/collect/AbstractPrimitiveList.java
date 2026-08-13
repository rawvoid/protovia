package io.github.rawvoid.protovia.collect;

import java.util.AbstractList;
import java.util.RandomAccess;

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
