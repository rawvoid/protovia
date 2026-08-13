package io.github.rawvoid.protovia.itest.model;

import io.github.rawvoid.protovia.annotation.ProtoOneofCase;

@ProtoOneofCase(10)
public record Email(String value) implements Target {
}
