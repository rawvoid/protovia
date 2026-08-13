package io.github.rawvoid.protovia.collect;

import java.util.Arrays;
import java.util.Collection;

public final class FloatArrayList extends AbstractPrimitiveList<Float> {

    private float[] values;

    public FloatArrayList() {
        this(10);
    }

    public FloatArrayList(int capacity) {
        this.values = new float[Math.max(capacity, 1)];
    }

    public FloatArrayList(Collection<? extends Float> values) {
        this(values.size());
        for (Float value : values) {
            addFloat(value);
        }
    }

    public void addFloat(float value) {
        ensureCapacity(size + 1);
        values[size++] = value;
        modCount++;
    }

    public float getFloat(int index) {
        checkElementIndex(index);
        return values[index];
    }

    public float[] toFloatArray() {
        return Arrays.copyOf(values, size);
    }

    public void ensureCapacity(int minCapacity) {
        if (minCapacity > values.length) {
            values = Arrays.copyOf(values, grow(values.length, minCapacity));
        }
    }

    @Override
    public Float get(int index) {
        return getFloat(index);
    }

    @Override
    public boolean add(Float value) {
        addFloat(value);
        return true;
    }

    @Override
    public void add(int index, Float value) {
        checkPositionIndex(index);
        ensureCapacity(size + 1);
        System.arraycopy(values, index, values, index + 1, size - index);
        values[index] = value;
        size++;
        modCount++;
    }

    @Override
    public Float set(int index, Float value) {
        checkElementIndex(index);
        float old = values[index];
        values[index] = value;
        return old;
    }

    @Override
    public Float remove(int index) {
        checkElementIndex(index);
        float old = values[index];
        int moved = size - index - 1;
        if (moved > 0) {
            System.arraycopy(values, index + 1, values, index, moved);
        }
        size--;
        modCount++;
        return old;
    }
}
