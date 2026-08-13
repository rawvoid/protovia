package io.github.rawvoid.protovia.itest.model;

import io.github.rawvoid.protovia.annotation.ProtoField;
import io.github.rawvoid.protovia.annotation.ProtoMessage;

import java.time.Duration;
import java.time.Instant;

@ProtoMessage
public class Timed {

    @ProtoField(number = 1)
    public Instant at;

    @ProtoField(number = 2)
    public Duration wait;
}
