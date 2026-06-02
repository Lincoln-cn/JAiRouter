package org.unreal.modelrouter.monitor.tracing.memory;

import java.util.AbstractMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Predicate;

/**
 * LRU缓存实现
 * 线程安全的最近最少使用缓存
 */
public class LRUCache<K, V> {
    private final Map<K, Node<K, V>> map = new ConcurrentHashMap<>();
    private volatile int capacity;
    private final Node<K, V> head = new Node<>(null, null);
    private final Node<K, V> tail = new Node<>(null, null);

    /**
     * 构造LRU缓存
     * @param capacity 缓存容量
     */
    public LRUCache(final int capacity) {
        this.capacity = capacity;
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
            existing.value = value;
            moveToHead(existing);
        } else {
            Node<K, V> newNode = new Node<>(key, value);
            map.put(key, newNode);
            addToHead(newNode);

            if (map.size() > capacity) {
                Node<K, V> tailNode = removeTail();
                map.remove(tailNode.key);
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
