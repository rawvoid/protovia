package io.github.rawvoid.protovia.itest.model;

import io.github.rawvoid.protovia.annotation.ProtoOneofCase;

@ProtoOneofCase(11)
public record Home(Address address) implements Target {
}
