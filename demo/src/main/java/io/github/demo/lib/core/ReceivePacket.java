package io.github.demo.lib.core;

/**
 * 接收的包
 */
public abstract class ReceivePacket extends Packet{

    // 保存的数量，不一定全部保存
    public abstract void save(byte[] bytes, int count);

}
