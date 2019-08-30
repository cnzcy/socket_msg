package io.github.demo.server.handler;

import java.io.IOException;
import java.nio.channels.SocketChannel;

import io.github.demo.lib.CloseUtils;
import io.github.demo.lib.core.Connector;

public class ClientHandler extends Connector{
    private final ClientHandlerCallback clientHandlerCallback;
    private final String clientInfo;

    public ClientHandler(SocketChannel socketChannel, final ClientHandlerCallback clientHandlerCallback) throws IOException {
        this.clientHandlerCallback = clientHandlerCallback;
        this.clientInfo = socketChannel.getRemoteAddress().toString();

        System.out.println("新客户端连接：" + clientInfo);

        setup(socketChannel);
    }

    public void exit() {
        CloseUtils.close(this);
        System.out.println("客户端已退出：" + clientInfo);
    }

    private void exitBySelf() {
        exit();
        clientHandlerCallback.onSelfClosed(this);
    }

    @Override
    public void onChannelClosed(SocketChannel channel) {
        super.onChannelClosed(channel);
        exitBySelf();
    }

    @Override
    protected void onReceiveNewMessage(String str) {
        super.onReceiveNewMessage(str);
        clientHandlerCallback.onNewMessageArrived(this, str);
    }

    public interface ClientHandlerCallback {
        // 自身关闭的通知
        void onSelfClosed(ClientHandler handler);

        // 收到消息时通知
        void onNewMessageArrived(ClientHandler handler, String msg);
    }

}
