package io.github.rawvoid.protovia.wire;

import java.util.function.IntSupplier;

/**
 * Per-serialization table of length-delimited sizes (nested messages, packed blobs, map entries).
 *
 * <p>Slots are reserved in pre-order before recursing, then filled with the computed size. Writers
 * consume the same slots in pre-order, so a nested {@code computeSize} never runs twice.
 *
 * <p>{@link #NOOP} discards reservations; {@link #take(IntSupplier)} then runs the fallback.
 *
 * @author Rawvoid
 */
public final class SizeCache {

    public static final SizeCache NOOP = new SizeCache(true);

    private final boolean noop;
    private int[] slots;
    private int writePos;
    private int readPos;

    public SizeCache() {
        this(false);
    }

    private SizeCache(boolean noop) {
        this.noop = noop;
        this.slots = noop ? null : new int[8];
    }

    public int reserve() {
        if (noop) {
            return -1;
        }
        if (writePos == slots.length) {
            int[] grown = new int[slots.length * 2];
            System.arraycopy(slots, 0, grown, 0, slots.length);
            slots = grown;
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

    public void reset() {
        writePos = 0;
        readPos = 0;
    }
}
