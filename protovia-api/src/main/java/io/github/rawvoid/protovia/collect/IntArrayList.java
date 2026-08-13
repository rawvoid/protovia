package io.github.rawvoid.protovia.collect;

import java.util.AbstractList;
import java.util.Arrays;
import java.util.RandomAccess;

/**
 * {@link java.util.List} of {@link Integer} backed by {@code int[]}, matching protobuf-java
 * {@code IntArrayList} for packed / repeated scalar decode without per-element boxing.
 */
public final class IntArrayList extends AbstractList<Integer> implements RandomAccess {

    private int[] values;
    private int size;

    public IntArrayList() {
        this(10);
    }

    public IntArrayList(int capacity) {
        this.values = new int[Math.max(capacity, 1)];
    }

    public void addInt(int value) {
        ensureCapacity(size + 1);
        values[size++] = value;
        modCount++;
    }

    public int getInt(int index) {
        rangeCheck(index);
        return values[index];
    }

    public int[] toIntArray() {
        return Arrays.copyOf(values, size);
    }

    public void ensureCapacity(int minCapacity) {
        if (minCapacity > values.length) {
            int next = Math.max(values.length * 2, minCapacity);
            values = Arrays.copyOf(values, next);
        }
    }

    @Override
    public Integer get(int index) {
        return getInt(index);
    }

    @Override
    public int size() {
        return size;
    }

    @Override
    public boolean add(Integer value) {
        addInt(value);
        return true;
    }

    @Override
    public void add(int index, Integer value) {
        if (index < 0 || index > size) {
            throw new IndexOutOfBoundsException("index=" + index + " size=" + size);
        }
        ensureCapacity(size + 1);
        System.arraycopy(values, index, values, index + 1, size - index);
        values[index] = value;
        size++;
        modCount++;
    }

    @Override
    public Integer set(int index, Integer value) {
        rangeCheck(index);
        int old = values[index];
        values[index] = value;
        return old;
    }

    @Override
    public Integer remove(int index) {
        rangeCheck(index);
        int old = values[index];
        int moved = size - index - 1;
        if (moved > 0) {
            System.arraycopy(values, index + 1, values, index, moved);
        }
        size--;
        modCount++;
        return old;
    }

    private void rangeCheck(int index) {
        if (index < 0 || index >= size) {
            throw new IndexOutOfBoundsException("index=" + index + " size=" + size);
        }
    }
}
