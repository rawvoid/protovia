package io.github.rawvoid.protovia.collect;

import java.util.Arrays;
import java.util.Collection;

/** {@code List<Integer>} backed by {@code int[]} so packed decode can avoid boxing. */
public final class IntArrayList extends AbstractPrimitiveList<Integer> {

    private int[] values;

    public IntArrayList() {
        this(10);
    }

    public IntArrayList(int capacity) {
        this.values = new int[Math.max(capacity, 1)];
    }

    public IntArrayList(Collection<? extends Integer> values) {
        this(values.size());
        for (Integer value : values) {
            addInt(value);
        }
    }

    public void addInt(int value) {
        ensureCapacity(size + 1);
        values[size++] = value;
        modCount++;
    }

    public int getInt(int index) {
        checkElementIndex(index);
        return values[index];
    }

    public int[] toIntArray() {
        return Arrays.copyOf(values, size);
    }

    public void ensureCapacity(int minCapacity) {
        if (minCapacity > values.length) {
            values = Arrays.copyOf(values, grow(values.length, minCapacity));
        }
    }

    @Override
    public Integer get(int index) {
        return getInt(index);
    }

    @Override
    public boolean add(Integer value) {
        addInt(value);
        return true;
    }

    @Override
    public void add(int index, Integer value) {
        checkPositionIndex(index);
        ensureCapacity(size + 1);
        System.arraycopy(values, index, values, index + 1, size - index);
        values[index] = value;
        size++;
        modCount++;
    }

    @Override
    public Integer set(int index, Integer value) {
        checkElementIndex(index);
        int old = values[index];
        values[index] = value;
        return old;
    }

    @Override
    public Integer remove(int index) {
        checkElementIndex(index);
        int old = values[index];
        int moved = size - index - 1;
        if (moved > 0) {
            System.arraycopy(values, index + 1, values, index, moved);
        }
        size--;
        modCount++;
        return old;
    }
}
