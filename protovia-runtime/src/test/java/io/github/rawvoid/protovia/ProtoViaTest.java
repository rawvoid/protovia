package io.github.rawvoid.protovia;

import io.github.rawvoid.protovia.support.User;
import io.github.rawvoid.protovia.support.UserProtoCodec;
import io.github.rawvoid.protovia.wkt.Int32Value;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class ProtoViaTest {

    @BeforeEach
    void register() {
        ProtoVia.register(User.class, UserProtoCodec.INSTANCE);
        ProtoVia.register(User.Address.class, UserProtoCodec.AddressProtoCodec.INSTANCE);
    }

    @Test
    void roundTrip() {
        User user = sample();
        byte[] bytes = ProtoVia.toBytes(user);
        User back = ProtoVia.fromBytes(User.class, bytes);
        assertEquals(user, back);
        assertEquals(bytes.length, ProtoVia.sizeOf(user));
    }

    @Test
    void emptyMessageIsEmptyBytes() {
        User user = new User();
        user.setTags(null);
        user.setScores(null);
        byte[] bytes = ProtoVia.toBytes(user);
        assertEquals(0, bytes.length);
        User back = ProtoVia.fromBytes(User.class, bytes);
        assertNull(back.getName());
        assertEquals(0, back.getAge());
    }

    @Test
    void optionalZeroIsWritten() {
        User user = new User();
        user.setTags(null);
        user.setScores(null);
        user.setLevel(0);
        byte[] bytes = ProtoVia.toBytes(user);
        assertArrayEquals(new byte[]{0x30, 0x00}, bytes);
        User back = ProtoVia.fromBytes(User.class, bytes);
        assertEquals(0, back.getLevel());
    }

    @Test
    void defaultAgeIsOmitted() {
        User user = new User();
        user.setName("a");
        user.setAge(0);
        user.setTags(null);
        user.setScores(null);
        byte[] bytes = ProtoVia.toBytes(user);
        assertArrayEquals(new byte[]{0x0A, 0x01, 'a'}, bytes);
    }

    @Test
    void streamRoundTrip() {
        User user = sample();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ProtoVia.write(out, user);
        User back = ProtoVia.read(User.class, new ByteArrayInputStream(out.toByteArray()));
        assertEquals(user, back);
    }

    @Test
    void packUnpackUserAndInstant() {
        User user = sample();
        ProtoAny packed = ProtoVia.pack(user);
        assertEquals("type.googleapis.com/User", packed.typeUrl());
        assertTrue(ProtoVia.is(packed, User.class));
        assertEquals(user, ProtoVia.unpack(packed, User.class));

        Instant at = Instant.parse("2020-01-02T03:04:05.006Z");
        ProtoAny time = ProtoVia.pack(at);
        assertEquals("type.googleapis.com/google.protobuf.Timestamp", time.typeUrl());
        assertEquals(at, ProtoVia.unpack(time, Instant.class));
        assertThrows(ProtoException.class, () -> ProtoVia.unpack(time, Int32Value.class));
    }

    @Test
    void nullMessageRejected() {
        assertThrows(NullPointerException.class, () -> ProtoVia.toBytes(null));
    }

    @Test
    void missingCodec() {
        assertThrows(ProtoException.class, () -> ProtoVia.toBytes("not-a-message"));
    }

    @Test
    void skipUnknownFieldOnRead() {
        User user = new User();
        user.setName("n");
        user.setTags(null);
        user.setScores(null);
        byte[] known = ProtoVia.toBytes(user);
        // append unknown field 15 string "x"
        byte[] extra = new byte[known.length + 3];
        System.arraycopy(known, 0, extra, 0, known.length);
        extra[known.length] = 0x7A; // field 15, LEN
        extra[known.length + 1] = 0x01;
        extra[known.length + 2] = 'x';
        User back = ProtoVia.fromBytes(User.class, extra);
        assertEquals("n", back.getName());
    }

    private static User sample() {
        User user = new User();
        user.setName("Ada");
        user.setAge(36);
        user.setTags(List.of("dev", "java"));
        User.Address address = new User.Address();
        address.setCity("Paris");
        address.setStreet("Rue");
        user.setAddress(address);
        user.setScores(Map.of("math", 99, "eng", 70));
        user.setLevel(0);
        return user;
    }
}
