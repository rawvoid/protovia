package io.github.rawvoid.protovia.itest.model;

import io.github.rawvoid.protovia.UnknownFields;
import io.github.rawvoid.protovia.annotation.ProtoField;
import io.github.rawvoid.protovia.annotation.ProtoMessage;
import io.github.rawvoid.protovia.annotation.ProtoUnknown;

import java.util.ArrayList;
import java.util.List;

@ProtoMessage
public class Flags {

    @ProtoField(number = 1)
    public List<Status> flags = new ArrayList<>();

    @ProtoUnknown
    public UnknownFields unknownFields;
}
