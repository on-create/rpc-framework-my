package org.example.simple.transport.netty.client;

import io.netty.channel.Channel;
import io.netty.channel.ChannelFutureListener;
import io.netty.util.AttributeKey;
import lombok.extern.slf4j.Slf4j;
import org.example.common.dto.RpcRequest;
import org.example.common.dto.RpcResponse;
import org.example.common.utils.checker.RpcMessageChecker;
import org.example.simple.registry.ServiceDiscovery;
import org.example.simple.registry.ZkServiceDiscovery;
import org.example.simple.transport.ClientTransport;

import java.net.InetSocketAddress;
import java.util.concurrent.atomic.AtomicReference;

@Slf4j
public class NettyClientTransport implements ClientTransport {

    private final ServiceDiscovery serviceDiscovery;

    public NettyClientTransport() {
        this.serviceDiscovery = new ZkServiceDiscovery();
    }

    @Override
    public Object sendRpcRequest(RpcRequest rpcRequest) {
        AtomicReference<Object> result = new AtomicReference<>(null);
        try {
            InetSocketAddress inetSocketAddress = serviceDiscovery.lookupService(rpcRequest.getInterfaceName());
            Channel channel = ChannelProvider.get(inetSocketAddress);
            // isActive(): 通过查找底层套接字来查看它是否已经连接来工作
            if (channel.isActive()) {
                channel.writeAndFlush(rpcRequest)
                        .addListener((ChannelFutureListener) future -> {
                            if (future.isSuccess()) {
                                log.info("client send message: {}", rpcRequest);
                            } else {
                                future.channel().close();
                                log.error("Send failed:", future.cause());
                            }
                        });

                channel.closeFuture().sync();
                AttributeKey<RpcResponse<?>> key = AttributeKey.valueOf("rpcResponse" + rpcRequest.getRequestId());
                RpcResponse<?> rpcResponse = channel.attr(key).get();
                log.info("client get rpcResponse from channel:{}", rpcResponse);
                // 校验 RpcResponse 和 RpcRequest
                RpcMessageChecker.check(rpcRequest, rpcResponse);
                result.set(rpcResponse.getData());
            } else {
                NettyClient.close();
                System.exit(0);
            }
        } catch (InterruptedException e) {
            log.error("occur exception when send rpc message from client:", e);
        }

        return result.get();
    }
}
