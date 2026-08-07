package com.talkie.chat.global.config;

import com.talkie.chat.global.filter.StompChannelInterceptor;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketTransportRegistration;

import java.util.List;

@Configuration
@EnableWebSocketMessageBroker
@RequiredArgsConstructor
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    private static final Logger log = LoggerFactory.getLogger(WebSocketConfig.class);

    private final StompChannelInterceptor stompChannelInterceptor;

    @Value("#{'${app.cors.allowed-origins}'.split(',')}")
    private List<String> allowedOrigins;

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint("/ws-talkie")
                .setAllowedOrigins(allowedOrigins.toArray(new String[0]));
    }
    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        registry.enableSimpleBroker("/sub", "/queue");
        registry.setApplicationDestinationPrefixes("/pub");
    }

    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        registration.interceptors(stompChannelInterceptor);
        ThreadPoolTaskExecutor inboundExecutor = new ThreadPoolTaskExecutor();
        inboundExecutor.setThreadNamePrefix("stomp-inbound-");
        inboundExecutor.setRejectedExecutionHandler((runnable, executor) ->
                log.warn("inbound 채널 큐 초과로 작업 폐기: active={}, queueSize={}",
                        executor.getActiveCount(), executor.getQueue().size()));
        registration.taskExecutor(inboundExecutor)
                .corePoolSize(8)
                .maxPoolSize(16)
                .queueCapacity(500);
    }

    @Override
    public void configureClientOutboundChannel(ChannelRegistration registration) {
        ThreadPoolTaskExecutor outboundExecutor = new ThreadPoolTaskExecutor();
        outboundExecutor.setThreadNamePrefix("stomp-outbound-");
        outboundExecutor.setRejectedExecutionHandler((runnable, executor) ->
                log.warn("outbound 채널 큐 초과로 작업 폐기: active={}, queueSize={}",
                        executor.getActiveCount(), executor.getQueue().size()));
        registration.taskExecutor(outboundExecutor)
                .corePoolSize(8)
                .maxPoolSize(16)
                .queueCapacity(500);
    }

    @Override
    public void configureWebSocketTransport(WebSocketTransportRegistration registration) {
        registration.setSendTimeLimit(30_000)
                .setSendBufferSizeLimit(1024 * 1024);
    }
}
