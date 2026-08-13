package io.github.rawvoid.protovia.collect;

import java.util.AbstractList;
import java.util.Arrays;
import java.util.RandomAccess;

public final class DoubleArrayList extends AbstractList<Double> implements RandomAccess {

    private double[] values;
    private int size;

    public DoubleArrayList() {
        this(10);
    }

    public DoubleArrayList(int capacity) {
        this.values = new double[Math.max(capacity, 1)];
    }

    public void addDouble(double value) {
        ensureCapacity(size + 1);
        values[size++] = value;
        modCount++;
    }

    public double getDouble(int index) {
        rangeCheck(index);
        return values[index];
    }

    public double[] toDoubleArray() {
        return Arrays.copyOf(values, size);
    }

    public void ensureCapacity(int minCapacity) {
        if (minCapacity > values.length) {
            values = Arrays.copyOf(values, Math.max(values.length * 2, minCapacity));
        }
    }

    @Override
    public Double get(int index) {
        return getDouble(index);
    }

    @Override
    public int size() {
        return size;
    }

    @Override
    public boolean add(Double value) {
        addDouble(value);
        return true;
    }

    @Override
    public void add(int index, Double value) {
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
    public Double set(int index, Double value) {
        rangeCheck(index);
        double old = values[index];
        values[index] = value;
        return old;
    }

    @Override
    public Double remove(int index) {
        rangeCheck(index);
        double old = values[index];
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
