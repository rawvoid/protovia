package io.github.rawvoid.protovia.collect;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

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
    void copyConstructors() {
        assertEquals(new ArrayList<>(List.of(4, 5)), new IntArrayList(List.of(4, 5)));
        assertEquals(List.of(4L, 5L), new LongArrayList(List.of(4L, 5L)));
        assertEquals(1.5f, new FloatArrayList(List.of(1.5f)).getFloat(0));
        assertEquals(2.5d, new DoubleArrayList(List.of(2.5d)).getDouble(0));
        assertEquals(List.of(true), new BooleanArrayList(List.of(true)));
    }

    @Test
    void booleanUnboxedAccess() {
        BooleanArrayList list = new BooleanArrayList();
        list.addBoolean(true);
        assertTrue(list.getBoolean(0));
    }

    @Test
    void indexOutOfBounds() {
        IntArrayList list = new IntArrayList();
        assertThrows(IndexOutOfBoundsException.class, () -> list.getInt(0));
        assertThrows(IndexOutOfBoundsException.class, () -> list.add(2, 1));
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
}
