// SPDX-License-Identifier: Apache-2.0
package io.github.hallucigenia.raito;

import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

public final class HyperionProtocolTest {
    static ByteBuffer little(byte[] bytes) {
        return ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN);
    }

    static int child(ByteBuffer b, int table, int index) throws IOException {
        int pointer = table + HyperionProtocol.field(b, table, index);
        return pointer + b.getInt(pointer);
    }

    public void encodes_register_command_and_priority() throws IOException {
        ByteBuffer b = little(HyperionProtocol.registerPacket("test-origin", 152));
        int root = b.getInt(0);
        Check.equal((byte) 4, b.get(root + HyperionProtocol.field(b, root, 0)), "Register command enum");
        int register = child(b, root, 1);
        Check.equal(152, b.getInt(register + HyperionProtocol.field(b, register, 1)), "priority");
        int origin = child(b, register, 0);
        byte[] name = new byte[b.getInt(origin)];
        b.position(origin + 4);
        b.get(name);
        Check.equal("test-origin", new String(name, "UTF-8"), "origin");
    }

    public void encodes_raw_rgb_image_with_expiry() throws IOException {
        byte[] rgb = {1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12};
        ByteBuffer b = little(HyperionProtocol.imagePacket(rgb, 2, 2, 1500));
        int root = b.getInt(0);
        Check.equal((byte) 2, b.get(root + HyperionProtocol.field(b, root, 0)), "Image command enum");
        int image = child(b, root, 1);
        Check.equal(1500, b.getInt(image + HyperionProtocol.field(b, image, 2)), "frame expiry");
        int raw = child(b, image, 1);
        Check.equal(2, b.getInt(raw + HyperionProtocol.field(b, raw, 1)), "width");
        Check.equal(2, b.getInt(raw + HyperionProtocol.field(b, raw, 2)), "height");
        int vector = child(b, raw, 0);
        Check.equal(rgb.length, b.getInt(vector), "RGB vector length");
        byte[] decoded = new byte[rgb.length];
        b.position(vector + 4);
        b.get(decoded);
        Check.bytes(rgb, decoded, "RGB vector content");
    }

    public void rejects_truncated_rgb_payload() {
        Check.fails(IllegalArgumentException.class,
            () -> HyperionProtocol.imagePacket(new byte[2], 2, 2, 1), "short payload");
    }

    public void reads_registered_priority_from_reply() throws IOException {
        Check.equal(150, HyperionProtocol.reply(stream(Replies.registered(150))), "registered priority");
    }

    public void reports_missing_registration_as_minus_one() throws IOException {
        Check.equal(-1, HyperionProtocol.reply(stream(Replies.empty())), "no registered field");
    }

    public void raises_server_error_text() {
        IOException e = Check.fails(IOException.class,
            () -> HyperionProtocol.reply(stream(Replies.error("Priority out of range"))), "error reply");
        Check.equal("Priority out of range", e.getMessage(), "server message");
    }

    public void rejects_oversized_reply() {
        byte[] size = {0x7f, 0, 0, 0};
        Check.fails(IOException.class, () -> HyperionProtocol.reply(
            new DataInputStream(new ByteArrayInputStream(size))), "oversized reply");
    }

    static DataInputStream stream(byte[] reply) {
        ByteBuffer framed = ByteBuffer.allocate(4 + reply.length);
        framed.putInt(reply.length).put(reply);
        return new DataInputStream(new ByteArrayInputStream(framed.array()));
    }
}
