package org.unreal.modelrouter.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.UnknownHostException;
import java.time.LocalDateTime;

/**
 * 特别注意: ping不通, 并不代表telnet或者socket就不能正常连接
 * 原因: 协议不同
 * - ping是基于ICMP协议, ping不通可能原因是防火墙或其他主机设置禁用了ICMP协议
 * - telnet/socket都是基于TCP/IP协议簇
 */
public final class NetUtils {

    private static final Logger LOGGER = LoggerFactory.getLogger(NetUtils.class);
    
    /**
     * 同步检测ip是否可连接
     *
     * @param host 域名或ip地址
     * @return NetConnect 返回结果
     */
    public static NetConnect testIpConnect(final String host) {
        boolean reachable = false;
        String msg;

        try {
            InetAddress address = InetAddress.getByName(host);
            // 5000ms timeout
            // 这个方法会尝试发送一个ICMP（Internet Control Message Protocol）回显请求包, ping命令也是基于ICMP协议
            reachable = address.isReachable(5000);
            if (reachable) {
                msg = String.format("Ping %s  : Success", host);
            } else {
                msg = String.format("Ping %s  : Failed", host);
            }
        } catch (UnknownHostException e) {
            msg = String.format("Invalid URL or Host not found: %s", host);
            LOGGER.error("UnknownHostException occurred while pinging host: {}", host, e);
        } catch (Exception e) {
            msg = String.format("Error: %s", e.getMessage());
            LOGGER.error("Unexpected error occurred while pinging host: {}", host, e);
        }
        return NetConnect.builder().connect(reachable).msg(msg).receiveTime(LocalDateTime.now()).build();
    }

    /**
     * 同步检测socket是否可连接
     *
     * @param host 域名或ip地址
     * @param port 端口号
     * @return NetConnect 返回结果
     */
    public static NetConnect testSocketConnect(final String host, final int port) {
        boolean reachable = false;
        String msg;

        try {
            //这里创建的是tcp连接的socket, 这里能连接的话, 则http接口应该也可以正常连接
            Socket socket = new Socket();
            // 5000ms timeout
            socket.connect(new InetSocketAddress(host, port), 5000);
            reachable = true;
            msg = String.format("Port %s is open on %s", port, host);
            // 关闭socket连接
            socket.close();
        } catch (Exception e) {
            msg = String.format("Port %s is closed on %s", port, host);
            LOGGER.error("Error occurred while testing socket connection to {}:{}", host, port, e);
        }
        return NetConnect.builder().connect(reachable).msg(msg).receiveTime(LocalDateTime.now()).build();
    }

    public static class NetConnect {
        private boolean connect;
        private String msg;
        private LocalDateTime receiveTime;

        public static NetConnectBuilder builder() {
            return new NetConnectBuilder();
        }

        /**
         * 获取连接状态
         * 
         * @return 连接状态
         */
        public boolean isConnect() {
            return connect;
        }

        /**
         * 获取消息
         * 
         * @return 消息内容
         */
        public String getMsg() {
            return msg;
        }

        /**
         * 获取接收时间
         * 
         * @return 接收时间
         */
        public LocalDateTime getReceiveTime() {
            return receiveTime;
        }

        public static class NetConnectBuilder {
            private boolean connect;
            private String msg;
            private LocalDateTime receiveTime;

            /**
             * 设置连接状态
             * 
             * @param isConnected 连接状态
             * @return 构建器实例
             */
            public NetConnectBuilder connect(final boolean isConnected) {
                this.connect = isConnected;
                return this;
            }

            /**
             * 设置消息
             * 
             * @param message 消息内容
             * @return 构建器实例
             */
            public NetConnectBuilder msg(final String message) {
                this.msg = message;
                return this;
            }

            /**
             * 设置接收时间
             * 
             * @param time 接收时间
             * @return 构建器实例
             */
            public NetConnectBuilder receiveTime(final LocalDateTime time) {
                this.receiveTime = time;
                return this;
            }

            /**
             * 构建NetConnect实例
             * 
             * @return NetConnect实例
             */
            public NetConnect build() {
                NetConnect netConnect = new NetConnect();
                netConnect.connect = this.connect;
                netConnect.msg = this.msg;
                netConnect.receiveTime = this.receiveTime;
                return netConnect;
            }
        }
    }
    
    // 私有构造器防止实例化
    private NetUtils() {
    }
}
