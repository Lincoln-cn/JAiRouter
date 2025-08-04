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
public class NetUtils {

    private static final Logger log = LoggerFactory.getLogger(NetUtils.class);
    
    /**
     * 同步检测ip是否可连接
     *
     * @param host 域名或ip地址
     * @return NetConnect 返回结果
     */
    public static NetConnect testIpConnect(String host) {
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
            log.error("UnknownHostException occurred while pinging host: {}", host, e);
        } catch (Exception e) {
            msg = String.format("Error: %s", e.getMessage());
            log.error("Unexpected error occurred while pinging host: {}", host, e);
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
    public static NetConnect testSocketConnect(String host, int port) {
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
            log.error("Error occurred while testing socket connection to {}:{}", host, port, e);
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

        public boolean isConnect() {
            return connect;
        }

        public String getMsg() {
            return msg;
        }

        public LocalDateTime getReceiveTime() {
            return receiveTime;
        }

        public static class NetConnectBuilder {
            private boolean connect;
            private String msg;
            private LocalDateTime receiveTime;

            public NetConnectBuilder connect(boolean connect) {
                this.connect = connect;
                return this;
            }

            public NetConnectBuilder msg(String msg) {
                this.msg = msg;
                return this;
            }

            public NetConnectBuilder receiveTime(LocalDateTime receiveTime) {
                this.receiveTime = receiveTime;
                return this;
            }

            public NetConnect build() {
                NetConnect netConnect = new NetConnect();
                netConnect.connect = this.connect;
                netConnect.msg = this.msg;
                netConnect.receiveTime = this.receiveTime;
                return netConnect;
            }
        }
    }
}
