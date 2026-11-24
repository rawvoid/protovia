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

package io.github.rawvoid.protovia.adapter;

import io.github.rawvoid.protovia.ProtoException;
import io.github.rawvoid.protovia.ProtoType;
import io.github.rawvoid.protovia.annotation.ProtoScalar;
import io.github.rawvoid.protovia.codec.ProtoAdapter;

import java.net.InetAddress;
import java.net.UnknownHostException;

/**
 * Opt-in {@link InetAddress} as proto3 {@code string} (e.g. {@code "192.168.1.1"} or {@code "2001:db8::1"}).
 * Parses strict IPv4 and IPv6 address literals in memory without performing DNS resolution.
 * Unused unless named in {@code @ProtoField(adapter)} / {@code @ProtoAdapters}.
 *
 * @author Rawvoid
 */
@ProtoScalar(ProtoType.STRING)
public final class InetAddressStringAdapter implements ProtoAdapter<InetAddress, String> {

    public static final InetAddressStringAdapter INSTANCE = new InetAddressStringAdapter();

    private InetAddressStringAdapter() {
    }

    @Override
    public String toWire(InetAddress value) {
        return value.getHostAddress();
    }

    @Override
    public InetAddress fromWire(String wire) {
        if (wire == null || wire.isEmpty()) {
            throw new ProtoException("invalid IP address string: " + wire);
        }
        byte[] addr = parseIpLiteral(wire);
        if (addr == null) {
            throw new ProtoException("invalid IP address string: " + wire);
        }
        try {
            return InetAddress.getByAddress(addr);
        } catch (UnknownHostException e) {
            throw new ProtoException("invalid IP address string: " + wire, e);
        }
    }

    private static byte[] parseIpLiteral(String s) {
        if (s.indexOf(':') >= 0) {
            return parseIpv6(s);
        }
        byte[] addr = new byte[4];
        return parseIpv4(s, 0, s.length(), addr, 0) ? addr : null;
    }

    private static boolean parseIpv4(String s, int start, int end, byte[] dst, int dstOffset) {
        int pos = start;
        for (int octet = 0; octet < 4; octet++) {
            if (pos >= end) {
                return false;
            }
            int octetStart = pos;
            int value = 0;
            while (pos < end) {
                char c = s.charAt(pos);
                if (c == '.') {
                    break;
                }
                if (c < '0' || c > '9') {
                    return false;
                }
                if (pos - octetStart >= 3) {
                    return false;
                }
                value = value * 10 + (c - '0');
                pos++;
            }
            int digits = pos - octetStart;
            if (digits == 0 || (digits > 1 && s.charAt(octetStart) == '0') || value > 255) {
                return false;
            }
            dst[dstOffset + octet] = (byte) value;
            if (octet < 3) {
                if (pos >= end || s.charAt(pos) != '.') {
                    return false;
                }
                pos++;
            }
        }
        return pos == end;
    }

    private static byte[] parseIpv6(String s) {
        int len = s.length();
        int doubleColon = s.indexOf("::");
        byte[] addr = new byte[16];
        if (doubleColon == -1) {
            int count = parseIpv6Part(s, 0, len, true, addr, 8);
            return count == 8 ? addr : null;
        }
        if (s.indexOf("::", doubleColon + 1) != -1) {
            return null;
        }
        int leftCount = 0;
        if (doubleColon > 0) {
            leftCount = parseIpv6Part(s, 0, doubleColon, false, addr, 7);
            if (leftCount < 0) {
                return null;
            }
        }
        int rightStart = doubleColon + 2;
        if (rightStart < len) {
            int maxRight = 7 - leftCount;
            if (maxRight == 0) {
                return null;
            }
            byte[] rightBuf = new byte[maxRight * 2];
            int rightCount = parseIpv6Part(s, rightStart, len, true, rightBuf, maxRight);
            if (rightCount < 0) {
                return null;
            }
            System.arraycopy(rightBuf, 0, addr, 16 - rightCount * 2, rightCount * 2);
        }
        return addr;
    }

    private static int parseIpv6Part(String s, int start, int end, boolean allowIpv4, byte[] dst, int maxGroups) {
        int pos = start;
        int groups = 0;
        while (true) {
            int colon = s.indexOf(':', pos);
            int segEnd = (colon == -1 || colon >= end) ? end : colon;
            boolean isLast = (segEnd == end);
            if (segEnd == pos) {
                return -1;
            }
            if (isLast && allowIpv4 && segEnd - pos <= 15 && containsDot(s, pos, segEnd)) {
                if (groups + 2 > maxGroups) {
                    return -1;
                }
                if (!parseIpv4(s, pos, segEnd, dst, groups * 2)) {
                    return -1;
                }
                groups += 2;
            } else {
                if (groups >= maxGroups) {
                    return -1;
                }
                int value = parseHexGroup(s, pos, segEnd);
                if (value < 0) {
                    return -1;
                }
                dst[groups * 2] = (byte) (value >>> 8);
                dst[groups * 2 + 1] = (byte) value;
                groups++;
            }
            if (isLast) {
                return groups;
            }
            pos = segEnd + 1;
            if (pos == end) {
                return -1;
            }
        }
    }

    private static boolean containsDot(String s, int start, int end) {
        for (int i = start; i < end; i++) {
            if (s.charAt(i) == '.') {
                return true;
            }
        }
        return false;
    }

    private static int parseHexGroup(String s, int start, int end) {
        int len = end - start;
        if (len < 1 || len > 4) {
            return -1;
        }
        int value = 0;
        for (int i = start; i < end; i++) {
            char c = s.charAt(i);
            int digit;
            if (c >= '0' && c <= '9') {
                digit = c - '0';
            } else if (c >= 'a' && c <= 'f') {
                digit = c - 'a' + 10;
            } else if (c >= 'A' && c <= 'F') {
                digit = c - 'A' + 10;
            } else {
                return -1;
            }
            value = (value << 4) | digit;
        }
        return value;
    }
}
