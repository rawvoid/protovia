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

package io.github.rawvoid.protovia.bench;

import io.github.rawvoid.protovia.Protovia;
import io.github.rawvoid.protovia.bench.model.User;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * @author Rawvoid
 */
class WireParityTest {

    @Test
    void officialBytesAreReadableByProtovia() throws Exception {
        User back = Protovia.fromBytes(User.class, Samples.officialSmall().toByteArray());
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
            io.github.rawvoid.protovia.bench.official.User.parseFrom(Protovia.toBytes(Samples.protoviaSmall()));
        assertEquals("Ada", back.getName());
        assertEquals(36, back.getAge());
        assertEquals("Paris", back.getAddress().getCity());
        assertEquals(99, back.getScoresOrThrow("math"));
        assertEquals(3, back.getRanksCount());
    }

    @Test
    void cjkAndPackedRoundTrip() throws Exception {
        User cjk = Protovia.fromBytes(User.class, Samples.officialCjk().toByteArray());
        assertEquals(Samples.CJK_BIO, cjk.bio);
        io.github.rawvoid.protovia.bench.official.User officialCjk =
            io.github.rawvoid.protovia.bench.official.User.parseFrom(Protovia.toBytes(Samples.protoviaCjk()));
        assertEquals(Samples.CJK_BIO, officialCjk.getBio());

        User packed = Protovia.fromBytes(User.class, Samples.officialPacked().toByteArray());
        assertEquals(256, packed.ranks.size());
        assertEquals(255, packed.ranks.get(255));
    }
}
