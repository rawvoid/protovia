package io.github.rawvoid.protovia.collect;

import java.util.AbstractList;
import java.util.Arrays;
import java.util.RandomAccess;

public final class FloatArrayList extends AbstractList<Float> implements RandomAccess {

    private float[] values;
    private int size;

    public FloatArrayList() {
        this(10);
    }

    public FloatArrayList(int capacity) {
        this.values = new float[Math.max(capacity, 1)];
    }

    public void addFloat(float value) {
        ensureCapacity(size + 1);
        values[size++] = value;
        modCount++;
    }

    public float getFloat(int index) {
        rangeCheck(index);
        return values[index];
    }

    public float[] toFloatArray() {
        return Arrays.copyOf(values, size);
    }

    public void ensureCapacity(int minCapacity) {
        if (minCapacity > values.length) {
            values = Arrays.copyOf(values, Math.max(values.length * 2, minCapacity));
        }
    }

    @Override
    public Float get(int index) {
        return getFloat(index);
    }

    @Override
    public int size() {
        return size;
    }

    @Override
    public boolean add(Float value) {
        addFloat(value);
        return true;
    }

    @Override
    public void add(int index, Float value) {
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
    public Float set(int index, Float value) {
        rangeCheck(index);
        float old = values[index];
        values[index] = value;
        return old;
    }

    @Override
    public Float remove(int index) {
        rangeCheck(index);
        float old = values[index];
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
