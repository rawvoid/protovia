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

package io.github.rawvoid.protovia.itest;

import io.github.rawvoid.protovia.ProtoException;
import io.github.rawvoid.protovia.Protovia;
import io.github.rawvoid.protovia.collect.IntArrayList;
import io.github.rawvoid.protovia.itest.model.*;
import io.github.rawvoid.protovia.itest.model.internal.UserProtoCodec;
import io.github.rawvoid.protovia.wire.ProtoReader;
import io.github.rawvoid.protovia.wire.ProtoWriter;
import io.github.rawvoid.protovia.wkt.Int32Value;
import org.junit.jupiter.api.Test;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * @author Rawvoid
 */
class RoundTripTest {

    @Test
    void pojoRoundTrip() {
        User user = sampleUser();
        byte[] bytes = Protovia.toBytes(user);
        User back = Protovia.fromBytes(User.class, bytes);
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
        byte[] bytes = Protovia.toBytes(record);
        UserRecord back = Protovia.fromBytes(UserRecord.class, bytes);
        assertEquals(record, back);
    }

    @Test
    void anyAndWrapperRoundTrip() {
        Carrier carrier = new Carrier();
        carrier.name = "box";
        carrier.count = new Int32Value(0);
        carrier.extra = Protovia.pack(new Int32Value(7));
        Carrier back = Protovia.fromBytes(Carrier.class, Protovia.toBytes(carrier));
        assertEquals("box", back.name);
        assertEquals(new Int32Value(0), back.count);
        assertEquals(new Int32Value(7), Protovia.unpack(back.extra, Int32Value.class));
        assertEquals("example.v1.Carrier", Protovia.codec(Carrier.class).protoFullName());
    }

    @Test
    void defaultsAreOmitted() {
        User user = new User();
        user.setName("");
        user.setAge(0);
        user.setStatus(Status.UNKNOWN);
        byte[] bytes = Protovia.toBytes(user);
        assertEquals(0, bytes.length);
    }

    @Test
    void optionalZeroIsWritten() {
        User user = new User();
        user.setLevel(0);
        byte[] bytes = Protovia.toBytes(user);
        assertArrayEquals(new byte[]{0x40, 0x00}, bytes);
        User back = Protovia.fromBytes(User.class, bytes);
        assertEquals(0, back.getLevel());
    }

    @Test
    void repeatedUnknownEnumDoesNotThrowOnRewrite() {
        ProtoWriter extra = ProtoWriter.growing();
        extra.writeEnum(1, 1);
        extra.writeEnum(1, 99);
        extra.writeEnum(1, 2);
        Flags back = Protovia.fromBytes(Flags.class, extra.toByteArray());
        assertEquals(List.of(Status.ACTIVE, Status.BANNED), back.flags);
        Flags again = Protovia.fromBytes(Flags.class, Protovia.toBytes(back));
        assertEquals(List.of(Status.ACTIVE, Status.BANNED), again.flags);
        assertTrue(again.unknownFields.serializedSize() > 0);
    }

    @Test
    void unknownEnumIsSkipped() {
        User user = new User();
        user.setName("n");
        byte[] known = Protovia.toBytes(user);
        byte[] extra = new byte[known.length + 2];
        System.arraycopy(known, 0, extra, 0, known.length);
        extra[known.length] = 0x38; // field 7 enum
        extra[known.length + 1] = 0x63; // 99
        User back = Protovia.fromBytes(User.class, extra);
        assertEquals("n", back.getName());
        assertEquals(Status.UNRECOGNIZED, back.getStatus());
    }

    @Test
    void unknownEnumRewrittenViaUnknownFields() {
        Envelope env = new Envelope();
        env.name = "n";
        ProtoWriter extra = ProtoWriter.growing();
        extra.writeRawBytes(Protovia.toBytes(env), 0, Protovia.toBytes(env).length);
        extra.writeInt32(7, 99);
        Envelope back = Protovia.fromBytes(Envelope.class, extra.toByteArray());
        assertEquals(Status.UNRECOGNIZED, back.status);
        Envelope again = Protovia.fromBytes(Envelope.class, Protovia.toBytes(back));
        assertEquals(Status.UNRECOGNIZED, again.status);
        ProtoReader r = new ProtoReader(Protovia.toBytes(back));
        int raw = -1;
        int tag;
        while ((tag = r.readTag()) != 0) {
            if (tag == 56) {
                raw = r.readEnum();
            } else {
                r.skipField();
            }
        }
        assertEquals(99, raw);
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
        byte[] bytes = Protovia.toBytes(a);
        NodeA back = Protovia.fromBytes(NodeA.class, bytes);
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
        User back = Protovia.fromBytes(User.class, concat(Protovia.toBytes(first), Protovia.toBytes(second)));
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

        UserProtoCodec.INSTANCE.mergeFrom(new ProtoReader(Protovia.toBytes(incoming)), existing);
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
        Address back = Protovia.fromBytes(Address.class, concat(Protovia.toBytes(first), Protovia.toBytes(second)));
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
    void genericOneofClassRoundTrip() {
        ApiRS<Target> email = new ApiRS<>();
        email.success = true;
        email.data = new Email("ada@example.com");
        ApiRS<Target> emailBack = Protovia.fromBytes(ApiRS.class, Protovia.toBytes(email));
        assertTrue(emailBack.success);
        assertEquals(new Email("ada@example.com"), emailBack.data);

        ApiRS<Target> home = new ApiRS<>();
        home.success = true;
        home.data = new Home(new Address("Paris", "Rue"));
        ApiRS<Target> homeBack = Protovia.fromBytes(ApiRS.class, Protovia.toBytes(home));
        assertTrue(homeBack.success);
        assertEquals(new Home(new Address("Paris", "Rue")), homeBack.data);
    }

    @Test
    void genericOneofRecordRoundTrip() {
        ApiRecordRS<Target> email = new ApiRecordRS<>(true, new Email("ada@example.com"));
        assertEquals(email, Protovia.fromBytes(ApiRecordRS.class, Protovia.toBytes(email)));

        ApiRecordRS<Target> home = new ApiRecordRS<>(true, new Home(new Address("Paris", "Rue")));
        assertEquals(home, Protovia.fromBytes(ApiRecordRS.class, Protovia.toBytes(home)));
    }

    @Test
    void genericOneofMatchesConcreteTwinBytes() {
        ApiRS<Target> generic = new ApiRS<>();
        generic.success = true;
        generic.data = new Email("ada@example.com");
        ApiTwin twin = new ApiTwin();
        twin.success = true;
        twin.data = new Email("ada@example.com");
        assertArrayEquals(Protovia.toBytes(twin), Protovia.toBytes(generic));

        ApiTwin decoded = Protovia.fromBytes(ApiTwin.class, Protovia.toBytes(generic));
        assertTrue(decoded.success);
        assertEquals(new Email("ada@example.com"), decoded.data);
    }

    @Test
    void genericOneofOmitsDefaultsAndWritesEmptyScalar() {
        ApiRS<Target> empty = new ApiRS<>();
        assertEquals(0, Protovia.toBytes(empty).length);

        ApiRS<Target> blank = new ApiRS<>();
        blank.data = new Email("");
        byte[] bytes = Protovia.toBytes(blank);
        assertTrue(bytes.length > 0);
        ApiRS<Target> back = Protovia.fromBytes(ApiRS.class, bytes);
        assertFalse(back.success);
        assertEquals(new Email(""), back.data);
    }

    @Test
    void oneofEmailRoundTrip() {
        Contact c = new Contact();
        c.name = "Ada";
        c.target = new Email("ada@example.com");
        Contact back = Protovia.fromBytes(Contact.class, Protovia.toBytes(c));
        assertEquals("Ada", back.name);
        assertEquals(new Email("ada@example.com"), back.target);
    }

    @Test
    void oneofProtoMessageRecordRoundTrip() {
        Bag address = new Bag();
        address.data = new Address("Paris", "Rue");
        Bag addressBack = Protovia.fromBytes(Bag.class, Protovia.toBytes(address));
        assertEquals(new Address("Paris", "Rue"), addressBack.data);

        Bag label = new Bag();
        label.data = "ada@example.com";
        Bag labelBack = Protovia.fromBytes(Bag.class, Protovia.toBytes(label));
        assertEquals("ada@example.com", labelBack.data);
    }

    @Test
    void aliasReusesEmailAndHomeAtNumbersOneAndTwo() {
        Alias email = new Alias();
        email.target = new Email("ada@example.com");
        Alias emailBack = Protovia.fromBytes(Alias.class, Protovia.toBytes(email));
        assertEquals(new Email("ada@example.com"), emailBack.target);

        Alias home = new Alias();
        home.target = new Home(new Address("Paris", "Rue"));
        Alias homeBack = Protovia.fromBytes(Alias.class, Protovia.toBytes(home));
        assertEquals(new Home(new Address("Paris", "Rue")), homeBack.target);
    }

    @Test
    void oneofUnexpectedRuntimeTypeIsRejected() {
        Contact c = new Contact();
        c.target = new Phone("1");
        ProtoException toBytes = assertThrows(ProtoException.class, () -> Protovia.toBytes(c));
        assertTrue(toBytes.getMessage().contains("unexpected type"));
        ProtoException size = assertThrows(ProtoException.class, () -> Protovia.sizeOf(c));
        assertTrue(size.getMessage().contains("unexpected type"));
    }

    @Test
    void oneofEmptyEmailIsWritten() {
        Contact c = new Contact();
        c.target = new Email("");
        byte[] bytes = Protovia.toBytes(c);
        assertTrue(bytes.length > 0);
        Contact back = Protovia.fromBytes(Contact.class, bytes);
        assertEquals(new Email(""), back.target);
    }

    @Test
    void oneofLastTagWins() {
        Contact email = new Contact();
        email.target = new Email("a@b.c");
        Contact home = new Contact();
        home.target = new Home(new Address("Paris", "Rue"));
        byte[] first = Protovia.toBytes(email);
        byte[] second = Protovia.toBytes(home);
        byte[] both = new byte[first.length + second.length];
        System.arraycopy(first, 0, both, 0, first.length);
        System.arraycopy(second, 0, both, first.length, second.length);
        Contact back = Protovia.fromBytes(Contact.class, both);
        assertEquals(new Home(new Address("Paris", "Rue")), back.target);
    }

    @Test
    void oneofKnownEnumRoundTrip() {
        Picker picker = new Picker();
        picker.name = "Ada";
        picker.choice = new Picker.StatusPick(Status.ACTIVE);
        Picker back = Protovia.fromBytes(Picker.class, Protovia.toBytes(picker));
        assertEquals("Ada", back.name);
        assertEquals(new Picker.StatusPick(Status.ACTIVE), back.choice);
    }

    @Test
    void oneofUnknownEnumWithSentinelRewrittenViaUnknownFields() {
        ProtoWriter extra = ProtoWriter.growing();
        extra.writeString(1, "n");
        extra.writeInt32(10, 99);
        PickerEnvelope back = Protovia.fromBytes(PickerEnvelope.class, extra.toByteArray());
        assertEquals("n", back.name);
        assertEquals(new Picker.StatusPick(Status.UNRECOGNIZED), back.choice);
        assertFalse(back.unknownFields.isEmpty());

        PickerEnvelope again = Protovia.fromBytes(PickerEnvelope.class, Protovia.toBytes(back));
        assertEquals(new Picker.StatusPick(Status.UNRECOGNIZED), again.choice);
        assertEquals(99, readEnumAt(Protovia.toBytes(again), 10));
    }

    @Test
    void oneofUnknownEnumWithSentinelWithoutUnknownFieldsDoesNotThrow() {
        ProtoWriter extra = ProtoWriter.growing();
        extra.writeString(1, "n");
        extra.writeInt32(10, 99);
        Picker back = Protovia.fromBytes(Picker.class, extra.toByteArray());
        assertEquals("n", back.name);
        assertEquals(new Picker.StatusPick(Status.UNRECOGNIZED), back.choice);

        byte[] rewritten = Protovia.toBytes(back);
        assertEquals(-1, readEnumAt(rewritten, 10));
        Picker again = Protovia.fromBytes(Picker.class, rewritten);
        assertEquals("n", again.name);
        assertNull(again.choice);
    }

    @Test
    void oneofUnknownEnumClosedRewrittenViaUnknownFields() {
        ProtoWriter extra = ProtoWriter.growing();
        extra.writeInt32(10, 99);
        KindPicker back = Protovia.fromBytes(KindPicker.class, extra.toByteArray());
        assertNull(back.choice);
        assertFalse(back.unknownFields.isEmpty());

        KindPicker again = Protovia.fromBytes(KindPicker.class, Protovia.toBytes(back));
        assertNull(again.choice);
        assertEquals(99, readEnumAt(Protovia.toBytes(again), 10));
    }

    @Test
    void oneofUnknownEnumClosedWithoutUnknownFieldsIsDropped() {
        ProtoWriter extra = ProtoWriter.growing();
        extra.writeInt32(10, 99);
        KindPickerBare back = Protovia.fromBytes(KindPickerBare.class, extra.toByteArray());
        assertNull(back.choice);
        assertEquals(0, Protovia.toBytes(back).length);
    }

    @Test
    void oneofUnrecognizedSentinelConstructedByUserIsNotWritten() {
        Picker picker = new Picker();
        picker.choice = new Picker.StatusPick(Status.UNRECOGNIZED);
        assertEquals(0, Protovia.toBytes(picker).length);

        PickerEnvelope env = new PickerEnvelope();
        env.choice = new Picker.StatusPick(null);
        assertEquals(0, Protovia.toBytes(env).length);
    }

    @Test
    void unknownFieldsRoundTrip() {
        Envelope env = new Envelope();
        env.name = "keep";
        byte[] known = Protovia.toBytes(env);

        ProtoWriter extra = ProtoWriter.growing();
        extra.writeRawBytes(known, 0, known.length);
        extra.writeInt32(15, 42);
        extra.writeString(16, "x");

        Envelope back = Protovia.fromBytes(Envelope.class, extra.toByteArray());
        assertEquals("keep", back.name);
        assertTrue(back.unknownFields != null && !back.unknownFields.isEmpty());

        Envelope again = Protovia.fromBytes(Envelope.class, Protovia.toBytes(back));
        assertEquals("keep", again.name);

        ProtoReader r = new ProtoReader(Protovia.toBytes(back));
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
        byte[] known = Protovia.toBytes(user);
        byte[] extra = new byte[known.length + 2];
        System.arraycopy(known, 0, extra, 0, known.length);
        extra[known.length] = 0x78;
        extra[known.length + 1] = 0x01;
        User back = Protovia.fromBytes(User.class, extra);
        assertEquals("n", back.getName());
    }

    @Test
    void packedAndUnpackedInt32() {
        User user = new User();
        user.setRanks(List.of(3, 270));
        user.setUnpacked(List.of(1, 2));
        User back = Protovia.fromBytes(User.class, Protovia.toBytes(user));
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

    private static int readEnumAt(byte[] bytes, int fieldNumber) {
        ProtoReader r = new ProtoReader(bytes);
        int tag;
        int expected = fieldNumber << 3;
        while ((tag = r.readTag()) != 0) {
            if (tag == expected) {
                return r.readEnum();
            }
            r.skipField();
        }
        return -1;
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
