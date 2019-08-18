package io.github.demo.server.handler;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.PrintStream;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.SocketChannel;
import java.util.Iterator;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import io.github.demo.lib.CloseUtils;

public class ClientHandler {
    private final SocketChannel socketChannel;
    private final ClientReadHandler readHandler;
    private final ClientWriteHandler writeHandler;
    private final ClientHandlerCallback clientHandlerCallback;
    private final String clientInfo;

    public ClientHandler(SocketChannel socketChannel, ClientHandlerCallback clientHandlerCallback) throws IOException {
        this.socketChannel = socketChannel;

        // 设置非阻塞
        socketChannel.configureBlocking(false);
        Selector readSelector = Selector.open();
        socketChannel.register(readSelector, SelectionKey.OP_READ);
        readHandler = new ClientReadHandler(readSelector);

        Selector writeSelector = Selector.open();
        socketChannel.register(writeSelector, SelectionKey.OP_WRITE);
        writeHandler = new ClientWriteHandler(writeSelector);

        this.clientHandlerCallback = clientHandlerCallback;
        this.clientInfo = socketChannel.getRemoteAddress().toString();
        System.out.println("新客户端连接：" + clientInfo);
    }

    public String getClientInfo() {
        return clientInfo;
    }

    public void exit() {
        readHandler.exit();
        writeHandler.exit();
        CloseUtils.close(socketChannel);
        System.out.println("客户端已退出：" + clientInfo);
    }

    public void send(String str) {
        writeHandler.send(str);
    }

    public void readToPrint() {
        readHandler.start();
    }

    private void exitBySelf() {
        exit();
        clientHandlerCallback.onSelfClosed(this);
    }

    public interface ClientHandlerCallback {
        // 自身关闭的通知
        void onSelfClosed(ClientHandler handler);

        // 收到消息时通知
        void onNewMessageArrived(ClientHandler handler, String msg);
    }

    class ClientReadHandler extends Thread {

        private boolean done = false;
        private final Selector selector;
        private final ByteBuffer byteBuffer;

        ClientReadHandler(Selector selector) {
            this.selector = selector;
            this.byteBuffer = ByteBuffer.allocate(256);
        }

        @Override
        public void run() {


            try {
                do {
                    // 客户端拿到一条数据
                    if (selector.select() == 0) {
                        if (done) {
                            break;
                        }
                        continue;
                    }

                    Iterator<SelectionKey> iterator = selector.selectedKeys().iterator();
                    while (iterator.hasNext()) {
                        if (done) {
                            break;
                        }
                        SelectionKey key = iterator.next();
                        iterator.remove();

                        if (key.isReadable()) {
                            SocketChannel client = (SocketChannel) key.channel();// 获取注册的channel
                            byteBuffer.clear();
                            // 读取
                            int read = client.read(byteBuffer);
                            if (read > 0) {
                                // 丢弃换行符
                                String str = new String(byteBuffer.array(), 0, byteBuffer.position() - 1);
                                // 通知TCPServer收到消息
                                clientHandlerCallback.onNewMessageArrived(ClientHandler.this, str);
                            } else {
                                // 读取超时，IO异常
                                System.out.println("客户端无法读取数据");
                                // 退出当前客户端
                                ClientHandler.this.exitBySelf();
                                break;
                            }
                        }
                    }
                } while (!done);
            } catch (Exception e) {
                if (!done) {
                    System.out.println("连接异常断开");
                    ClientHandler.this.exitBySelf();
                }
            } finally {
                // 连接关闭
                CloseUtils.close(selector);
            }
        }

        void exit() {
            done = true;
            selector.wakeup();
            CloseUtils.close(selector);
        }
    }

    class ClientWriteHandler {

        private boolean done = false;
        private final Selector selector;
        private final ByteBuffer byteBuffer;

        private final ExecutorService executorService;

        ClientWriteHandler(Selector selector) {
            this.selector = selector;
            byteBuffer = ByteBuffer.allocate(256);
            executorService = Executors.newSingleThreadExecutor();
        }

        void exit() {
            done = true;
            CloseUtils.close(selector);
            executorService.shutdownNow();
        }

        void send(String str) {
            if (done) {
                return;
            }
            executorService.execute(new WriteRunnable(str));
        }

        class WriteRunnable implements Runnable {

            private final String msg;

            WriteRunnable(String msg) {
                this.msg = msg + '\n';
            }

            @Override
            public void run() {
                if (done) {
                    return;
                }
                byteBuffer.clear();
                byteBuffer.put(msg.getBytes());
                byteBuffer.flip();// 反转

                while (!done && byteBuffer.hasRemaining()) {// 如果还有数据，并且没有结束
                    try {
                        int len = socketChannel.write(byteBuffer);
                        // 等于0合法
                        if (len < 0) {
                            System.out.println("客户端已无法发送数据!");
                            ClientHandler.this.exitBySelf();
                            break;
                        }
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
            }
        }
    }
}
