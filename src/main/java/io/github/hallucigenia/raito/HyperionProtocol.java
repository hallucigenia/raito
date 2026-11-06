// SPDX-License-Identifier: Apache-2.0
package io.github.hallucigenia.raito;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;

/**
 * Hand-encoded Hyperion FlatBuffers messages, so Raito needs no runtime library.
 *
 * <p>Schema (hyperion_request.fbs / hyperion_reply.fbs):
 * {@code Request{command:Command}}, {@code union Command{Color=1, Image=2, Clear=3, Register=4}},
 * {@code Register{origin:string, priority:int}},
 * {@code Image{data:ImageType, duration:int}}, {@code union ImageType{RawImage=1}},
 * {@code RawImage{data:[ubyte], width:int, height:int}},
 * {@code Reply{error:string, video:int, registered:int}}.
 *
 * <p>Every request uses one fixed layout: root offset (0), Request vtable (4), Request table (16),
 * command vtable (28), command table (40). A union occupies two vtable slots (type, then value).
 * Each packet is sent with a big-endian int32 length prefix.
 */
final class HyperionProtocol {
    static final int COMMAND_IMAGE = 2;
    static final int COMMAND_REGISTER = 4;
    static final int IMAGE_TYPE_RAW = 1;
    static final int MAX_REPLY_BYTES = 1024 * 1024;

    private HyperionProtocol() { }

    static byte[] registerPacket(String origin, int priority) {
        byte[] name = origin.getBytes(StandardCharsets.UTF_8);
        ByteBuffer b = little(57 + name.length);
        request(b, COMMAND_REGISTER);
        // Register vtable at 28: origin at +4, priority at +8.
        b.putShort(28, (short) 8); b.putShort(30, (short) 12);
        b.putShort(32, (short) 4); b.putShort(34, (short) 8);
        // Register table at 40; origin string at 52.
        b.putInt(40, 12); b.putInt(44, 8); b.putInt(48, priority);
        b.putInt(52, name.length); b.position(56); b.put(name); b.put((byte) 0);
        return b.array();
    }

    static byte[] imagePacket(byte[] rgb, int width, int height, int durationMs) {
        if ((long) width * height * 3 != rgb.length) throw new IllegalArgumentException("RGB length mismatch");
        ByteBuffer b = little(88 + rgb.length);
        request(b, COMMAND_IMAGE);
        // Image vtable at 28: data type at +4, data at +8, duration at +12.
        b.putShort(28, (short) 10); b.putShort(30, (short) 16);
        b.putShort(32, (short) 4); b.putShort(34, (short) 8); b.putShort(36, (short) 12);
        // Image table at 40; RawImage table at 68.
        b.putInt(40, 12); b.put(44, (byte) IMAGE_TYPE_RAW); b.putInt(48, 20); b.putInt(52, durationMs);
        // RawImage vtable at 56: data at +4, width at +8, height at +12.
        b.putShort(56, (short) 10); b.putShort(58, (short) 16);
        b.putShort(60, (short) 4); b.putShort(62, (short) 8); b.putShort(64, (short) 12);
        // RawImage table at 68; byte vector at 84.
        b.putInt(68, 12); b.putInt(72, 12); b.putInt(76, width); b.putInt(80, height);
        b.putInt(84, rgb.length); b.position(88); b.put(rgb);
        return b.array();
    }

    static void write(DataOutputStream out, byte[] packet) throws IOException {
        out.writeInt(packet.length);
        out.write(packet);
        out.flush();
    }

    /** Reads one reply; returns the registered priority, or -1 when the field is absent. */
    static int reply(DataInputStream in) throws IOException {
        int size = in.readInt();
        if (size < 8 || size > MAX_REPLY_BYTES) throw new IOException("Invalid reply size " + size);
        byte[] bytes = new byte[size];
        in.readFully(bytes);
        ByteBuffer b = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN);
        int table = b.getInt(0);
        if (table < 4 || table + 4 > size) throw new IOException("Invalid reply root");
        int error = field(b, table, 0);
        if (error != 0) {
            int pointer = table + error;
            int text = pointer + b.getInt(pointer);
            int length = b.getInt(text);
            if (length < 0 || text + 4 + length > size) throw new IOException("Invalid reply error string");
            if (length > 0) throw new IOException(new String(bytes, text + 4, length, StandardCharsets.UTF_8));
        }
        int registered = field(b, table, 2);
        return registered == 0 ? -1 : b.getInt(table + registered);
    }

    /** Offset of a table field relative to the table, or 0 when absent. */
    static int field(ByteBuffer b, int table, int index) throws IOException {
        int vtable = table - b.getInt(table);
        if (vtable < 0 || vtable + 4 > b.limit()) throw new IOException("Invalid vtable");
        int length = b.getShort(vtable) & 0xffff;
        int position = vtable + 4 + index * 2;
        if (position + 2 > vtable + length) return 0;
        return b.getShort(position) & 0xffff;
    }

    private static ByteBuffer little(int size) {
        return ByteBuffer.allocate((size + 3) & ~3).order(ByteOrder.LITTLE_ENDIAN);
    }

    /** Request vtable and table: command type at +4, command offset at +8 pointing to 40. */
    private static void request(ByteBuffer b, int command) {
        b.putInt(0, 16);
        b.putShort(4, (short) 8); b.putShort(6, (short) 12);
        b.putShort(8, (short) 4); b.putShort(10, (short) 8);
        b.putInt(16, 12); b.put(20, (byte) command); b.putInt(24, 16);
    }
}
