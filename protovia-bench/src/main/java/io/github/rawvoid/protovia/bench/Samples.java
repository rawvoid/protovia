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

import io.github.rawvoid.protovia.bench.model.Address;
import io.github.rawvoid.protovia.bench.model.User;
import io.github.rawvoid.protovia.collect.IntArrayList;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Shared Protovia and official protobuf fixtures for JMH and parity tests.
 *
 * @author Rawvoid
 */
final class Samples {

    static final String CJK_BIO = "你好世界".repeat(32);

    private Samples() {
    }

    static User protoviaSmall() {
        return protoviaUser("Ada", "dev", false);
    }

    static User protoviaCjk() {
        return protoviaUser("李明", CJK_BIO, false);
    }

    static User protoviaPacked() {
        return protoviaUser("Ada", "dev", true);
    }

    static io.github.rawvoid.protovia.bench.official.User officialSmall() {
        return officialUser("Ada", "dev", false);
    }

    static io.github.rawvoid.protovia.bench.official.User officialCjk() {
        return officialUser("李明", CJK_BIO, false);
    }

    static io.github.rawvoid.protovia.bench.official.User officialPacked() {
        return officialUser("Ada", "dev", true);
    }

    private static User protoviaUser(String name, String bio, boolean packed) {
        User user = new User();
        user.name = name;
        user.age = 36;
        user.tags = List.of("dev", "java", "protobuf");
        user.address = new Address("Paris", "Rue");
        user.scores = scores();
        user.ranks = packed ? packedRanksUnboxed() : List.of(1, 2, 3);
        user.bio = bio;
        return user;
    }

    private static io.github.rawvoid.protovia.bench.official.User officialUser(
        String name, String bio, boolean packed) {
        return io.github.rawvoid.protovia.bench.official.User.newBuilder()
            .setName(name)
            .setAge(36)
            .addAllTags(List.of("dev", "java", "protobuf"))
            .setAddress(io.github.rawvoid.protovia.bench.official.Address.newBuilder()
                .setCity("Paris")
                .setStreet("Rue"))
            .putAllScores(scores())
            .addAllRanks(packed ? packedRanks() : List.of(1, 2, 3))
            .setBio(bio)
            .build();
    }

    private static Map<String, Integer> scores() {
        Map<String, Integer> scores = new LinkedHashMap<>();
        scores.put("math", 99);
        scores.put("eng", 70);
        return scores;
    }

    private static List<Integer> packedRanks() {
        List<Integer> ranks = new ArrayList<>(256);
        for (int i = 0; i < 256; i++) {
            ranks.add(i);
        }
        return ranks;
    }

    /**
     * Same numbers as {@link #packedRanks()}, stored unboxed like official {@code IntList}.
     */
    private static IntArrayList packedRanksUnboxed() {
        IntArrayList ranks = new IntArrayList(256);
        for (int i = 0; i < 256; i++) {
            ranks.addInt(i);
        }
        return ranks;
    }
}
