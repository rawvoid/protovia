package io.github.rawvoid.protovia.itest.model;

import io.github.rawvoid.protovia.ProtoAny;
import io.github.rawvoid.protovia.annotation.ProtoField;
import io.github.rawvoid.protovia.annotation.ProtoMessage;
import io.github.rawvoid.protovia.wkt.Int32Value;

@ProtoMessage(name = "Carrier", packageName = "example.v1")
public class Carrier {

    @ProtoField(number = 1)
    public String name;

    @ProtoField(number = 2)
    public ProtoAny extra;

    @ProtoField(number = 3)
    public Int32Value count;
}
