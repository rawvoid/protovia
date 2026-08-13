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
 * {@code List<Float>} backed by {@code float[]}.
 *
 * @author Rawvoid
 */
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
    public boolean add(Float value) {
        addFloat(value);
        return true;
    }

    @Override
    public Float get(int index) {
        return getFloat(index);
    }

    @Override
    public Float set(int index, Float value) {
        checkElementIndex(index);
        float old = values[index];
        values[index] = value;
        return old;
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
