package io.github.rawvoid.protovia.bench;

import io.github.rawvoid.protovia.ProtoVia;
import io.github.rawvoid.protovia.bench.model.User;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class WireParityTest {

    @Test
    void officialBytesAreReadableByProtovia() throws Exception {
        User back = ProtoVia.fromBytes(User.class, Samples.officialSmall().toByteArray());
        assertEquals("Ada", back.name);
        assertEquals(36, back.age);
        assertEquals("Paris", back.address.city);
        assertEquals("Rue", back.address.street);
        assertEquals(99, back.scores.get("math"));
        assertEquals(3, back.ranks.size());
        assertEquals("dev", back.bio);
    }

    @Test
    void protoviaBytesAreReadableByOfficial() throws Exception {
        io.github.rawvoid.protovia.bench.official.User back =
            io.github.rawvoid.protovia.bench.official.User.parseFrom(ProtoVia.toBytes(Samples.protoviaSmall()));
        assertEquals("Ada", back.getName());
        assertEquals(36, back.getAge());
        assertEquals("Paris", back.getAddress().getCity());
        assertEquals(99, back.getScoresOrThrow("math"));
        assertEquals(3, back.getRanksCount());
    }

    @Test
    void cjkAndPackedRoundTrip() throws Exception {
        User cjk = ProtoVia.fromBytes(User.class, Samples.officialCjk().toByteArray());
        assertEquals(Samples.CJK_BIO, cjk.bio);
        io.github.rawvoid.protovia.bench.official.User officialCjk =
            io.github.rawvoid.protovia.bench.official.User.parseFrom(ProtoVia.toBytes(Samples.protoviaCjk()));
        assertEquals(Samples.CJK_BIO, officialCjk.getBio());

        User packed = ProtoVia.fromBytes(User.class, Samples.officialPacked().toByteArray());
        assertEquals(256, packed.ranks.size());
        assertEquals(255, packed.ranks.get(255));
    }
}
