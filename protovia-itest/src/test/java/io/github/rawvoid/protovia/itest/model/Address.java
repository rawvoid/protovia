package io.github.rawvoid.protovia.itest.model;

import io.github.rawvoid.protovia.annotation.ProtoField;
import io.github.rawvoid.protovia.annotation.ProtoMessage;

@ProtoMessage
public record Address(
    @ProtoField(number = 1) String city,
    @ProtoField(number = 2) String street
) {
}
