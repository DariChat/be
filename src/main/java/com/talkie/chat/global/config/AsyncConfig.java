package com.talkie.chat.global.config;

import java.lang.reflect.Method;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.aop.interceptor.AsyncUncaughtExceptionHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.AsyncConfigurer;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

@Configuration
@EnableAsync
public class AsyncConfig implements AsyncConfigurer {

    public static final String READ_STATUS_EXECUTOR = "readStatusExecutor";

    private static final Logger log = LoggerFactory.getLogger(AsyncConfig.class);

    @Bean(READ_STATUS_EXECUTOR)
    public ThreadPoolTaskExecutor readStatusExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(4);
        executor.setQueueCapacity(200);
        executor.setThreadNamePrefix("read-status-");
        // 읽음 처리는 유실되어도 다음 메시지/재구독 시 다시 갱신되는 멱등한 부가 기능이므로,
        // 큐가 가득 차면 Redis 리스너 스레드를 막지 않도록 버리고 로그만 남긴다.
        executor.setRejectedExecutionHandler((runnable, threadPoolExecutor) ->
                log.warn("읽음 처리 큐 초과로 작업 폐기: active={}, queueSize={}",
                        threadPoolExecutor.getActiveCount(), threadPoolExecutor.getQueue().size()));
        executor.initialize();
        return executor;
    }

    @Override
    public AsyncUncaughtExceptionHandler getAsyncUncaughtExceptionHandler() {
        return new AsyncUncaughtExceptionHandler() {
            @Override
            public void handleUncaughtException(Throwable ex, Method method, Object... params) {
                log.error("비동기 처리 실패: method={}, params={}", method.getName(), params, ex);
            }
        };
    }
}