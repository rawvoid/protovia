package io.github.rawvoid.protovia.collect;

import java.util.AbstractList;
import java.util.Arrays;
import java.util.Collection;
import java.util.RandomAccess;

public final class BooleanArrayList extends AbstractList<Boolean> implements RandomAccess {

    private boolean[] values;
    private int size;

    public BooleanArrayList() {
        this(10);
    }

    public BooleanArrayList(int capacity) {
        this.values = new boolean[Math.max(capacity, 1)];
    }

    public BooleanArrayList(Collection<? extends Boolean> values) {
        this(values.size());
        for (Boolean value : values) {
            addBoolean(value);
        }
    }

    public void addBoolean(boolean value) {
        ensureCapacity(size + 1);
        values[size++] = value;
        modCount++;
    }

    public boolean getBoolean(int index) {
        rangeCheck(index);
        return values[index];
    }

    public boolean[] toBooleanArray() {
        return Arrays.copyOf(values, size);
    }

    public void ensureCapacity(int minCapacity) {
        if (minCapacity > values.length) {
            values = Arrays.copyOf(values, Math.max(values.length * 2, minCapacity));
        }
    }

    @Override
    public Boolean get(int index) {
        return getBoolean(index);
    }

    @Override
    public int size() {
        return size;
    }

    @Override
    public boolean add(Boolean value) {
        addBoolean(value);
        return true;
    }

    @Override
    public void add(int index, Boolean value) {
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
    public Boolean set(int index, Boolean value) {
        rangeCheck(index);
        boolean old = values[index];
        values[index] = value;
        return old;
    }

    @Override
    public Boolean remove(int index) {
        rangeCheck(index);
        boolean old = values[index];
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
