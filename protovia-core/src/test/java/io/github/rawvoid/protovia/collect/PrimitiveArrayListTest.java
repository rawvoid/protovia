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

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.ArrayList;
import java.util.ConcurrentModificationException;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * @author Rawvoid
 */
class PrimitiveArrayListTest {

    @Test
    void listContractOnEveryPrimitiveType() {
        assertListContract(new IntArrayList(), 1, 2, 3);
        assertListContract(new LongArrayList(), 1L, 2L, 3L);
        assertListContract(new FloatArrayList(), 1f, 2f, 3f);
        assertListContract(new DoubleArrayList(), 1d, 2d, 3d);
        assertListContract(new BooleanArrayList(), true, false, true);
    }

    @Test
    void defaultAndZeroCapacityConstructorsAllocateEmptyBacking() {
        IntArrayList ints = new IntArrayList();
        assertTrue(ints.isEmpty());
        assertEquals(0, ints.capacity());
        assertEquals(0, new IntArrayList(0).capacity());
        assertEquals(0, new IntArrayList(List.of()).capacity());
        assertSame(ints.toIntArray(), new IntArrayList().toIntArray());

        LongArrayList longs = new LongArrayList();
        assertTrue(longs.isEmpty());
        assertEquals(0, longs.capacity());
        assertEquals(0, new LongArrayList(0).capacity());
        assertEquals(0, new LongArrayList(List.of()).capacity());
        assertSame(longs.toLongArray(), new LongArrayList().toLongArray());

        FloatArrayList floats = new FloatArrayList();
        assertTrue(floats.isEmpty());
        assertEquals(0, floats.capacity());
        assertEquals(0, new FloatArrayList(0).capacity());
        assertEquals(0, new FloatArrayList(List.of()).capacity());
        assertSame(floats.toFloatArray(), new FloatArrayList().toFloatArray());

        DoubleArrayList doubles = new DoubleArrayList();
        assertTrue(doubles.isEmpty());
        assertEquals(0, doubles.capacity());
        assertEquals(0, new DoubleArrayList(0).capacity());
        assertEquals(0, new DoubleArrayList(List.of()).capacity());
        assertSame(doubles.toDoubleArray(), new DoubleArrayList().toDoubleArray());

        BooleanArrayList booleans = new BooleanArrayList();
        assertTrue(booleans.isEmpty());
        assertEquals(0, booleans.capacity());
        assertEquals(0, new BooleanArrayList(0).capacity());
        assertEquals(0, new BooleanArrayList(List.of()).capacity());
        assertSame(booleans.toBooleanArray(), new BooleanArrayList().toBooleanArray());
    }

    @Test
    void negativeCapacityThrowsIllegalArgumentException() {
        assertThrows(IllegalArgumentException.class, () -> new IntArrayList(-1));
        assertThrows(IllegalArgumentException.class, () -> new LongArrayList(-1));
        assertThrows(IllegalArgumentException.class, () -> new FloatArrayList(-1));
        assertThrows(IllegalArgumentException.class, () -> new DoubleArrayList(-1));
        assertThrows(IllegalArgumentException.class, () -> new BooleanArrayList(-1));
    }

    @Test
    void growIsOverflowSafe() {
        assertEquals(10, AbstractPrimitiveList.grow(0, 0));
        assertEquals(10, AbstractPrimitiveList.grow(0, 1));
        assertEquals(50, AbstractPrimitiveList.grow(0, 50));
        assertEquals(20, AbstractPrimitiveList.grow(10, 11));
        assertEquals(100, AbstractPrimitiveList.grow(10, 100));
        assertEquals(AbstractPrimitiveList.MAX_ARRAY_SIZE,
            AbstractPrimitiveList.grow(Integer.MAX_VALUE / 2, Integer.MAX_VALUE / 2 + 1));
        assertEquals(AbstractPrimitiveList.MAX_ARRAY_SIZE,
            AbstractPrimitiveList.grow(Integer.MAX_VALUE / 2 + 1, Integer.MAX_VALUE / 2 + 2));
        assertEquals(AbstractPrimitiveList.MAX_ARRAY_SIZE + 1,
            AbstractPrimitiveList.grow(AbstractPrimitiveList.MAX_ARRAY_SIZE, AbstractPrimitiveList.MAX_ARRAY_SIZE + 1));
        assertEquals(Integer.MAX_VALUE,
            AbstractPrimitiveList.grow(AbstractPrimitiveList.MAX_ARRAY_SIZE, Integer.MAX_VALUE));
        assertThrows(OutOfMemoryError.class, () -> AbstractPrimitiveList.grow(10, -1));
        assertThrows(OutOfMemoryError.class, () -> AbstractPrimitiveList.grow(Integer.MAX_VALUE, Integer.MAX_VALUE + 1));
    }

    @Test
    void clearIsConstantTimeOnLargeList() {
        IntArrayList list = new IntArrayList(200_000);
        for (int i = 0; i < 200_000; i++) {
            list.addInt(i);
        }
        Iterator<Integer> it = list.iterator();
        assertTimeoutPreemptively(Duration.ofMillis(50), list::clear);
        assertTrue(list.isEmpty());
        assertEquals(0, list.size());
        assertThrows(ConcurrentModificationException.class, it::next);

        list.addInt(7);
        assertEquals(List.of(7), list);
    }

    @Test
    void subListClearUsesRemoveRangeOnEveryPrimitiveType() {
        assertSubListClear(new IntArrayList(List.of(10, 20, 30, 40, 50)), List.of(10, 40, 50));
        assertSubListClear(new LongArrayList(List.of(10L, 20L, 30L, 40L, 50L)), List.of(10L, 40L, 50L));
        assertSubListClear(new FloatArrayList(List.of(1f, 2f, 3f, 4f, 5f)), List.of(1f, 4f, 5f));
        assertSubListClear(new DoubleArrayList(List.of(1d, 2d, 3d, 4d, 5d)), List.of(1d, 4d, 5d));
        assertSubListClear(new BooleanArrayList(List.of(true, false, false, true, false)), List.of(true, true, false));
    }

    @Test
    void removeRangeValidatesBoundsAndIncrementsModCount() {
        IntArrayList ints = new IntArrayList(List.of(10, 20, 30, 40));
        assertThrows(IndexOutOfBoundsException.class, () -> ints.removeRange(-1, 2));
        assertThrows(IndexOutOfBoundsException.class, () -> ints.removeRange(0, 5));
        assertThrows(IndexOutOfBoundsException.class, () -> ints.removeRange(3, 2));

        Iterator<Integer> it = ints.iterator();
        ints.removeRange(1, 3);
        assertEquals(List.of(10, 40), ints);
        assertThrows(ConcurrentModificationException.class, it::next);

        LongArrayList longs = new LongArrayList(List.of(1L, 2L));
        assertThrows(IndexOutOfBoundsException.class, () -> longs.removeRange(2, 1));
        FloatArrayList floats = new FloatArrayList(List.of(1f, 2f));
        assertThrows(IndexOutOfBoundsException.class, () -> floats.removeRange(-1, 1));
        DoubleArrayList doubles = new DoubleArrayList(List.of(1d, 2d));
        assertThrows(IndexOutOfBoundsException.class, () -> doubles.removeRange(0, 3));
        BooleanArrayList booleans = new BooleanArrayList(List.of(true, false));
        assertThrows(IndexOutOfBoundsException.class, () -> booleans.removeRange(2, 1));
    }

    @Test
    void copyConstructors() {
        assertEquals(new ArrayList<>(List.of(4, 5)), new IntArrayList(List.of(4, 5)));
        assertEquals(List.of(4L, 5L), new LongArrayList(List.of(4L, 5L)));
        assertEquals(1.5f, new FloatArrayList(List.of(1.5f)).getFloat(0));
        assertEquals(2.5d, new DoubleArrayList(List.of(2.5d)).getDouble(0));
        assertEquals(List.of(true), new BooleanArrayList(List.of(true)));

        assertThrows(NullPointerException.class, () -> new IntArrayList(null));
        assertThrows(NullPointerException.class, () -> new LongArrayList(null));
        assertThrows(NullPointerException.class, () -> new FloatArrayList(null));
        assertThrows(NullPointerException.class, () -> new DoubleArrayList(null));
        assertThrows(NullPointerException.class, () -> new BooleanArrayList(null));
    }

    @Test
    void copyConstructorFastPathProducesIndependentCopy() {
        IntArrayList origInts = new IntArrayList(List.of(1, 2, 3));
        IntArrayList copyInts = new IntArrayList(origInts);
        assertEquals(origInts, copyInts);
        origInts.set(0, 99);
        copyInts.addInt(4);
        assertEquals(List.of(99, 2, 3), origInts);
        assertEquals(List.of(1, 2, 3, 4), copyInts);
        assertEquals(0, new IntArrayList(new IntArrayList()).capacity());

        LongArrayList origLongs = new LongArrayList(List.of(1L, 2L));
        LongArrayList copyLongs = new LongArrayList(origLongs);
        origLongs.set(0, 99L);
        copyLongs.addLong(3L);
        assertEquals(List.of(99L, 2L), origLongs);
        assertEquals(List.of(1L, 2L, 3L), copyLongs);
        assertEquals(0, new LongArrayList(new LongArrayList()).capacity());

        FloatArrayList origFloats = new FloatArrayList(List.of(1f, 2f));
        FloatArrayList copyFloats = new FloatArrayList(origFloats);
        origFloats.set(0, 9f);
        copyFloats.addFloat(3f);
        assertEquals(List.of(9f, 2f), origFloats);
        assertEquals(List.of(1f, 2f, 3f), copyFloats);
        assertEquals(0, new FloatArrayList(new FloatArrayList()).capacity());

        DoubleArrayList origDoubles = new DoubleArrayList(List.of(1d, 2d));
        DoubleArrayList copyDoubles = new DoubleArrayList(origDoubles);
        origDoubles.set(0, 9d);
        copyDoubles.addDouble(3d);
        assertEquals(List.of(9d, 2d), origDoubles);
        assertEquals(List.of(1d, 2d, 3d), copyDoubles);
        assertEquals(0, new DoubleArrayList(new DoubleArrayList()).capacity());

        BooleanArrayList origBooleans = new BooleanArrayList(List.of(true, false));
        BooleanArrayList copyBooleans = new BooleanArrayList(origBooleans);
        origBooleans.set(0, false);
        copyBooleans.addBoolean(true);
        assertEquals(List.of(false, false), origBooleans);
        assertEquals(List.of(true, false, true), copyBooleans);
        assertEquals(0, new BooleanArrayList(new BooleanArrayList()).capacity());
    }

    @Test
    void equalsAndHashCodeParityWithJdkLists() {
        List<Integer> intValues = List.of(Integer.MIN_VALUE, -1, 0, 42, Integer.MAX_VALUE);
        assertEqualsAndHashCodeParity(new IntArrayList(intValues), new IntArrayList(intValues), intValues);

        List<Long> longValues = List.of(Long.MIN_VALUE, -1L, 0L, 1234567890123L, Long.MAX_VALUE);
        assertEqualsAndHashCodeParity(new LongArrayList(longValues), new LongArrayList(longValues), longValues);

        List<Float> floatValues = List.of(
            Float.NEGATIVE_INFINITY, -0.0f, +0.0f, Float.NaN, Float.MIN_VALUE, Float.MAX_VALUE, Float.POSITIVE_INFINITY);
        assertEqualsAndHashCodeParity(new FloatArrayList(floatValues), new FloatArrayList(floatValues), floatValues);

        List<Double> doubleValues = List.of(
            Double.NEGATIVE_INFINITY, -0.0d, +0.0d, Double.NaN, Double.MIN_VALUE, Double.MAX_VALUE, Double.POSITIVE_INFINITY);
        assertEqualsAndHashCodeParity(new DoubleArrayList(doubleValues), new DoubleArrayList(doubleValues), doubleValues);

        List<Boolean> boolValues = List.of(true, false, true, true, false);
        assertEqualsAndHashCodeParity(new BooleanArrayList(boolValues), new BooleanArrayList(boolValues), boolValues);

        // Empty lists of any type are equal and share hashCode 1.
        assertEquals(new IntArrayList(), new LongArrayList());
        assertEquals(List.of().hashCode(), new IntArrayList().hashCode());
        assertEquals(List.of().hashCode(), new DoubleArrayList().hashCode());
    }

    @Test
    void floatAndDoubleEdgeCasesMatchBoxedListSemantics() {
        float nonCanonicalNaN = Float.intBitsToFloat(0x7fc00001);
        FloatArrayList fNan1 = new FloatArrayList(List.of(Float.NaN));
        FloatArrayList fNan2 = new FloatArrayList(List.of(nonCanonicalNaN));
        assertEquals(fNan1, fNan2);
        assertEquals(fNan1.hashCode(), fNan2.hashCode());
        assertEquals(List.of(Float.NaN), fNan2);
        assertEquals(fNan2, List.of(Float.NaN));
        assertEquals(new LinkedList<>(List.of(Float.NaN)), fNan2);
        assertTrue(fNan2.contains(Float.NaN));
        assertEquals(0, fNan2.indexOf(Float.NaN));

        FloatArrayList fNegZero = new FloatArrayList(List.of(-0.0f));
        FloatArrayList fPosZero = new FloatArrayList(List.of(+0.0f));
        assertNotEquals(fNegZero, fPosZero);
        assertNotEquals(fNegZero, List.of(+0.0f));
        assertNotEquals(List.of(+0.0f), fNegZero);
        assertNotEquals(fNegZero, new LinkedList<>(List.of(+0.0f)));
        assertFalse(fNegZero.contains(+0.0f));
        assertTrue(fNegZero.contains(-0.0f));
        assertEquals(-1, fNegZero.indexOf(+0.0f));
        assertEquals(0, fNegZero.indexOf(-0.0f));

        FloatArrayList fMulti = new FloatArrayList(List.of(Float.NaN, -0.0f, +0.0f, nonCanonicalNaN, -0.0f));
        assertEquals(0, fMulti.indexOf(Float.NaN));
        assertEquals(3, fMulti.lastIndexOf(Float.NaN));
        assertEquals(1, fMulti.indexOf(-0.0f));
        assertEquals(4, fMulti.lastIndexOf(-0.0f));
        assertEquals(2, fMulti.indexOf(+0.0f));
        assertEquals(2, fMulti.lastIndexOf(+0.0f));

        double nonCanonicalDoubleNaN = Double.longBitsToDouble(0x7ff8000000000001L);
        DoubleArrayList dNan1 = new DoubleArrayList(List.of(Double.NaN));
        DoubleArrayList dNan2 = new DoubleArrayList(List.of(nonCanonicalDoubleNaN));
        assertEquals(dNan1, dNan2);
        assertEquals(dNan1.hashCode(), dNan2.hashCode());
        assertEquals(List.of(Double.NaN), dNan2);
        assertEquals(dNan2, List.of(Double.NaN));
        assertEquals(new LinkedList<>(List.of(Double.NaN)), dNan2);
        assertTrue(dNan2.contains(Double.NaN));
        assertEquals(0, dNan2.indexOf(Double.NaN));

        DoubleArrayList dNegZero = new DoubleArrayList(List.of(-0.0d));
        DoubleArrayList dPosZero = new DoubleArrayList(List.of(+0.0d));
        assertNotEquals(dNegZero, dPosZero);
        assertNotEquals(dNegZero, List.of(+0.0d));
        assertNotEquals(List.of(+0.0d), dNegZero);
        assertNotEquals(dNegZero, new LinkedList<>(List.of(+0.0d)));
        assertFalse(dNegZero.contains(+0.0d));
        assertTrue(dNegZero.contains(-0.0d));
        assertEquals(-1, dNegZero.indexOf(+0.0d));
        assertEquals(0, dNegZero.indexOf(-0.0d));

        DoubleArrayList dMulti = new DoubleArrayList(List.of(Double.NaN, -0.0d, +0.0d, nonCanonicalDoubleNaN, -0.0d));
        assertEquals(0, dMulti.indexOf(Double.NaN));
        assertEquals(3, dMulti.lastIndexOf(Double.NaN));
        assertEquals(1, dMulti.indexOf(-0.0d));
        assertEquals(4, dMulti.lastIndexOf(-0.0d));
        assertEquals(2, dMulti.indexOf(+0.0d));
        assertEquals(2, dMulti.lastIndexOf(+0.0d));
    }

    @Test
    void indexOfLastIndexOfAndContains() {
        IntArrayList ints = new IntArrayList(List.of(10, 20, 10, 30));
        assertEquals(0, ints.indexOf(10));
        assertEquals(2, ints.lastIndexOf(10));
        assertEquals(-1, ints.indexOf(99));
        assertEquals(-1, ints.indexOf(null));
        assertEquals(-1, ints.indexOf(10L));
        assertTrue(ints.contains(20));
        assertFalse(ints.contains(null));
        assertFalse(ints.contains("20"));

        LongArrayList longs = new LongArrayList(List.of(1L, 2L, 1L));
        assertEquals(0, longs.indexOf(1L));
        assertEquals(2, longs.lastIndexOf(1L));
        assertFalse(longs.contains(1));

        BooleanArrayList booleans = new BooleanArrayList(List.of(false, true, false));
        assertEquals(0, booleans.indexOf(false));
        assertEquals(2, booleans.lastIndexOf(false));
        assertTrue(booleans.contains(true));
        assertFalse(booleans.contains("true"));
    }

    @Test
    void addAllIncludingSelfAppend() {
        IntArrayList ints = new IntArrayList(List.of(1, 2));
        assertTrue(ints.addAll(ints));
        assertEquals(List.of(1, 2, 1, 2), ints);
        assertFalse(ints.addAll(new IntArrayList()));
        assertTrue(ints.addAll(List.of(3, 4)));
        assertEquals(List.of(1, 2, 1, 2, 3, 4), ints);

        LongArrayList longs = new LongArrayList(List.of(1L));
        assertTrue(longs.addAll(longs));
        assertEquals(List.of(1L, 1L), longs);

        FloatArrayList floats = new FloatArrayList(List.of(1.5f));
        assertTrue(floats.addAll(floats));
        assertEquals(List.of(1.5f, 1.5f), floats);

        DoubleArrayList doubles = new DoubleArrayList(List.of(2.5d));
        assertTrue(doubles.addAll(doubles));
        assertEquals(List.of(2.5d, 2.5d), doubles);

        BooleanArrayList booleans = new BooleanArrayList(List.of(true));
        assertTrue(booleans.addAll(booleans));
        assertEquals(List.of(true, true), booleans);

        // addAll increments modCount (CME on active iterator) and rejects null boxed elements with NPE.
        IntArrayList target = new IntArrayList(List.of(1));
        Iterator<Integer> it = target.iterator();
        assertTrue(target.addAll(new IntArrayList(List.of(2, 3))));
        assertThrows(ConcurrentModificationException.class, it::next);

        List<Integer> withNull = new ArrayList<>();
        withNull.add(4);
        withNull.add(null);
        assertThrows(NullPointerException.class, () -> target.addAll(withNull));
    }

    @Test
    void booleanUnboxedAccess() {
        BooleanArrayList list = new BooleanArrayList();
        list.addBoolean(true);
        assertTrue(list.getBoolean(0));
    }

    @Test
    void indexOutOfBoundsAndNullElementSafety() {
        IntArrayList list = new IntArrayList();
        assertThrows(IndexOutOfBoundsException.class, () -> list.getInt(0));
        assertThrows(IndexOutOfBoundsException.class, () -> list.add(2, 1));

        list.addInt(10);
        list.addInt(20);
        assertThrows(NullPointerException.class, () -> list.add(0, null));
        assertEquals(List.of(10, 20), list);
    }

    private static <T> void assertListContract(List<T> list, T first, T second, T inserted) {
        list.add(first);
        list.add(second);
        list.add(1, inserted);
        assertEquals(first, list.get(0));
        assertEquals(inserted, list.get(1));
        assertEquals(second, list.remove(2));
        assertEquals(2, list.size());
    }

    private static <T> void assertSubListClear(List<T> list, List<T> expected) {
        list.subList(1, 3).clear();
        assertEquals(expected, list);
        list.subList(1, 1).clear();
        assertEquals(expected, list);
    }

    private static <T> void assertEqualsAndHashCodeParity(List<T> a, List<T> b, List<T> immutable) {
        ArrayList<T> arrayList = new ArrayList<>(immutable);
        LinkedList<T> linkedList = new LinkedList<>(immutable);

        assertEquals(a, a);
        assertEquals(a, b);
        assertEquals(b, a);
        assertEquals(a, immutable);
        assertEquals(immutable, a);
        assertEquals(a, arrayList);
        assertEquals(arrayList, a);
        assertEquals(a, linkedList);
        assertEquals(linkedList, a);

        assertEquals(immutable.hashCode(), a.hashCode());
        assertEquals(arrayList.hashCode(), a.hashCode());

        assertNotEquals(a, null);
        assertNotEquals(a, "not a list");
        assertNotEquals(a, List.of());
        assertNotEquals(a, List.of("wrong"));
        List<Object> withNull = new ArrayList<>(immutable);
        withNull.set(0, null);
        assertNotEquals(a, withNull);
        assertNotEquals(withNull, a);
    }
}
