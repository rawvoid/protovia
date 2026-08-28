/*
 * Copyright 2026 Rawvoid(https://github.com/rawvoid)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.github.rawvoid.protovia.processor.model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * @author Rawvoid
 */
class ProtoIdentTest {

    @Test
    void snakeCaseSplitsCamelAndAcronyms() {
        assertEquals("cabin_class", ProtoIdent.toSnakeCase("CabinClass"));
        assertEquals("no_show_fee_combination", ProtoIdent.toSnakeCase("NoShowFeeCombination"));
        assertEquals("endorsement_carrier_scope", ProtoIdent.toSnakeCase("EndorsementCarrierScope"));
        assertEquals("ancillary_booking_rq", ProtoIdent.toSnakeCase("AncillaryBookingRQ"));
        assertEquals("flight_offer_id", ProtoIdent.toSnakeCase("FlightOfferId"));
        assertEquals("xml_http_request", ProtoIdent.toSnakeCase("XMLHttpRequest"));
        assertEquals("io_error", ProtoIdent.toSnakeCase("IOError"));
        assertEquals("user", ProtoIdent.toSnakeCase("User"));
        assertEquals("cabin_class", ProtoIdent.toSnakeCase("cabin_class"));
    }

    @Test
    void enumConstantNamePrefixesUpperSnakeType() {
        assertEquals("CABIN_CLASS_BUSINESS", ProtoIdent.enumConstantName("CabinClass", "BUSINESS"));
        assertEquals("ERROR_CATEGORY_SEAT", ProtoIdent.enumConstantName("ErrorCategory", "SEAT"));
        assertEquals("ANCILLARY_CATEGORY_SEAT", ProtoIdent.enumConstantName("AncillaryCategory", "SEAT"));
        assertEquals("STATUS_UNKNOWN", ProtoIdent.enumConstantName("Status", "UNKNOWN"));
        assertEquals("STATUS_UNKNOWN", ProtoIdent.enumConstantName("Status", "Unknown"));
        assertEquals("STATUS_UNKNOWN", ProtoIdent.enumConstantName("Status", "unknown"));
        assertEquals("STATUS_ACTIVE_USER", ProtoIdent.enumConstantName("Status", "ActiveUser"));
        assertEquals("STATUS_HTTP_OK", ProtoIdent.enumConstantName("Status", "HTTP_OK"));
        assertEquals("FOO_BAR_UNKNOWN", ProtoIdent.enumConstantName("FooBar", "UNKNOWN"));
    }
}
