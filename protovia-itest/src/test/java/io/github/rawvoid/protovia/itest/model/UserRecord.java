package io.github.rawvoid.protovia.itest.model;

import io.github.rawvoid.protovia.annotation.ProtoField;
import io.github.rawvoid.protovia.annotation.ProtoMessage;

import java.util.List;
import java.util.Map;
import java.util.Optional;

@ProtoMessage
public record UserRecord(
        @ProtoField(number = 1) String name,
        @ProtoField(number = 2) int age,
        @ProtoField(number = 3) Address address,
        @ProtoField(number = 4) List<String> tags,
        @ProtoField(number = 5) Map<String, Integer> scores,
        @ProtoField(number = 6) Status status,
        @ProtoField(number = 7, optional = true) Optional<Integer> level
) {
}
