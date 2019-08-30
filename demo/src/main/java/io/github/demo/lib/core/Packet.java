package io.github.demo.lib.core;

import java.io.Closeable;

/**
 * 数据包
 * 定义数据类型和长度
 */
public abstract class Packet implements Closeable {
    protected byte type;
    protected int length;

    public byte type(){
        return type;
    }

    public int length(){
        return length;
    }
}
