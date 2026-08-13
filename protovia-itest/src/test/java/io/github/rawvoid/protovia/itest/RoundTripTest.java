package io.github.rawvoid.protovia.itest;

import io.github.rawvoid.protovia.ProtoVia;
import io.github.rawvoid.protovia.collect.IntArrayList;
import io.github.rawvoid.protovia.wire.ProtoReader;
import io.github.rawvoid.protovia.itest.model.Address;
import io.github.rawvoid.protovia.itest.model.Envelope;
import io.github.rawvoid.protovia.itest.model.NodeA;
import io.github.rawvoid.protovia.itest.model.NodeB;
import io.github.rawvoid.protovia.itest.model.Status;
import io.github.rawvoid.protovia.itest.model.User;
import io.github.rawvoid.protovia.itest.model.UserProtoCodec;
import io.github.rawvoid.protovia.itest.model.UserRecord;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import io.github.rawvoid.protovia.ProtoException;
import io.github.rawvoid.protovia.wire.ProtoWriter;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
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
        first.setScores(Map.of("math", 1));
        second.setScores(Map.of("math", 99, "eng", 70));
        User back = ProtoVia.fromBytes(User.class, concat(ProtoVia.toBytes(first), ProtoVia.toBytes(second)));
        assertEquals("Paris", back.getAddress().city());
        assertEquals("Rue", back.getAddress().street());
        assertEquals(List.of(1, 2, 3), back.getRanks());
        assertEquals(99, back.getScores().get("math"));
        assertEquals(70, back.getScores().get("eng"));
    }

    @Test
    void mergeFromCopiesImmutablePojoCollections() {
        User existing = new User();
        existing.setTags(List.of("a"));
        existing.setRanks(List.of(1, 2));
        existing.setScores(Map.of("math", 1));

        User incoming = new User();
        incoming.setTags(List.of("b"));
        incoming.setRanks(List.of(3));
        incoming.setScores(Map.of("math", 99, "eng", 70));

        UserProtoCodec.INSTANCE.mergeFrom(new ProtoReader(ProtoVia.toBytes(incoming)), existing);
        assertEquals(List.of("a", "b"), existing.getTags());
        assertEquals(List.of(1, 2, 3), existing.getRanks());
        assertEquals(99, existing.getScores().get("math"));
        assertEquals(70, existing.getScores().get("eng"));
        assertTrue(existing.getRanks() instanceof IntArrayList);
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
    void packedWriteRejectsNullElement() {
        User user = new User();
        user.setRanks(Arrays.asList(1, null, 3));
        ProtoWriter writer = ProtoWriter.growing();
        assertThrows(ProtoException.class, () -> UserProtoCodec.INSTANCE.writeTo(writer, user));
    }

    @Test
    void unknownFieldsRoundTrip() {
        Envelope env = new Envelope();
        env.name = "keep";
        byte[] known = ProtoVia.toBytes(env);

        ProtoWriter extra = ProtoWriter.growing();
        extra.writeRawBytes(known, 0, known.length);
        extra.writeInt32(15, 42);
        extra.writeString(16, "x");

        Envelope back = ProtoVia.fromBytes(Envelope.class, extra.toByteArray());
        assertEquals("keep", back.name);
        assertTrue(back.unknownFields != null && !back.unknownFields.isEmpty());

        Envelope again = ProtoVia.fromBytes(Envelope.class, ProtoVia.toBytes(back));
        assertEquals("keep", again.name);

        ProtoReader r = new ProtoReader(ProtoVia.toBytes(back));
        int extraInt = 0;
        String extraStr = null;
        int tag;
        while ((tag = r.readTag()) != 0) {
            switch (tag) {
                case 10 -> assertEquals("keep", r.readString());
                case 120 -> extraInt = r.readInt32();
                case 130 -> extraStr = r.readString();
                default -> r.skipField();
            }
        }
        assertEquals(42, extraInt);
        assertEquals("x", extraStr);
    }

    @Test
    void userWithoutUnknownSlotStillSkips() {
        User user = new User();
        user.setName("n");
        byte[] known = ProtoVia.toBytes(user);
        byte[] extra = new byte[known.length + 2];
        System.arraycopy(known, 0, extra, 0, known.length);
        extra[known.length] = 0x78;
        extra[known.length + 1] = 0x01;
        User back = ProtoVia.fromBytes(User.class, extra);
        assertEquals("n", back.getName());
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
