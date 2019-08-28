package io.github.demo.lib.core;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;

/**
 * 封装ByteBuffer
 */
public class IoArgs {
    // 复现1：消息不全
    // 发送超过4个byte的长度就复现了
    // 4864728b-e623-4992-a3d3-8df68e7d7255:123
    // 长度：3
    // 4864728b-e623-4992-a3d3-8df68e7d7255:567
    // 长度：3
    // 4864728b-e623-4992-a3d3-8df68e7d7255:90
    // 长度：3
    // 而实际发送的是1234567890
    // private byte[] byteBuffer = new byte[4];
    private byte[] byteBuffer = new byte[256];
    private ByteBuffer buffer = ByteBuffer.wrap(byteBuffer);

    public int read(SocketChannel channel) throws IOException {
        buffer.clear();
        return channel.read(buffer);
    }

    public int write(SocketChannel channel) throws IOException {
        return channel.write(buffer);
    }

    public String bufferString() {
        // 丢弃换行符
        return new String(byteBuffer, 0, buffer.position() - 1);
    }

    public interface IoArgsEventListener {
        void onStarted(IoArgs args);

        void onCompleted(IoArgs args);
    }
}
