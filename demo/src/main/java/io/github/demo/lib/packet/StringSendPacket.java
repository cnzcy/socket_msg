package io.github.demo.lib.packet;

import java.io.IOException;

import io.github.demo.lib.core.SendPacket;

/**
 * 字符串的发送包
 */
public class StringSendPacket extends SendPacket {

    private final byte[] bytes;

    public StringSendPacket(String msg) {
        this.bytes = msg.getBytes();
        this.length = bytes.length;
    }

    @Override
    public byte[] bytes() {
        return bytes;
    }

    @Override
    public void close() throws IOException {

    }
}
