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

package io.github.rawvoid.protovia.wire;

import java.util.Arrays;
import java.util.function.IntSupplier;

/**
 * Per-serialization table of length-delimited sizes (nested messages, packed blobs, map entries)
 * and auxiliary cached objects (such as sorted deterministic map entries).
 *
 * <p>Slots are reserved in pre-order before recursing, then filled with the computed size. Writers
 * consume the same slots in pre-order, so a nested {@code computeSize} never runs twice. Backing
 * arrays are allocated lazily so flat messages allocate neither array.
 *
 * <p>{@link #NOOP} discards reservations and cached objects; {@link #take(IntSupplier)} then runs
 * the fallback.
 *
 * @author Rawvoid
 */
public final class SizeCache {

    public static final SizeCache NOOP = new SizeCache(true);
    private static final int INITIAL_CAPACITY = 8;
    private static final int MAX_ARRAY_SIZE = Integer.MAX_VALUE - 8;

    private final boolean noop;
    private int[] slots;
    private int writePos;
    private int readPos;
    private Object[] objects;
    private int objWritePos;
    private int objReadPos;

    public SizeCache() {
        this(false);
    }

    private SizeCache(boolean noop) {
        this.noop = noop;
    }

    public int reserve() {
        if (noop) {
            return -1;
        }
        if (slots == null) {
            slots = new int[INITIAL_CAPACITY];
        } else if (writePos == slots.length) {
            slots = Arrays.copyOf(slots, growCapacity(slots.length));
        }
        return writePos++;
    }

    public void set(int index, int size) {
        if (index >= 0) {
            slots[index] = size;
        }
    }

    public void push(int size) {
        set(reserve(), size);
    }

    public boolean hasNext() {
        return !noop && readPos < writePos;
    }

    public int take() {
        if (!hasNext()) {
            throw new IllegalStateException("SizeCache is empty");
        }
        return slots[readPos++];
    }

    public int take(IntSupplier fallback) {
        return hasNext() ? slots[readPos++] : fallback.getAsInt();
    }

    public void pushObject(Object value) {
        if (noop) {
            return;
        }
        if (objects == null) {
            objects = new Object[INITIAL_CAPACITY];
        } else if (objWritePos == objects.length) {
            objects = Arrays.copyOf(objects, growCapacity(objects.length));
        }
        objects[objWritePos++] = value;
    }

    public boolean hasNextObject() {
        return !noop && objReadPos < objWritePos;
    }

    public Object takeObject() {
        if (!hasNextObject()) {
            throw new IllegalStateException("SizeCache object cache is empty");
        }
        Object value = objects[objReadPos];
        objects[objReadPos++] = null;
        return value;
    }

    public void reset() {
        if (noop) {
            return;
        }
        writePos = 0;
        readPos = 0;
        if (objects != null && objReadPos < objWritePos) {
            Arrays.fill(objects, objReadPos, objWritePos, null);
        }
        objWritePos = 0;
        objReadPos = 0;
    }

    private static int growCapacity(int currentLength) {
        if (currentLength >= MAX_ARRAY_SIZE) {
            throw new IllegalStateException("SizeCache capacity overflow");
        }
        return currentLength > (MAX_ARRAY_SIZE >>> 1) ? MAX_ARRAY_SIZE : currentLength << 1;
    }
}
