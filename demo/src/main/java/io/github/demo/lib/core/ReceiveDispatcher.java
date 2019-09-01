package io.github.demo.lib.core;

import java.io.Closeable;

/**
 * 接收者调度
 * IoArgs 组合成 packet
 */
public interface ReceiveDispatcher extends Closeable {

    void start();

    void stop();

    // 接收到数据的回调
    interface ReceivePacketCallback{
        ReceivePacket<?, ?> onArrivedNewPacket(byte type, long length);

        void onReceivePacketCompleted(ReceivePacket packet);
    }

}
