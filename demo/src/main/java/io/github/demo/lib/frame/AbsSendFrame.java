package io.github.demo.lib.frame;

import java.io.IOException;

import io.github.demo.lib.core.Frame;
import io.github.demo.lib.core.IoArgs;

public abstract class AbsSendFrame extends Frame {

    // 帧头还剩数据
    volatile byte headerRemaining = Frame.FRAME_HEADER_LENGTH;

    // body部分长度
    volatile int bodyRemaining;

    public AbsSendFrame(int length, byte type, byte flag, short identifier) {
        super(length, type, flag, identifier);
        bodyRemaining = length;
    }

    // 不能多线程同时消费
    @Override
    public synchronized boolean handle(IoArgs args) throws IOException {
        try {
            // 数据消费长度
            args.limit(headerRemaining + bodyRemaining);
            args.startWriting();
            if(headerRemaining > 0 && args.remained()){
                headerRemaining -= consumeHeader(args);// 减去消费的长度
            }

            // 头部消费完成，并且还有可消费区间，并且body可消费部分大于0
            if(headerRemaining == 0 && args.remained() && bodyRemaining > 0){
                bodyRemaining -= consumeBody(args);
            }

            return headerRemaining == 0 && bodyRemaining == 0;// 返回头部和body是否消费完成
        } finally {
            args.finishWriting();
        }
    }

    @Override
    public int getConsumableLength() {
        return headerRemaining + bodyRemaining;
    }

    protected byte consumeHeader(IoArgs args){
        int count = headerRemaining;
        int offset = header.length - count;// header 消费到的位置
        return (byte)args.readFrom(header, offset, count);
    }

    protected abstract int consumeBody(IoArgs args) throws IOException;

    // 发送数据 header部分就会减小
    protected synchronized boolean isSending(){
        return headerRemaining < Frame.FRAME_HEADER_LENGTH;
    }
}
