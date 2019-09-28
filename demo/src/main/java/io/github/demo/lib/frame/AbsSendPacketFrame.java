package io.github.demo.lib.frame;

import java.io.IOException;

import io.github.demo.lib.core.Frame;
import io.github.demo.lib.core.IoArgs;
import io.github.demo.lib.core.SendPacket;

public abstract class AbsSendPacketFrame extends AbsSendFrame {

    protected volatile SendPacket<?> packet;

    public AbsSendPacketFrame(int length, byte type, byte flag, short identifier, SendPacket packet) {
        super(length, type, flag, identifier);
        this.packet = packet;
    }

    /**
     * 获取当前发送的packet
     * @return
     */
    public synchronized SendPacket getPacket(){
        return packet;
    }

    @Override
    public synchronized boolean handle(IoArgs args) throws IOException {
        if(packet == null && !isSending()){
            return true;// 已取消，不需要发送了
        }
        return super.handle(args);
    }

    @Override
    public final synchronized Frame nextFrame() {
        return packet == null ? null : buildNextFrame();
    }

    protected abstract Frame buildNextFrame();

    // true 当前帧没有发送任何数据
    public final synchronized boolean abort(){
        // 判断是否已经发送了部分数据
        boolean isSending = isSending();
        if(isSending){
            fillDirtyDataOnAbort();
        }
        packet = null;
        return !isSending;
    }

    // 填充假数据
    protected void fillDirtyDataOnAbort(){

    }
}
