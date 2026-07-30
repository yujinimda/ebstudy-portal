package com.ebstudy.portal.auth.ratelimit;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Function;

/**
 * ★ 상한이 있는 캐시 — 방어 장치가 메모리 고갈 수단이 되지 않게 한다(research.md 5).
 *
 * <p>만료만 있고 최대 크기가 없으면 공격자가 <b>매번 다른 아이디</b>로 실패를 쌓아 키를 무한히
 * 늘릴 수 있다. 상한을 넘으면 <b>LRU 로 퇴거</b>하며, 퇴거된 키는 카운트가 0으로 돌아간다 —
 * 상한을 넘는 규모의 공격에서는 지연 방어가 약해지지만 <b>서버가 죽는 것보다 낫다</b>(알고 받는 대가).
 */
class LruCache<K, V> {

    private final Map<K, V> map;

    LruCache(int maxEntries) {
        int capacity = Math.max(16, maxEntries);
        this.map = new LinkedHashMap<>(16, 0.75f, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<K, V> eldest) {
                return size() > capacity;
            }
        };
    }

    synchronized V computeIfAbsent(K key, Function<K, V> factory) {
        return map.computeIfAbsent(key, factory);
    }

    synchronized V get(K key) {
        return map.get(key);
    }

    synchronized void remove(K key) {
        map.remove(key);
    }

    synchronized int size() {
        return map.size();
    }

    synchronized void clear() {
        map.clear();
    }
}
