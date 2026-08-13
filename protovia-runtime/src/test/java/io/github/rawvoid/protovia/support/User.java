package io.github.rawvoid.protovia.support;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public class User {

    private String name;
    private int age;
    private List<String> tags = new ArrayList<>();
    private Address address;
    private Map<String, Integer> scores = new LinkedHashMap<>();
    private Integer level;

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

    public Integer getLevel() {
        return level;
    }

    public void setLevel(Integer level) {
        this.level = level;
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
                && Objects.equals(name, user.name)
                && Objects.equals(tags, user.tags)
                && Objects.equals(address, user.address)
                && Objects.equals(scores, user.scores)
                && Objects.equals(level, user.level);
    }

    @Override
    public int hashCode() {
        return Objects.hash(name, age, tags, address, scores, level);
    }

    public static final class Address {
        private String city;
        private String street;

        public String getCity() {
            return city;
        }

        public void setCity(String city) {
            this.city = city;
        }

        public String getStreet() {
            return street;
        }

        public void setStreet(String street) {
            this.street = street;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (!(o instanceof Address address)) {
                return false;
            }
            return Objects.equals(city, address.city) && Objects.equals(street, address.street);
        }

        @Override
        public int hashCode() {
            return Objects.hash(city, street);
        }
    }
}
