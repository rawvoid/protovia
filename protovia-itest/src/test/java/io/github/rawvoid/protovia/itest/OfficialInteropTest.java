package io.github.rawvoid.protovia.itest;

import com.google.protobuf.DescriptorProtos;
import com.google.protobuf.Descriptors;
import com.google.protobuf.DynamicMessage;
import com.google.protobuf.ByteString;
import io.github.rawvoid.protovia.ProtoAny;
import io.github.rawvoid.protovia.ProtoVia;

import java.time.Instant;
import io.github.rawvoid.protovia.itest.model.Address;
import io.github.rawvoid.protovia.itest.model.Carrier;
import io.github.rawvoid.protovia.itest.model.Contact;
import io.github.rawvoid.protovia.itest.model.Email;
import io.github.rawvoid.protovia.itest.model.Envelope;
import io.github.rawvoid.protovia.itest.model.Home;
import io.github.rawvoid.protovia.itest.model.Timed;
import io.github.rawvoid.protovia.wkt.DurationCodec;
import io.github.rawvoid.protovia.wkt.TimestampCodec;
import io.github.rawvoid.protovia.itest.model.Status;
import io.github.rawvoid.protovia.itest.model.User;
import io.github.rawvoid.protovia.wkt.Int32Value;
import io.github.rawvoid.protovia.wkt.StringValue;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OfficialInteropTest {

    private static Descriptors.Descriptor userDescriptor;
    private static Descriptors.Descriptor addressDescriptor;

    @BeforeAll
    static void descriptors() throws Exception {
        DescriptorProtos.DescriptorProto address = DescriptorProtos.DescriptorProto.newBuilder()
                .setName("Address")
                .addField(field("city", 1, DescriptorProtos.FieldDescriptorProto.Type.TYPE_STRING))
                .addField(field("street", 2, DescriptorProtos.FieldDescriptorProto.Type.TYPE_STRING))
                .build();

        DescriptorProtos.EnumDescriptorProto status = DescriptorProtos.EnumDescriptorProto.newBuilder()
                .setName("Status")
                .addValue(DescriptorProtos.EnumValueDescriptorProto.newBuilder().setName("UNKNOWN").setNumber(0))
                .addValue(DescriptorProtos.EnumValueDescriptorProto.newBuilder().setName("ACTIVE").setNumber(1))
                .addValue(DescriptorProtos.EnumValueDescriptorProto.newBuilder().setName("BANNED").setNumber(2))
                .build();

        DescriptorProtos.DescriptorProto.Builder user = DescriptorProtos.DescriptorProto.newBuilder()
                .setName("User")
                .addField(field("name", 1, DescriptorProtos.FieldDescriptorProto.Type.TYPE_STRING))
                .addField(field("age", 2, DescriptorProtos.FieldDescriptorProto.Type.TYPE_INT32))
                .addField(field("score", 3, DescriptorProtos.FieldDescriptorProto.Type.TYPE_SINT64))
                .addField(repeated("tags", 4, DescriptorProtos.FieldDescriptorProto.Type.TYPE_STRING))
                .addField(field("address", 5, DescriptorProtos.FieldDescriptorProto.Type.TYPE_MESSAGE)
                        .setTypeName(".Address"))
                .addField(DescriptorProtos.FieldDescriptorProto.newBuilder()
                        .setName("scores")
                        .setNumber(6)
                        .setLabel(DescriptorProtos.FieldDescriptorProto.Label.LABEL_REPEATED)
                        .setType(DescriptorProtos.FieldDescriptorProto.Type.TYPE_MESSAGE)
                        .setTypeName(".User.ScoresEntry"))
                .addField(field("status", 7, DescriptorProtos.FieldDescriptorProto.Type.TYPE_ENUM)
                        .setTypeName(".Status"))
                .addField(field("level", 8, DescriptorProtos.FieldDescriptorProto.Type.TYPE_INT32)
                        .setProto3Optional(true))
                .addField(repeated("ranks", 9, DescriptorProtos.FieldDescriptorProto.Type.TYPE_INT32))
                .addField(repeated("unpacked", 10, DescriptorProtos.FieldDescriptorProto.Type.TYPE_INT32)
                        .setOptions(DescriptorProtos.FieldOptions.newBuilder().setPacked(false)))
                .addField(field("payload", 11, DescriptorProtos.FieldDescriptorProto.Type.TYPE_BYTES))
                .addNestedType(DescriptorProtos.DescriptorProto.newBuilder()
                        .setName("ScoresEntry")
                        .setOptions(DescriptorProtos.MessageOptions.newBuilder().setMapEntry(true))
                        .addField(field("key", 1, DescriptorProtos.FieldDescriptorProto.Type.TYPE_STRING))
                        .addField(field("value", 2, DescriptorProtos.FieldDescriptorProto.Type.TYPE_INT32)));

        DescriptorProtos.FileDescriptorProto file = DescriptorProtos.FileDescriptorProto.newBuilder()
                .setName("user.proto")
                .setSyntax("proto3")
                .setPackage("")
                .addMessageType(address)
                .addMessageType(user)
                .addEnumType(status)
                .build();

        Descriptors.FileDescriptor fd = Descriptors.FileDescriptor.buildFrom(file, new Descriptors.FileDescriptor[0]);
        addressDescriptor = fd.findMessageTypeByName("Address");
        userDescriptor = fd.findMessageTypeByName("User");
    }

    @Test
    void protoviaBytesAreReadableByDynamicMessage() throws Exception {
        User user = sample();
        byte[] bytes = ProtoVia.toBytes(user);
        DynamicMessage parsed = DynamicMessage.parseFrom(userDescriptor, bytes);

        assertEquals("Ada", parsed.getField(userDescriptor.findFieldByName("name")));
        assertEquals(36, parsed.getField(userDescriptor.findFieldByName("age")));
        assertEquals(-7L, parsed.getField(userDescriptor.findFieldByName("score")));
        assertEquals(List.of("dev", "java"), parsed.getField(userDescriptor.findFieldByName("tags")));
        DynamicMessage address = (DynamicMessage) parsed.getField(userDescriptor.findFieldByName("address"));
        assertEquals("Paris", address.getField(addressDescriptor.findFieldByName("city")));
        assertEquals(Status.ACTIVE.ordinal(), ((Descriptors.EnumValueDescriptor)
                parsed.getField(userDescriptor.findFieldByName("status"))).getNumber());
        assertEquals(0, parsed.getField(userDescriptor.findFieldByName("level")));
        assertEquals(List.of(1, 2, 3), parsed.getField(userDescriptor.findFieldByName("ranks")));
        assertEquals(ByteString.copyFrom(new byte[]{1, 2, 3}),
                parsed.getField(userDescriptor.findFieldByName("payload")));
    }

    @Test
    void dynamicMessageBytesAreReadableByProtovia() throws Exception {
        DynamicMessage.Builder builder = DynamicMessage.newBuilder(userDescriptor);
        builder.setField(userDescriptor.findFieldByName("name"), "Ada");
        builder.setField(userDescriptor.findFieldByName("age"), 36);
        builder.setField(userDescriptor.findFieldByName("score"), -7L);
        builder.setField(userDescriptor.findFieldByName("tags"), List.of("dev", "java"));
        DynamicMessage.Builder addr = DynamicMessage.newBuilder(addressDescriptor);
        addr.setField(addressDescriptor.findFieldByName("city"), "Paris");
        addr.setField(addressDescriptor.findFieldByName("street"), "Rue");
        builder.setField(userDescriptor.findFieldByName("address"), addr.build());
        builder.setField(userDescriptor.findFieldByName("status"),
                userDescriptor.findFieldByName("status").getEnumType().findValueByNumber(1));
        builder.setField(userDescriptor.findFieldByName("level"), 0);
        builder.setField(userDescriptor.findFieldByName("ranks"), List.of(1, 2, 3));
        builder.setField(userDescriptor.findFieldByName("unpacked"), List.of(8, 9));
        builder.setField(userDescriptor.findFieldByName("payload"), ByteString.copyFrom(new byte[]{1, 2, 3}));

        Descriptors.Descriptor entry = userDescriptor.findNestedTypeByName("ScoresEntry");
        DynamicMessage math = DynamicMessage.newBuilder(entry)
                .setField(entry.findFieldByName("key"), "math")
                .setField(entry.findFieldByName("value"), 99)
                .build();
        builder.addRepeatedField(userDescriptor.findFieldByName("scores"), math);

        byte[] bytes = builder.build().toByteArray();
        User back = ProtoVia.fromBytes(User.class, bytes);
        assertEquals("Ada", back.getName());
        assertEquals(36, back.getAge());
        assertEquals(-7L, back.getScore());
        assertEquals(List.of("dev", "java"), back.getTags());
        assertEquals(new Address("Paris", "Rue"), back.getAddress());
        assertEquals(Status.ACTIVE, back.getStatus());
        assertEquals(0, back.getLevel());
        assertEquals(List.of(1, 2, 3), back.getRanks());
        assertEquals(List.of(8, 9), back.getUnpacked());
        assertArrayEquals(new byte[]{1, 2, 3}, back.getPayload());
        assertEquals(99, back.getScores().get("math"));
    }

    @Test
    void timestampAndDurationMatchOfficial() throws Exception {
        Instant at = Instant.parse("2020-01-02T03:04:05.006Z");
        java.time.Duration wait = java.time.Duration.ofSeconds(-1, 500_000_000);

        com.google.protobuf.Timestamp officialTs = com.google.protobuf.Timestamp.newBuilder()
                .setSeconds(at.getEpochSecond())
                .setNanos(at.getNano())
                .build();
        assertArrayEquals(officialTs.toByteArray(), encode(TimestampCodec.INSTANCE, at));
        assertEquals(at, TimestampCodec.INSTANCE.readFrom(
                new io.github.rawvoid.protovia.wire.ProtoReader(officialTs.toByteArray())));

        com.google.protobuf.Duration officialDur = com.google.protobuf.Duration.parseFrom(
                encode(DurationCodec.INSTANCE, wait));
        assertEquals(wait, DurationCodec.INSTANCE.readFrom(
                new io.github.rawvoid.protovia.wire.ProtoReader(officialDur.toByteArray())));

        Timed timed = new Timed();
        timed.at = at;
        timed.wait = wait;
        Timed back = ProtoVia.fromBytes(Timed.class, ProtoVia.toBytes(timed));
        assertEquals(at, back.at);
        assertEquals(wait, back.wait);
    }

    private static <T> byte[] encode(io.github.rawvoid.protovia.codec.ProtoCodec<T> codec, T value) {
        int size = codec.computeSize(value);
        io.github.rawvoid.protovia.wire.ProtoWriter w =
                new io.github.rawvoid.protovia.wire.ProtoWriter(size);
        codec.writeTo(w, value);
        return w.finish();
    }

    @Test
    void oneofInteropsWithDynamicMessage() throws Exception {
        DescriptorProtos.DescriptorProto address = DescriptorProtos.DescriptorProto.newBuilder()
                .setName("Address")
                .addField(field("city", 1, DescriptorProtos.FieldDescriptorProto.Type.TYPE_STRING))
                .addField(field("street", 2, DescriptorProtos.FieldDescriptorProto.Type.TYPE_STRING))
                .build();
        DescriptorProtos.DescriptorProto contact = DescriptorProtos.DescriptorProto.newBuilder()
                .setName("Contact")
                .addField(field("name", 1, DescriptorProtos.FieldDescriptorProto.Type.TYPE_STRING))
                .addOneofDecl(DescriptorProtos.OneofDescriptorProto.newBuilder().setName("target"))
                .addField(field("email", 10, DescriptorProtos.FieldDescriptorProto.Type.TYPE_STRING)
                        .setOneofIndex(0))
                .addField(field("address", 11, DescriptorProtos.FieldDescriptorProto.Type.TYPE_MESSAGE)
                        .setTypeName(".Address")
                        .setOneofIndex(0))
                .build();
        Descriptors.FileDescriptor fd = Descriptors.FileDescriptor.buildFrom(
                DescriptorProtos.FileDescriptorProto.newBuilder()
                        .setName("contact.proto")
                        .setSyntax("proto3")
                        .addMessageType(address)
                        .addMessageType(contact)
                        .build(),
                new Descriptors.FileDescriptor[0]);
        Descriptors.Descriptor desc = fd.findMessageTypeByName("Contact");
        Descriptors.Descriptor addrDesc = fd.findMessageTypeByName("Address");

        Contact c = new Contact();
        c.name = "Ada";
        c.target = new Email("ada@example.com");
        DynamicMessage parsed = DynamicMessage.parseFrom(desc, ProtoVia.toBytes(c));
        assertEquals("Ada", parsed.getField(desc.findFieldByName("name")));
        assertEquals("ada@example.com", parsed.getField(desc.findFieldByName("email")));

        DynamicMessage official = DynamicMessage.newBuilder(desc)
                .setField(desc.findFieldByName("name"), "Ada")
                .setField(desc.findFieldByName("address"),
                        DynamicMessage.newBuilder(addrDesc)
                                .setField(addrDesc.findFieldByName("city"), "Paris")
                                .setField(addrDesc.findFieldByName("street"), "Rue")
                                .build())
                .build();
        Contact back = ProtoVia.fromBytes(Contact.class, official.toByteArray());
        assertEquals("Ada", back.name);
        assertEquals(new Home(new Address("Paris", "Rue")), back.target);
    }

    @Test
    void unknownFieldsSurviveDynamicMessage() throws Exception {
        Envelope env = new Envelope();
        env.name = "Ada";
        byte[] base = ProtoVia.toBytes(env);

        DescriptorProtos.DescriptorProto extra = DescriptorProtos.DescriptorProto.newBuilder()
                .setName("Envelope")
                .addField(field("name", 1, DescriptorProtos.FieldDescriptorProto.Type.TYPE_STRING))
                .addField(field("secret", 15, DescriptorProtos.FieldDescriptorProto.Type.TYPE_INT32))
                .build();
        Descriptors.FileDescriptor fd = Descriptors.FileDescriptor.buildFrom(
                DescriptorProtos.FileDescriptorProto.newBuilder()
                        .setName("envelope.proto")
                        .setSyntax("proto3")
                        .addMessageType(extra)
                        .build(),
                new Descriptors.FileDescriptor[0]);
        Descriptors.Descriptor desc = fd.findMessageTypeByName("Envelope");

        DynamicMessage richer = DynamicMessage.newBuilder(desc)
                .setField(desc.findFieldByName("name"), "Ada")
                .setField(desc.findFieldByName("secret"), 99)
                .build();
        Envelope captured = ProtoVia.fromBytes(Envelope.class, richer.toByteArray());
        assertEquals("Ada", captured.name);
        DynamicMessage parsed = DynamicMessage.parseFrom(desc, ProtoVia.toBytes(captured));
        assertEquals(99, parsed.getField(desc.findFieldByName("secret")));
        assertEquals(base.length < ProtoVia.toBytes(captured).length, true);
    }

    @Test
    void anyPacksInstantAgainstOfficial() throws Exception {
        Instant at = Instant.parse("2020-01-02T03:04:05.006Z");
        ProtoAny packed = ProtoVia.pack(at);
        com.google.protobuf.Any official = com.google.protobuf.Any.parseFrom(ProtoVia.toBytes(packed));
        assertEquals("type.googleapis.com/google.protobuf.Timestamp", official.getTypeUrl());
        com.google.protobuf.Timestamp ts = official.unpack(com.google.protobuf.Timestamp.class);
        assertEquals(at.getEpochSecond(), ts.getSeconds());
        assertEquals(at.getNano(), ts.getNanos());

        com.google.protobuf.Any officialPack = com.google.protobuf.Any.pack(
                com.google.protobuf.Timestamp.newBuilder()
                        .setSeconds(at.getEpochSecond())
                        .setNanos(at.getNano())
                        .build());
        ProtoAny back = ProtoVia.fromBytes(ProtoAny.class, officialPack.toByteArray());
        assertTrue(ProtoVia.is(back, Instant.class));
        assertEquals(at, ProtoVia.unpack(back, Instant.class));
    }

    @Test
    void anyPacksUserAgainstDynamicMessage() throws Exception {
        User user = sample();
        ProtoAny packed = ProtoVia.pack(user);
        assertEquals("type.googleapis.com/User", packed.typeUrl());
        assertEquals(user, ProtoVia.unpack(packed, User.class));

        com.google.protobuf.Any official = com.google.protobuf.Any.parseFrom(ProtoVia.toBytes(packed));
        assertEquals("type.googleapis.com/User", official.getTypeUrl());
        DynamicMessage parsed = DynamicMessage.parseFrom(userDescriptor, official.getValue().toByteArray());
        assertEquals("Ada", parsed.getField(userDescriptor.findFieldByName("name")));

        DynamicMessage officialUser = DynamicMessage.parseFrom(userDescriptor, ProtoVia.toBytes(user));
        com.google.protobuf.Any officialPack = com.google.protobuf.Any.pack(officialUser);
        User back = ProtoVia.unpack(ProtoVia.fromBytes(ProtoAny.class, officialPack.toByteArray()), User.class);
        assertEquals("Ada", back.getName());
        assertEquals(36, back.getAge());
    }

    @Test
    void wrappersMatchOfficial() throws Exception {
        assertArrayEquals(
                com.google.protobuf.Int32Value.of(42).toByteArray(),
                encode(Int32Value.INSTANCE, new Int32Value(42)));
        assertEquals(0, encode(Int32Value.INSTANCE, new Int32Value(0)).length);
        assertEquals(
                42,
                com.google.protobuf.Int32Value.parseFrom(
                        encode(Int32Value.INSTANCE, new Int32Value(42))).getValue());
        assertEquals(
                new Int32Value(-7),
                Int32Value.INSTANCE.readFrom(new io.github.rawvoid.protovia.wire.ProtoReader(
                        com.google.protobuf.Int32Value.of(-7).toByteArray())));

        assertArrayEquals(
                com.google.protobuf.StringValue.of("hi").toByteArray(),
                encode(StringValue.INSTANCE, new StringValue("hi")));

        Carrier carrier = new Carrier();
        carrier.name = "box";
        carrier.count = new Int32Value(0);
        carrier.extra = ProtoVia.pack(new Int32Value(7));
        byte[] bytes = ProtoVia.toBytes(carrier);
        // field 3 Int32Value(0) is present as an empty nested message: tag 0x1A, length 0
        assertTrue(indexOf(bytes, new byte[]{0x1A, 0x00}) >= 0);

        com.google.protobuf.Any officialAny = com.google.protobuf.Any.parseFrom(
                ProtoVia.toBytes(carrier.extra));
        assertEquals(7, officialAny.unpack(com.google.protobuf.Int32Value.class).getValue());

        Carrier back = ProtoVia.fromBytes(Carrier.class, bytes);
        assertEquals("box", back.name);
        assertEquals(new Int32Value(0), back.count);
        assertEquals(new Int32Value(7), ProtoVia.unpack(back.extra, Int32Value.class));
        assertFalse(ProtoVia.is(back.extra, StringValue.class));
    }

    @Test
    void emptyMessageInteroperable() throws Exception {
        byte[] empty = ProtoVia.toBytes(new User());
        DynamicMessage parsed = DynamicMessage.parseFrom(userDescriptor, empty);
        assertTrue(parsed.getUnknownFields().asMap().isEmpty());
        User back = ProtoVia.fromBytes(User.class, DynamicMessage.newBuilder(userDescriptor).build().toByteArray());
        assertEquals(0, back.getAge());
    }

    private static User sample() {
        User user = new User();
        user.setName("Ada");
        user.setAge(36);
        user.setScore(-7);
        user.setTags(List.of("dev", "java"));
        user.setAddress(new Address("Paris", "Rue"));
        user.setScores(Map.of("math", 99));
        user.setStatus(Status.ACTIVE);
        user.setLevel(0);
        user.setRanks(List.of(1, 2, 3));
        user.setUnpacked(List.of(8, 9));
        user.setPayload(new byte[]{1, 2, 3});
        return user;
    }

    private static int indexOf(byte[] haystack, byte[] needle) {
        outer:
        for (int i = 0; i <= haystack.length - needle.length; i++) {
            for (int j = 0; j < needle.length; j++) {
                if (haystack[i + j] != needle[j]) {
                    continue outer;
                }
            }
            return i;
        }
        return -1;
    }

    private static DescriptorProtos.FieldDescriptorProto.Builder field(
            String name, int number, DescriptorProtos.FieldDescriptorProto.Type type) {
        return DescriptorProtos.FieldDescriptorProto.newBuilder()
                .setName(name)
                .setNumber(number)
                .setType(type)
                .setLabel(DescriptorProtos.FieldDescriptorProto.Label.LABEL_OPTIONAL);
    }

    private static DescriptorProtos.FieldDescriptorProto.Builder repeated(
            String name, int number, DescriptorProtos.FieldDescriptorProto.Type type) {
        return DescriptorProtos.FieldDescriptorProto.newBuilder()
                .setName(name)
                .setNumber(number)
                .setType(type)
                .setLabel(DescriptorProtos.FieldDescriptorProto.Label.LABEL_REPEATED);
    }
}
