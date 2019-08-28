package io.github.demo.server;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;

import io.github.demo.constants.TCPConstants;
import io.github.demo.lib.core.IoContext;
import io.github.demo.lib.impl.IoSelectorProvider;

/*

1.消息粘包

TCP本质不会发生数据层面的粘包，底层是分包机制，一个一个发送的。会自动修复丢包和粘包
TCP是可以确保数据完整性的
这里不是TCP层的，是业务层的粘包
UDP不保证消息完整性，所以可能发生丢包

理想的数据接收情况
-----------------------------
    M3      M2      M1
-----------------------------

实际的数据接收情况
-----------------------------
    M3            M2M1
-----------------------------
M2和M1同时到达

2.消息不完整

TCP也不会发生数据丢失不全的情况

这里仍然是针对逻辑层面，物理层数据已经完整送达另一端，
另一端可能缓冲区不够，或者数据处理上不够完整，导致数据只能读取一部分

M2的数据提前读取部分到业务部分
-----------------------------
    M2(2)           M2(1)M1
-----------------------------

M1的数据仅仅读取部分到业务部分
-----------------------------
    M2M1(2)         M1(1)
-----------------------------

3.具体搜索 “复现”

 */
public class Server {
    public static void main(String[] args) throws IOException {
        IoContext.setup()
                .ioProvider(new IoSelectorProvider())
                .start();

        TCPServer tcpServer = new TCPServer(TCPConstants.PORT_SERVER);
        boolean isSucceed = tcpServer.start();
        if (!isSucceed) {
            System.out.println("Start TCP server failed!");
            return;
        }

        UDPProvider.start(TCPConstants.PORT_SERVER);

        BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(System.in));
        String str;
        do{
            str = bufferedReader.readLine();
            tcpServer.broadcast(str);
        }while(!"00bye00".equalsIgnoreCase(str));

        UDPProvider.stop();
        tcpServer.stop();

        IoContext.close();
    }
}
