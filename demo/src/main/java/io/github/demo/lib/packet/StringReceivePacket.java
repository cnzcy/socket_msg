package io.github.demo.lib.packet;

import java.io.IOException;

import io.github.demo.lib.core.ReceivePacket;

/**
 * 接收的字符串包
 */
public class StringReceivePacket extends ReceivePacket {

    private byte[] buffer;
    private int position;


    public StringReceivePacket(int len) {
        buffer = new byte[len];
        length = len;
    }

    @Override
    public void save(byte[] bytes, int count) {
        // 接收的数据保存到buffer，buffer数据进行叠加
        System.arraycopy(bytes, 0, buffer, position, count);
        position += count;
    }

    // 获取真实的String
    public String string(){
        return new String(buffer);
    }

    @Override
    public void close() throws IOException {

    }
}
