package org.unreal.modelrouter.monitor.stats;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/**
 * 轻量级事件总线
 *
 * 用于组件间的异步事件通信，避免直接耦合。
 * 统计更新事件通过事件总线分发到各个处理器。
 *
 * @author JAiRouter Team
 * @since 2.7.4
 */
public class StatsEventBus {

    private static final Logger logger = LoggerFactory.getLogger(StatsEventBus.class);

    /**
     * 事件处理器映射
     */
    private final Map<Class<?>, List<Consumer<?>>> handlers = new ConcurrentHashMap<>();

    /**
     * 异步执行器
     */
    private final ExecutorService executor;

    /**
     * 是否异步处理
     */
    private final boolean async;

    /**
     * 创建事件总线
     *
     * @param async 是否异步处理事件
     */
    public StatsEventBus(final boolean async) {
        this.async = async;
        this.executor = async ? Executors.newFixedThreadPool(2, r -> {
            Thread t = new Thread(r, "stats-event-bus");
            t.setDaemon(true);
            return t;
        }) : null;
    }

    /**
     * 创建同步事件总线
     */
    public StatsEventBus() {
        this(false);
    }

    /**
     * 注册事件处理器
     *
     * @param eventType 事件类型
     * @param handler 处理器
     * @param <T> 事件类型
     */
    public <T> void subscribe(final Class<T> eventType, final Consumer<T> handler) {
        handlers.computeIfAbsent(eventType, k -> new ArrayList<>()).add(handler);
        logger.debug("订阅事件: type={}, handler={}", eventType.getSimpleName(), handler.getClass().getSimpleName());
    }

    /**
     * 取消订阅
     *
     * @param eventType 事件类型
     * @param handler 处理器
     * @param <T> 事件类型
     */
    public <T> void unsubscribe(final Class<T> eventType, final Consumer<T> handler) {
        List<Consumer<?>> handlersList = handlers.get(eventType);
        if (handlersList != null) {
            handlersList.remove(handler);
        }
    }

    /**
     * 发布事件
     *
     * @param event 事件对象
     * @param <T> 事件类型
     */
    @SuppressWarnings("unchecked")
    public <T> void publish(final T event) {
        if (event == null) {
            return;
        }

        List<Consumer<?>> handlersList = handlers.get(event.getClass());
        if (handlersList == null || handlersList.isEmpty()) {
            return;
        }

        for (Consumer<?> handler : handlersList) {
            Consumer<T> typedHandler = (Consumer<T>) handler;

            if (async && executor != null) {
                executor.submit(() -> {
                    try {
                        typedHandler.accept(event);
                    } catch (Exception e) {
                        logger.error("处理事件失败: type={}, error={}",
                                event.getClass().getSimpleName(), e.getMessage(), e);
                    }
                });
            } else {
                try {
                    typedHandler.accept(event);
                } catch (Exception e) {
                    logger.error("处理事件失败: type={}, error={}",
                            event.getClass().getSimpleName(), e.getMessage(), e);
                }
            }
        }
    }

    /**
     * 关闭事件总线
     */
    public void shutdown() {
        if (executor != null) {
            executor.shutdown();
            try {
                if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
                    executor.shutdownNow();
                }
            } catch (InterruptedException e) {
                executor.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }
        handlers.clear();
        logger.info("事件总线已关闭");
    }
}
