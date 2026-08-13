package io.github.rawvoid.protovia.bench;

import io.github.rawvoid.protovia.bench.model.Address;
import io.github.rawvoid.protovia.bench.model.User;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

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
        user.ranks = packed ? packedRanks() : List.of(1, 2, 3);
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
}
