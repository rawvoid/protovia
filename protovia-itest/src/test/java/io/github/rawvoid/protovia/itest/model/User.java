package io.github.rawvoid.protovia.itest.model;

import io.github.rawvoid.protovia.ProtoType;
import io.github.rawvoid.protovia.annotation.ProtoField;
import io.github.rawvoid.protovia.annotation.ProtoMessage;

import java.util.*;

@ProtoMessage
public class User {

    @ProtoField(number = 1)
    private String name;

    @ProtoField(number = 2)
    private int age;

    @ProtoField(number = 3, type = ProtoType.SINT64)
    private long score;

    @ProtoField(number = 4)
    private List<String> tags = new ArrayList<>();

    @ProtoField(number = 5)
    private Address address;

    @ProtoField(number = 6)
    private Map<String, Integer> scores = new LinkedHashMap<>();

    @ProtoField(number = 7)
    private Status status;

    @ProtoField(number = 8, optional = true)
    private Integer level;

    @ProtoField(number = 9)
    private List<Integer> ranks = new ArrayList<>();

    @ProtoField(number = 10, packed = false)
    private List<Integer> unpacked = new ArrayList<>();

    @ProtoField(number = 11)
    private byte[] payload;

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public int getAge() {
        return age;
    }

    public void setAge(int age) {
        this.age = age;
    }

    public long getScore() {
        return score;
    }

    public void setScore(long score) {
        this.score = score;
    }

    public List<String> getTags() {
        return tags;
    }

    public void setTags(List<String> tags) {
        this.tags = tags;
    }

    public Address getAddress() {
        return address;
    }

    public void setAddress(Address address) {
        this.address = address;
    }

    public Map<String, Integer> getScores() {
        return scores;
    }

    public void setScores(Map<String, Integer> scores) {
        this.scores = scores;
    }

    public Status getStatus() {
        return status;
    }

    public void setStatus(Status status) {
        this.status = status;
    }

    public Integer getLevel() {
        return level;
    }

    public void setLevel(Integer level) {
        this.level = level;
    }

    public List<Integer> getRanks() {
        return ranks;
    }

    public void setRanks(List<Integer> ranks) {
        this.ranks = ranks;
    }

    public List<Integer> getUnpacked() {
        return unpacked;
    }

    public void setUnpacked(List<Integer> unpacked) {
        this.unpacked = unpacked;
    }

    public byte[] getPayload() {
        return payload;
    }

    public void setPayload(byte[] payload) {
        this.payload = payload;
    }

    @Override
    public int hashCode() {
        return Objects.hash(name, age, score, tags, address, scores, status, level, ranks, unpacked);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof User user)) {
            return false;
        }
        return age == user.age
            && score == user.score
            && Objects.equals(name, user.name)
            && Objects.equals(tags, user.tags)
            && Objects.equals(address, user.address)
            && Objects.equals(scores, user.scores)
            && status == user.status
            && Objects.equals(level, user.level)
            && Objects.equals(ranks, user.ranks)
            && Objects.equals(unpacked, user.unpacked)
            && Objects.deepEquals(payload, user.payload);
    }
}
