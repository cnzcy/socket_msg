package io.github.demo.lib.core;

import java.io.Closeable;

/**
 * 发送数据的调度者
 * 缓存所有发送的数据，通过对列进行发送
 * 对数据进行包装
 */
public interface SendDispatcher extends Closeable {

    /**
     * 发送一份数据
     * @param packet
     */
    void send(SendPacket packet);

    /**
     * 取消发送数据
     * @param packet
     */
    void cancel(SendPacket packet);

}
