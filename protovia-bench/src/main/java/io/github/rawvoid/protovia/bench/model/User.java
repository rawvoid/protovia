package io.github.rawvoid.protovia.bench.model;

import io.github.rawvoid.protovia.annotation.ProtoField;
import io.github.rawvoid.protovia.annotation.ProtoMessage;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Bench entity matching {@code user.proto}.
 *
 * @author Rawvoid
 */
@ProtoMessage
public class User {

    @ProtoField(number = 1)
    public String name;

    @ProtoField(number = 2)
    public int age;

    @ProtoField(number = 3)
    public List<String> tags = new ArrayList<>();

    @ProtoField(number = 4)
    public Address address;

    @ProtoField(number = 5)
    public Map<String, Integer> scores = new LinkedHashMap<>();

    @ProtoField(number = 6)
    public List<Integer> ranks = new ArrayList<>();

    @ProtoField(number = 7)
    public String bio;
}
