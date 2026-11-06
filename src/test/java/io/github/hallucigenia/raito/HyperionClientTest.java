// SPDX-License-Identifier: Apache-2.0
package io.github.hallucigenia.raito;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;

/** Talks to a scripted local Hyperion stand-in over a real socket. */
public final class HyperionClientTest {
    interface Script { byte[] replyTo(int requestIndex, byte[] request); }

    static final class FakeServer implements AutoCloseable {
        final ServerSocket server;
        final BlockingQueue<byte[]> requests = new ArrayBlockingQueue<byte[]>(16);
        final Thread thread;

        FakeServer(final Script script) throws IOException {
            server = new ServerSocket(0, 1, InetAddress.getLoopbackAddress());
            thread = new Thread(() -> {
                try (Socket socket = server.accept()) {
                    DataInputStream in = new DataInputStream(socket.getInputStream());
                    DataOutputStream out = new DataOutputStream(socket.getOutputStream());
                    for (int i = 0; ; i++) {
                        byte[] request = new byte[in.readInt()];
                        in.readFully(request);
                        requests.add(request);
                        byte[] reply = script.replyTo(i, request);
                        out.writeInt(reply.length);
                        out.write(reply);
                        out.flush();
                    }
                } catch (IOException ignored) {
                    // Client closed the connection; the test is over.
                }
            });
            thread.setDaemon(true);
            thread.start();
        }

        int port() { return server.getLocalPort(); }

        byte[] nextRequest() throws InterruptedException {
            byte[] request = requests.poll(2, TimeUnit.SECONDS);
            if (request == null) throw new AssertionError("server received no request");
            return request;
        }

        public void close() throws IOException { server.close(); }
    }

    static HyperionClient connect(FakeServer server) throws IOException {
        return HyperionClient.connect("127.0.0.1", server.port(), 150, "test-origin", 1500);
    }

    static byte commandOf(byte[] request) throws IOException {
        ByteBuffer b = ByteBuffer.wrap(request).order(ByteOrder.LITTLE_ENDIAN);
        int root = b.getInt(0);
        return b.get(root + HyperionProtocol.field(b, root, 0));
    }

    public void registers_then_sends_acknowledged_frames() throws Exception {
        try (FakeServer server = new FakeServer((i, request) -> Replies.registered(150));
             HyperionClient client = connect(server)) {
            Check.equal((byte) 4, commandOf(server.nextRequest()), "first request registers");
            client.send(new Frame(new byte[] {1, 2, 3}, 1, 1));
            Check.equal((byte) 2, commandOf(server.nextRequest()), "then an image");
        }
    }

    public void rejects_registration_at_another_priority() throws Exception {
        try (FakeServer server = new FakeServer((i, request) -> Replies.registered(160))) {
            Check.fails(IOException.class, () -> connect(server), "priority mismatch");
        }
    }

    public void fails_send_when_hyperion_drops_the_registration() throws Exception {
        Script script = (i, request) -> i == 0 ? Replies.registered(150) : Replies.empty();
        try (FakeServer server = new FakeServer(script); HyperionClient client = connect(server)) {
            Check.fails(IOException.class,
                () -> client.send(new Frame(new byte[] {1, 2, 3}, 1, 1)), "re-registration requested");
        }
    }

    public void surfaces_server_error_text() throws Exception {
        try (FakeServer server = new FakeServer((i, request) -> Replies.error("Bad origin"))) {
            IOException e = Check.fails(IOException.class, () -> connect(server), "error reply");
            Check.equal("Bad origin", e.getMessage(), "server message");
        }
    }

    public void fails_fast_when_nothing_listens() throws Exception {
        int port;
        try (ServerSocket probe = new ServerSocket(0, 1, InetAddress.getLoopbackAddress())) {
            port = probe.getLocalPort();
        }
        final int closedPort = port;
        Check.fails(IOException.class,
            () -> HyperionClient.connect("127.0.0.1", closedPort, 150, "o", 1500), "refused");
    }
}
