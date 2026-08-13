package io.github.rawvoid.protovia.itest.model;

import io.github.rawvoid.protovia.UnknownFields;
import io.github.rawvoid.protovia.annotation.ProtoField;
import io.github.rawvoid.protovia.annotation.ProtoMessage;
import io.github.rawvoid.protovia.annotation.ProtoUnknown;

@ProtoMessage
public class Envelope {

    @ProtoField(number = 1)
    public String name;

    @ProtoField(number = 7)
    public Status status;

    @ProtoUnknown
    public UnknownFields unknownFields;
}
