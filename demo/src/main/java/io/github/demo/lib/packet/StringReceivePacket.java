package io.github.demo.lib.packet;

import java.io.ByteArrayOutputStream;
import java.io.IOException;

import io.github.demo.lib.core.ReceivePacket;

/**
 * 接收的字符串包
 */
public class StringReceivePacket extends ReceivePacket<ByteArrayOutputStream> {

    private String string;

    public StringReceivePacket(int len) {
        length = len;
    }

    // 获取真实的String
    public String string(){
        return string;
    }

    @Override
    protected void closeStream(ByteArrayOutputStream stream) throws IOException {
        super.closeStream(stream);
        string = new String(stream.toByteArray());
    }

    @Override
    protected ByteArrayOutputStream createStream() {
        return new ByteArrayOutputStream((int) length);
    }
}
