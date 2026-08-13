package io.github.rawvoid.protovia.collect;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class IntArrayListTest {

    @Test
    void addIntAvoidsListBoxingPath() {
        IntArrayList list = new IntArrayList(2);
        list.addInt(3);
        list.addInt(270);
        assertEquals(2, list.size());
        assertEquals(3, list.getInt(0));
        assertEquals(270, list.getInt(1));
        assertEquals(List.of(3, 270), list);
        assertArrayEquals(new int[]{3, 270}, list.toIntArray());
    }

    @Test
    void equalsArrayList() {
        IntArrayList list = new IntArrayList();
        list.addInt(1);
        list.addInt(2);
        assertEquals(new ArrayList<>(List.of(1, 2)), list);
        assertEquals(list, List.of(1, 2));
    }

    @Test
    void ensureMutableListCopiesListOf() {
        List<Integer> immutable = List.of(1, 2);
        List<Integer> mutable = ProtoLists.ensureMutableList(immutable, IntArrayList::new);
        mutable.add(3);
        assertEquals(List.of(1, 2, 3), mutable);
        assertEquals(List.of(1, 2), immutable);
    }

    @Test
    void ensureMutableListKeepsArrayList() {
        List<Integer> original = new ArrayList<>(List.of(1));
        List<Integer> ensured = ProtoLists.ensureMutableList(original, IntArrayList::new);
        assertTrue(ensured == original);
        ensured.add(2);
        assertEquals(List.of(1, 2), original);
    }

    @Test
    void protoListsDispatchesToAddInt() {
        IntArrayList list = new IntArrayList();
        ProtoLists.addInt(list, 9);
        ProtoLists.ensureIntCapacity(list, 8);
        ProtoLists.addInt(list, 10);
        assertEquals(List.of(9, 10), list);
        assertTrue(list instanceof IntArrayList);
    }
}
