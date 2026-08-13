package io.github.rawvoid.protovia.itest.model;

import io.github.rawvoid.protovia.annotation.ProtoEnum;
import io.github.rawvoid.protovia.annotation.ProtoEnumValue;

@ProtoEnum
public enum Status {
    @ProtoEnumValue(0) UNKNOWN,
    @ProtoEnumValue(1) ACTIVE,
    @ProtoEnumValue(2) BANNED
}
