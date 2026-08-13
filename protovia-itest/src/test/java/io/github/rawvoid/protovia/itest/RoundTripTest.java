package io.github.rawvoid.protovia.itest;

import io.github.rawvoid.protovia.ProtoVia;
import io.github.rawvoid.protovia.collect.IntArrayList;
import io.github.rawvoid.protovia.itest.model.Address;
import io.github.rawvoid.protovia.itest.model.NodeA;
import io.github.rawvoid.protovia.itest.model.NodeB;
import io.github.rawvoid.protovia.itest.model.Status;
import io.github.rawvoid.protovia.itest.model.User;
import io.github.rawvoid.protovia.itest.model.UserRecord;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RoundTripTest {

    @Test
    void pojoRoundTrip() {
        User user = sampleUser();
        byte[] bytes = ProtoVia.toBytes(user);
        User back = ProtoVia.fromBytes(User.class, bytes);
        assertEquals(user, back);
    }

    @Test
    void recordRoundTrip() {
        UserRecord record = new UserRecord(
                "Ada",
                36,
                new Address("Paris", "Rue"),
                List.of("dev", "java"),
                map("math", 99),
                Status.ACTIVE,
                Optional.of(0));
        byte[] bytes = ProtoVia.toBytes(record);
        UserRecord back = ProtoVia.fromBytes(UserRecord.class, bytes);
        assertEquals(record, back);
    }

    @Test
    void defaultsAreOmitted() {
        User user = new User();
        user.setName("");
        user.setAge(0);
        user.setStatus(Status.UNKNOWN);
        byte[] bytes = ProtoVia.toBytes(user);
        assertEquals(0, bytes.length);
    }

    @Test
    void optionalZeroIsWritten() {
        User user = new User();
        user.setLevel(0);
        byte[] bytes = ProtoVia.toBytes(user);
        assertArrayEquals(new byte[]{0x40, 0x00}, bytes);
        User back = ProtoVia.fromBytes(User.class, bytes);
        assertEquals(0, back.getLevel());
    }

    @Test
    void unknownEnumIsSkipped() {
        User user = new User();
        user.setName("n");
        byte[] known = ProtoVia.toBytes(user);
        byte[] extra = new byte[known.length + 2];
        System.arraycopy(known, 0, extra, 0, known.length);
        extra[known.length] = 0x38; // field 7 enum
        extra[known.length + 1] = 0x63; // 99
        User back = ProtoVia.fromBytes(User.class, extra);
        assertEquals("n", back.getName());
        assertNull(back.getStatus());
    }

    @Test
    void mutualRecursion() {
        NodeA a = new NodeA();
        a.name = "a";
        NodeB b = new NodeB();
        b.name = "b";
        a.next = b;
        NodeA a2 = new NodeA();
        a2.name = "a2";
        b.next = a2;
        byte[] bytes = ProtoVia.toBytes(a);
        NodeA back = ProtoVia.fromBytes(NodeA.class, bytes);
        assertEquals("a", back.name);
        assertEquals("b", back.next.name);
        assertEquals("a2", back.next.next.name);
    }

    @Test
    void concatenatedMessagesMergeNestedAndRepeated() {
        User first = new User();
        first.setAddress(new Address("Paris", null));
        first.setRanks(List.of(1, 2));
        User second = new User();
        second.setAddress(new Address(null, "Rue"));
        second.setRanks(List.of(3));
        User back = ProtoVia.fromBytes(User.class, concat(ProtoVia.toBytes(first), ProtoVia.toBytes(second)));
        assertEquals("Paris", back.getAddress().city());
        assertEquals("Rue", back.getAddress().street());
        assertEquals(List.of(1, 2, 3), back.getRanks());
    }

    @Test
    void concatenatedRecordsMergeFields() {
        Address first = new Address("Paris", null);
        Address second = new Address(null, "Rue");
        Address back = ProtoVia.fromBytes(Address.class, concat(ProtoVia.toBytes(first), ProtoVia.toBytes(second)));
        assertEquals("Paris", back.city());
        assertEquals("Rue", back.street());
    }

    @Test
    void packedAndUnpackedInt32() {
        User user = new User();
        user.setRanks(List.of(3, 270));
        user.setUnpacked(List.of(1, 2));
        User back = ProtoVia.fromBytes(User.class, ProtoVia.toBytes(user));
        assertEquals(List.of(3, 270), back.getRanks());
        assertEquals(List.of(1, 2), back.getUnpacked());
        assertTrue(back.getRanks() instanceof IntArrayList);
        assertTrue(back.getUnpacked() instanceof IntArrayList);
    }

    private static User sampleUser() {
        User user = new User();
        user.setName("Ada");
        user.setAge(36);
        user.setScore(-7);
        user.setTags(List.of("dev", "java"));
        user.setAddress(new Address("Paris", "Rue"));
        user.setScores(map("math", 99, "eng", 0));
        user.setStatus(Status.ACTIVE);
        user.setLevel(0);
        user.setRanks(List.of(1, 2, 3));
        user.setUnpacked(List.of(8, 9));
        user.setPayload(new byte[]{1, 2, 3});
        return user;
    }

    private static byte[] concat(byte[] left, byte[] right) {
        byte[] out = new byte[left.length + right.length];
        System.arraycopy(left, 0, out, 0, left.length);
        System.arraycopy(right, 0, out, left.length, right.length);
        return out;
    }

    private static Map<String, Integer> map(Object... kv) {
        Map<String, Integer> map = new LinkedHashMap<>();
        for (int i = 0; i < kv.length; i += 2) {
            map.put((String) kv[i], (Integer) kv[i + 1]);
        }
        return map;
    }
}
