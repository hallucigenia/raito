// SPDX-License-Identifier: Apache-2.0
package io.github.hallucigenia.raito;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;

/**
 * Hand-built Hyperion replies (hyperion_reply.fbs: error:string, video:int, registered:int).
 * Layout: root offset, vtable at 4, table at 16, field data from 20.
 */
final class Replies {
    private Replies() { }

    static byte[] registered(int priority) {
        ByteBuffer b = table(new short[] {0, 0, 4}, 24);
        b.putInt(20, priority);
        return b.array();
    }

    static byte[] empty() {
        return table(new short[] {0, 0, 0}, 20).array();
    }

    static byte[] error(String message) {
        byte[] text = message.getBytes(StandardCharsets.UTF_8);
        ByteBuffer b = table(new short[] {4, 0, 0}, 29 + text.length);
        b.putInt(20, 4);
        b.putInt(24, text.length);
        b.position(28);
        b.put(text);
        return b.array();
    }

    private static ByteBuffer table(short[] fieldOffsets, int size) {
        ByteBuffer b = ByteBuffer.allocate((size + 3) & ~3).order(ByteOrder.LITTLE_ENDIAN);
        b.putInt(0, 16);
        b.putShort(4, (short) (4 + fieldOffsets.length * 2));
        b.putShort(6, (short) 8);
        for (int i = 0; i < fieldOffsets.length; i++) b.putShort(8 + i * 2, fieldOffsets[i]);
        b.putInt(16, 12);
        return b;
    }
}
