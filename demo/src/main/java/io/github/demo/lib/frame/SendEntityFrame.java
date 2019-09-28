package io.github.demo.lib.frame;

import java.io.IOException;
import java.nio.channels.ReadableByteChannel;

import io.github.demo.lib.core.Frame;
import io.github.demo.lib.core.IoArgs;
import io.github.demo.lib.core.SendPacket;

public class SendEntityFrame extends AbsSendPacketFrame{

    private final long unConsumeEntityLength;// 未消费的长度
    private final ReadableByteChannel channel;

    public SendEntityFrame(short identifier, long entityLength, ReadableByteChannel channel, SendPacket packet) {
        super((int) Math.min(entityLength, Frame.MAX_CAPACITY), Frame.TYPE_PACKET_ENTITY,
                Frame.FLAG_NONE, identifier, packet);

        unConsumeEntityLength = entityLength - bodyRemaining;
        this.channel = channel;
    }

    @Override
    protected int consumeBody(IoArgs args) throws IOException {
        if(packet == null){// 已终止当前帧，填充假数据
            return args.fillEmpty(bodyRemaining);
        }
        return args.readFrom(channel);
    }

    @Override
    public Frame buildNextFrame() {
        if(unConsumeEntityLength == 0){// 所有数据消费完成
            return null;
        }

        return new SendEntityFrame(getBodyIdentifier(), unConsumeEntityLength, channel, packet);
    }
}
