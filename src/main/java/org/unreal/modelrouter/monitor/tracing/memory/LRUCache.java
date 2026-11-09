package org.unreal.modelrouter.monitor.tracing.memory;

import java.util.AbstractMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.function.Predicate;

/**
 * LRU缓存实现
 * 线程安全的最近最少使用缓存
 *
 * <p><b>淘汰回调</b>（issue #1055）：构造时可传入 {@code onEvict}，任何一条「值离开缓存」的路径
 * 都会回调它 —— 容量淘汰、显式 {@link #removeEldest()}、条件移除 {@link #removeIf}，
 * 以及同 key 覆盖写入时被替换掉的旧值。这样调用方才能维护「在缓存中的数据总量」这类记账，
 * 否则只能累加、无法扣减。</p>
 *
 * <p>回调在缓存自身的锁内执行，必须足够轻量、且<b>不得再调用本实例的方法</b>（否则会重入死锁）。
 * 典型用法只是做一次计数加减。</p>
 */
public final class LRUCache<K, V> {
    private final Map<K, Node<K, V>> map = new ConcurrentHashMap<>();
    private volatile int capacity;
    private final Node<K, V> head = new Node<>(null, null);
    private final Node<K, V> tail = new Node<>(null, null);
    private final Consumer<V> onEvict;

    /**
     * 构造LRU缓存
     * @param capacity 缓存容量
     */
    public LRUCache(final int capacity) {
        this(capacity, null);
    }

    /**
     * 构造带淘汰回调的LRU缓存
     * @param capacity 缓存容量
     * @param onEvict  值离开缓存时的回调（可为 {@code null}）
     */
    public LRUCache(final int capacity, final Consumer<V> onEvict) {
        this.capacity = capacity;
        this.onEvict = onEvict;
        head.next = tail;
        tail.prev = head;
    }

    /**
     * 获取缓存值
     */
    public synchronized V get(final K key) {
        Node<K, V> node = map.get(key);
        if (node != null) {
            moveToHead(node);
            return node.value;
        }
        return null;
    }

    /**
     * 存入缓存
     */
    public synchronized void put(final K key, final V value) {
        Node<K, V> existing = map.get(key);
        if (existing != null) {
            final V previous = existing.value;
            existing.value = value;
            moveToHead(existing);
            // 同一 key 覆盖：旧值已经不在缓存里了，记账要扣掉它，
            // 否则「同一 traceId 反复写入」会把总量算成累加值（issue #1055）
            if (this.onEvict != null && previous != value) {
                this.onEvict.accept(previous);
            }
        } else {
            Node<K, V> newNode = new Node<>(key, value);
            map.put(key, newNode);
            addToHead(newNode);

            if (map.size() > capacity) {
                Node<K, V> tailNode = removeTail();
                map.remove(tailNode.key);
                notifyEvicted(tailNode.value);
            }
        }
    }

    /**
     * 移除最老的条目
     */
    public synchronized void removeEldest() {
        if (!map.isEmpty()) {
            Node<K, V> tailNode = removeTail();
            map.remove(tailNode.key);
            notifyEvicted(tailNode.value);
        }
    }

    /**
     * 条件移除
     */
    public synchronized void removeIf(final Predicate<Map.Entry<K, V>> predicate) {
        map.entrySet().removeIf(entry -> {
            Map.Entry<K, V> valueEntry = new AbstractMap.SimpleEntry<>(entry.getKey(), entry.getValue().value);
            if (predicate.test(valueEntry)) {
                removeNode(entry.getValue());
                notifyEvicted(entry.getValue().value);
                return true;
            }
            return false;
        });
    }

    public int size() {
        return map.size();
    }

    public boolean isEmpty() {
        return map.isEmpty();
    }

    public int getCapacity() {
        return capacity;
    }

    public void setCapacity(final int capacity) {
        this.capacity = capacity;
        while (map.size() > capacity) {
            removeEldest();
        }
    }

    private void notifyEvicted(final V value) {
        if (this.onEvict != null && value != null) {
            this.onEvict.accept(value);
        }
    }

    private void addToHead(final Node<K, V> node) {
        node.prev = head;
        node.next = head.next;
        head.next.prev = node;
        head.next = node;
    }

    private void removeNode(final Node<K, V> node) {
        node.prev.next = node.next;
        node.next.prev = node.prev;
    }

    private void moveToHead(final Node<K, V> node) {
        removeNode(node);
        addToHead(node);
    }

    private Node<K, V> removeTail() {
        Node<K, V> lastNode = tail.prev;
        removeNode(lastNode);
        return lastNode;
    }

    private static class Node<K, V> {
        K key;
        V value;
        Node<K, V> prev;
        Node<K, V> next;

        Node(final K key, final V value) {
            this.key = key;
            this.value = value;
        }
    }
}
