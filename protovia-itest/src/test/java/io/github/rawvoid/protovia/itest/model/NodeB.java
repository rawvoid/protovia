package io.github.rawvoid.protovia.itest.model;

import io.github.rawvoid.protovia.annotation.ProtoField;
import io.github.rawvoid.protovia.annotation.ProtoMessage;

@ProtoMessage
public class NodeB {
    @ProtoField(number = 1)
    public String name;
    @ProtoField(number = 2)
    public NodeA next;
}
