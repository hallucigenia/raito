// SPDX-License-Identifier: Apache-2.0
package io.github.hallucigenia.raito;

import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;

/** A registered Hyperion FlatBuffers connection. Every frame waits for Hyperion's reply. */
final class HyperionClient implements FrameSink {
    static final int CONNECT_TIMEOUT_MS = 1500;
    static final int REPLY_TIMEOUT_MS = 1500;
    static final int SEND_BUFFER_BYTES = 64 * 1024;

    private final Socket socket;
    private final DataInputStream in;
    private final DataOutputStream out;
    private final int priority;
    private final int frameDurationMs;

    private HyperionClient(Socket socket, int priority, int frameDurationMs) throws IOException {
        this.socket = socket;
        this.in = new DataInputStream(socket.getInputStream());
        // Buffered so the 4-byte length prefix does not go out as separate tiny TCP segments.
        this.out = new DataOutputStream(new BufferedOutputStream(socket.getOutputStream(), SEND_BUFFER_BYTES));
        this.priority = priority;
        this.frameDurationMs = frameDurationMs;
    }

    static HyperionClient connect(String host, int port, int priority, String origin, int frameDurationMs)
            throws IOException {
        Socket socket = new Socket();
        try {
            socket.connect(new InetSocketAddress(host, port), CONNECT_TIMEOUT_MS);
            socket.setTcpNoDelay(true);
            socket.setSoTimeout(REPLY_TIMEOUT_MS);
            HyperionClient client = new HyperionClient(socket, priority, frameDurationMs);
            client.register(origin);
            return client;
        } catch (IOException | RuntimeException e) {
            socket.close();
            throw e;
        }
    }

    static FrameSink.Factory factory(final Config config) {
        return new FrameSink.Factory() {
            public FrameSink open() throws IOException {
                return connect(config.host, config.port, config.priority, config.origin, config.frameDurationMs);
            }
        };
    }

    private void register(String origin) throws IOException {
        HyperionProtocol.write(out, HyperionProtocol.registerPacket(origin, priority));
        int registered = HyperionProtocol.reply(in);
        if (registered != priority) {
            throw new IOException("Hyperion registered priority " + registered + " instead of " + priority);
        }
    }

    public void send(Frame frame) throws IOException {
        HyperionProtocol.write(out, HyperionProtocol.imagePacket(frame.rgb, frame.width, frame.height, frameDurationMs));
        int registered = HyperionProtocol.reply(in);
        if (registered != priority) throw new IOException("Hyperion dropped the registration (reply " + registered + ")");
    }

    public void close() throws IOException {
        socket.close();
    }
}
