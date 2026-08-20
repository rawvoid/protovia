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

import java.util.Arrays;
import java.util.Collection;

/**
 * {@code List<Double>} backed by {@code double[]}.
 *
 * @author Rawvoid
 */
public final class DoubleArrayList extends AbstractPrimitiveList<Double> {

    private double[] values;

    public DoubleArrayList() {
        this(10);
    }

    public DoubleArrayList(int capacity) {
        this.values = new double[Math.max(capacity, 1)];
    }

    public DoubleArrayList(Collection<? extends Double> values) {
        this(values.size());
        for (Double value : values) {
            addDouble(value);
        }
    }

    public void addDouble(double value) {
        ensureCapacity(size + 1);
        values[size++] = value;
        modCount++;
    }

    public double getDouble(int index) {
        checkElementIndex(index);
        return values[index];
    }

    public double[] toDoubleArray() {
        return Arrays.copyOf(values, size);
    }

    public void ensureCapacity(int minCapacity) {
        if (minCapacity > values.length) {
            values = Arrays.copyOf(values, grow(values.length, minCapacity));
        }
    }

    @Override
    public boolean add(Double value) {
        addDouble(value);
        return true;
    }

    @Override
    public Double get(int index) {
        return getDouble(index);
    }

    @Override
    public Double set(int index, Double value) {
        checkElementIndex(index);
        double old = values[index];
        values[index] = value;
        return old;
    }

    @Override
    public void add(int index, Double value) {
        checkPositionIndex(index);
        ensureCapacity(size + 1);
        System.arraycopy(values, index, values, index + 1, size - index);
        values[index] = value;
        size++;
        modCount++;
    }

    @Override
    public Double remove(int index) {
        checkElementIndex(index);
        double old = values[index];
        int moved = size - index - 1;
        if (moved > 0) {
            System.arraycopy(values, index + 1, values, index, moved);
        }
        size--;
        modCount++;
        return old;
    }
}
