package io.github.demo.lib.frame;

import java.io.IOException;
import java.io.InputStream;
import java.nio.channels.Channels;
import java.nio.channels.ReadableByteChannel;

import io.github.demo.lib.core.Frame;
import io.github.demo.lib.core.IoArgs;
import io.github.demo.lib.core.SendPacket;

/**
 * 发送的头针
 */
public class SendHeaderFrame extends AbsSendPacketFrame{

    static final int PACKET_HEADER_FRAME_MIN_LENGTH = 6;
    private final byte[] body;

    // 唯一标识
    public SendHeaderFrame(short identifier, SendPacket packet) {
        super(PACKET_HEADER_FRAME_MIN_LENGTH, Frame.TYPE_PACKET_HEADER,
                Frame.FLAG_NONE, identifier, packet);


        final long packetLength = packet.length();
        final byte packetType = packet.type();
        final byte[] packetHeaderInfo = packet.headerInfo();

        this.body = new byte[bodyRemaining];

        // 00000000 00000000 00000000 00000000 00000000 00000000 00000000 00000000
        // 5个字节标识body的长度
        body[0] = (byte) (packetLength >> 32);
        body[1] = (byte) (packetLength >> 24);
        body[2] = (byte) (packetLength >> 16);
        body[3] = (byte) (packetLength >> 8);
        body[4] = (byte) (packetLength);

        body[5] = packetType;// 类型

        if(packetHeaderInfo != null){
            System.arraycopy(packetHeaderInfo, 0, body, PACKET_HEADER_FRAME_MIN_LENGTH, packetHeaderInfo.length);
        }
    }

    @Override
    protected int consumeBody(IoArgs args) throws IOException {
        int count = bodyRemaining;
        int offset = body.length - count;
        return args.readFrom(body, offset, count);
    }

    @Override
    public Frame buildNextFrame() {
        InputStream stream = packet.open();
        ReadableByteChannel channel = Channels.newChannel(stream);
        return new SendEntityFrame(getBodyIdentifier(), packet.length(), channel, packet);
    }
}
