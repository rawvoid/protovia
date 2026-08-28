/*
 * Copyright 2026 Rawvoid(https://github.com/rawvoid)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.github.rawvoid.protovia.itest;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * @author Rawvoid
 */
class ProtoExportTest {

    private static final String MODEL = "io/github/rawvoid/protovia/itest/model/";
    private static final Path PROTO_ROOT =
        Path.of("target/generated-test-sources/test-annotations/proto");

    @Test
    void userProtoIsGenerated() {
        assertEquals("""
            syntax = "proto3";

            package io.github.rawvoid.protovia.itest.model;

            import "io/github/rawvoid/protovia/itest/model/address.proto";
            import "io/github/rawvoid/protovia/itest/model/status.proto";

            message User {
              string name = 1;
              int32 age = 2;
              sint64 score = 3;
              repeated string tags = 4;
              Address address = 5;
              map<string, int32> scores = 6;
              Status status = 7;
              optional int32 level = 8;
              repeated int32 ranks = 9;
              repeated int32 unpacked = 10 [packed = false];
              bytes payload = 11;
            }
            """, resource(MODEL + "user.proto"));
        String status = resource(MODEL + "status.proto");
        assertFalse(status.contains("UNRECOGNIZED"));
        assertTrue(status.contains("STATUS_UNKNOWN = 0;"));
        assertTrue(status.contains("STATUS_ACTIVE = 1;"));
    }

    @Test
    void contactOneofIsPayloadTypeNotJavaWrapper() {
        String contact = resource(MODEL + "contact.proto");
        assertEquals("""
            syntax = "proto3";

            package io.github.rawvoid.protovia.itest.model;

            import "io/github/rawvoid/protovia/itest/model/address.proto";

            message Contact {
              string name = 1;
              oneof target {
                string email = 10;
                Address address = 11;
              }
            }
            """, contact);
        assertFalse(contact.contains("Email"));
        assertFalse(contact.contains("Home"));
    }

    @Test
    void carrierUsesWellKnownImports() {
        assertEquals("""
            syntax = "proto3";

            package example.v1;

            import "google/protobuf/any.proto";
            import "google/protobuf/wrappers.proto";

            message Carrier {
              string name = 1;
              google.protobuf.Any extra = 2;
              google.protobuf.Int32Value count = 3;
            }
            """, resource("example/v1/carrier.proto"));
    }

    @Test
    void datedAdaptersAreScalars() {
        String dated = resource(MODEL + "dated.proto");
        assertTrue(dated.contains("int32 birthDate = 3;"));
        assertTrue(dated.contains("repeated int32 days = 4;"));
        assertTrue(dated.contains("repeated int32 unpacked = 5 [packed = false];"));
        assertTrue(dated.contains("map<string, int32> dates = 6;"));
        assertTrue(dated.contains("string id = 7;"));
        assertTrue(dated.contains("map<string, int32> byId = 8;"));
        assertFalse(dated.contains("LocalDate"));
        assertFalse(dated.contains("UUID"));
        assertFalse(dated.contains("google.protobuf.Timestamp"));
    }

    @Test
    void timedUsesTimestampAndDuration() {
        String timed = resource(MODEL + "timed.proto");
        assertTrue(timed.contains("import \"google/protobuf/duration.proto\";"));
        assertTrue(timed.contains("import \"google/protobuf/timestamp.proto\";"));
        assertTrue(timed.contains("google.protobuf.Timestamp at = 1;"));
        assertTrue(timed.contains("google.protobuf.Duration wait = 2;"));
    }

    @Test
    @EnabledIf("protocOnPath")
    void protocAcceptsUserProto() throws Exception {
        Path desc = Files.createTempFile("protovia-user", ".pb");
        String userProto = MODEL + "user.proto";
        Process process = new ProcessBuilder(
            "protoc",
            "-I", PROTO_ROOT.toString(),
            "--descriptor_set_out=" + desc,
            userProto)
            .redirectErrorStream(true)
            .start();
        String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        assertTrue(process.waitFor(30, TimeUnit.SECONDS));
        assertEquals(0, process.exitValue(), output);
        assertTrue(Files.size(desc) > 0);
    }

    static boolean protocOnPath() {
        try {
            Process process = new ProcessBuilder("protoc", "--version").start();
            return process.waitFor(2, TimeUnit.SECONDS) && process.exitValue() == 0;
        } catch (Exception e) {
            return false;
        }
    }

    private static String resource(String path) {
        Path file = PROTO_ROOT.resolve(path);
        try {
            return Files.readString(file);
        } catch (IOException e) {
            throw new AssertionError("missing generated proto " + file.toAbsolutePath(), e);
        }
    }
}
