package io.github.rawvoid.protovia.itest.model;

import io.github.rawvoid.protovia.annotation.ProtoField;
import io.github.rawvoid.protovia.annotation.ProtoMessage;
import io.github.rawvoid.protovia.annotation.ProtoOneof;

@ProtoMessage
public class Contact {

    @ProtoField(number = 1)
    public String name;

    @ProtoOneof
    public Target target;
}
