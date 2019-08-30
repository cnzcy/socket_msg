package io.github.demo.client;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;

import io.github.demo.lib.core.IoContext;
import io.github.demo.lib.impl.IoSelectorProvider;

public class Client {
    public static void main(String[] args) throws IOException {
        IoContext.setup()
                .ioProvider(new IoSelectorProvider())
                .start();

        ServerInfo info = UDPSearcher.searchServer(10000);
        System.out.println("Server:" + info);

        if (info != null) {
            TCPClient tcpClient = null;
            try {
                tcpClient = TCPClient.startWith(info);
                if (tcpClient == null) {
                    return;
                }
                write(tcpClient);
            } catch (IOException e) {
                e.printStackTrace();
            } finally {
                if (tcpClient != null) {
                    tcpClient.exit();
                }
            }
        }

        IoContext.close();
    }

    private static void write(TCPClient tcpClient) throws IOException {
        // 构建键盘输入流
        InputStream in = System.in;
        BufferedReader input = new BufferedReader(new InputStreamReader(in));
        do {
            // 键盘读取一行
            String str = input.readLine();
            // 发送到服务器
            tcpClient.send(str);

            // 复现2：消息粘包
            // 4条消息拼装成1条大的消息，就是粘包的效果了。我们把消息的间隔也就是分隔符打印成字符。
             tcpClient.send(str);
             tcpClient.send(str);
             tcpClient.send(str);

            if ("00bye00".equalsIgnoreCase(str)) {
                break;
            }
        } while (true);
    }
}
