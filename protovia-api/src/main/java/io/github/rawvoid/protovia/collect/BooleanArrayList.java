package io.github.rawvoid.protovia.collect;

import java.util.Arrays;
import java.util.Collection;

/**
 * {@code List<Boolean>} backed by {@code boolean[]}.
 *
 * @author Rawvoid
 */
public final class BooleanArrayList extends AbstractPrimitiveList<Boolean> {

    private boolean[] values;

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
        checkElementIndex(index);
        return values[index];
    }

    public boolean[] toBooleanArray() {
        return Arrays.copyOf(values, size);
    }

    public void ensureCapacity(int minCapacity) {
        if (minCapacity > values.length) {
            values = Arrays.copyOf(values, grow(values.length, minCapacity));
        }
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
        boolean old = values[index];
        values[index] = value;
        return old;
    }

    @Override
    public void add(int index, Boolean value) {
        checkPositionIndex(index);
        ensureCapacity(size + 1);
        System.arraycopy(values, index, values, index + 1, size - index);
        values[index] = value;
        size++;
        modCount++;
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
}
