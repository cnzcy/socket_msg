package io.github.demo.server;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.channels.SelectableChannel;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import io.github.demo.lib.CloseUtils;
import io.github.demo.server.handler.ClientHandler;

public class TCPServer implements ClientHandler.ClientHandlerCallback {
    private final int port;
    private ClientListener mListener;
    // 只能保证添加删除时线程安全，不能保证遍历时
    private List<ClientHandler> clientHandlerList = Collections.synchronizedList(new ArrayList<ClientHandler>());
    private final ExecutorService forwardingThreadPoolExecutor;
    private Selector selector;
    private ServerSocketChannel server;

    public TCPServer(int port) {
        this.port = port;
        this.forwardingThreadPoolExecutor = Executors.newSingleThreadExecutor();
    }

    public boolean start() {
        try {
            selector = Selector.open();
            server = ServerSocketChannel.open();
            server.configureBlocking(false);// 设置非阻塞
            server.socket().bind(new InetSocketAddress(port));// 绑定本地端口
            // 注册事件
            server.register(selector, SelectionKey.OP_ACCEPT);

            System.out.println("服务器信息：" + server.getLocalAddress().toString());

            // 启动监听
            ClientListener listener = new ClientListener();
            mListener = listener;
            listener.start();
        } catch (IOException e) {
            e.printStackTrace();
            return false;
        }
        return true;
    }

    public void stop() {
        if (mListener != null) {
            mListener.exit();
        }

        CloseUtils.close(server);
        CloseUtils.close(selector);

        synchronized (TCPServer.this) {
            for (ClientHandler clientHandler : clientHandlerList) {
                clientHandler.exit();
            }
            clientHandlerList.clear();
        }
        forwardingThreadPoolExecutor.shutdownNow();
    }

    public void broadcast(String str) {
        synchronized (TCPServer.this) {
            for (ClientHandler clientHandler : clientHandlerList) {
                clientHandler.send(str);
            }
        }
    }

    @Override
    public synchronized void onSelfClosed(ClientHandler handler) {
        clientHandlerList.remove(handler);
    }

    // 接收线程调用，不能阻塞
    @Override
    public void onNewMessageArrived(final ClientHandler handler, final String msg) {
        // 打印到屏幕
        System.out.println("收到：" + handler.getClientInfo() + " : " + msg);
        forwardingThreadPoolExecutor.execute(new Runnable() {
            @Override
            public void run() {
                synchronized (TCPServer.this) {
                    for (ClientHandler clientHandler : clientHandlerList) {
                        if (clientHandler.equals(handler)) {
                            continue;// 跳过自己
                        }
                        clientHandler.send(msg);// 这个发送是在一个客户端线程池中进行的不会阻塞
                    }
                }
            }
        });
    }

    private class ClientListener extends Thread {
        private boolean done = false;

        @Override
        public void run() {
            super.run();

            Selector selector = TCPServer.this.selector;

            System.out.println("服务器准备就绪～");
            // 等待客户端连接
            do {
                // 得到客户端
                try {
                    // 如果选择器是被wakeUp唤醒的返回就是0
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
                        iterator.remove();// 已经使用了，先移除
                        if (key.isAcceptable()) {// 客户端到达的状态
                            // 拿到注册时的channel
                            ServerSocketChannel serverSocketChannel = (ServerSocketChannel) key.channel();
                            // 非阻塞状态获取客户端连接
                            SocketChannel socketChannel = serverSocketChannel.accept();

                            // 客户端构建异步线程
                            try {
                                ClientHandler clientHandler = new ClientHandler(socketChannel, TCPServer.this);
                                // 读取数据并打印
                                clientHandler.readToPrint();
                                synchronized (TCPServer.this) {
                                    clientHandlerList.add(clientHandler);
                                }
                            } catch (IOException e) {
                                e.printStackTrace();
                                System.out.println("客户端连接异常：" + e.getMessage());
                            }
                        }
                    }


                } catch (IOException e) {
                    e.printStackTrace();
                }
            } while (!done);

            System.out.println("服务器已关闭！");
        }

        void exit() {
            done = true;
            // 唤醒阻塞状态
            selector.wakeup();
        }
    }

}
