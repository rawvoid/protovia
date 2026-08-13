package io.github.rawvoid.protovia.collect;

import java.util.AbstractList;
import java.util.Arrays;
import java.util.Collection;
import java.util.RandomAccess;

public final class LongArrayList extends AbstractList<Long> implements RandomAccess {

    private long[] values;
    private int size;

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
        rangeCheck(index);
        return values[index];
    }

    public long[] toLongArray() {
        return Arrays.copyOf(values, size);
    }

    public void ensureCapacity(int minCapacity) {
        if (minCapacity > values.length) {
            values = Arrays.copyOf(values, Math.max(values.length * 2, minCapacity));
        }
    }

    @Override
    public Long get(int index) {
        return getLong(index);
    }

    @Override
    public int size() {
        return size;
    }

    @Override
    public boolean add(Long value) {
        addLong(value);
        return true;
    }

    @Override
    public void add(int index, Long value) {
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
    public Long set(int index, Long value) {
        rangeCheck(index);
        long old = values[index];
        values[index] = value;
        return old;
    }

    @Override
    public Long remove(int index) {
        rangeCheck(index);
        long old = values[index];
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
