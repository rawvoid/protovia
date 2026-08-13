package io.github.rawvoid.protovia.bench.model;

import io.github.rawvoid.protovia.annotation.ProtoField;
import io.github.rawvoid.protovia.annotation.ProtoMessage;

/**
 * Nested address used by the bench {@link User}.
 *
 * @author Rawvoid
 */
@ProtoMessage
public class Address {

    @ProtoField(number = 1)
    public String city;

    @ProtoField(number = 2)
    public String street;

    public Address() {
    }

    public Address(String city, String street) {
        this.city = city;
        this.street = street;
    }
}
