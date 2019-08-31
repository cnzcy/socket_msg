package io.github.demo.lib.packet;

import java.io.ByteArrayInputStream;

import io.github.demo.lib.core.SendPacket;

/**
 * 字符串的发送包
 */
public class StringSendPacket extends SendPacket<ByteArrayInputStream> {

    private final byte[] bytes;

    public StringSendPacket(String msg) {
        this.bytes = msg.getBytes();
        this.length = bytes.length;
    }

    @Override
    protected ByteArrayInputStream createStream() {
        return new ByteArrayInputStream(bytes);
    }
}
