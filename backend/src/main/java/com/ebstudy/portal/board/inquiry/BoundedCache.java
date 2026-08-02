package com.ebstudy.portal.board.inquiry;

import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.BiPredicate;
import java.util.function.Function;

/**
 * ★ 상한이 있는 LRU 캐시 — <b>방어 장치가 메모리 고갈 수단이 되지 않게 한다.</b>
 *
 * <p>001 {@code auth.ratelimit.LruCache} 와 같은 물건이고 이유도 같다: 만료만 있고 최대 크기가
 * 없으면 공격자가 <b>매번 다른 글 번호</b>로 실패를 쌓아 키를 무한히 늘릴 수 있다.
 * 상한을 넘으면 LRU 로 퇴거하고, 퇴거된 키는 카운트가 0으로 돌아간다 —
 * 상한을 넘는 규모의 공격에서는 방어가 약해지지만 <b>서버가 죽는 것보다 낫다</b>(알고 받는 대가).
 *
 * <p>001 것을 그대로 쓰지 않은 이유는 하나뿐이다: 그 클래스가 <b>패키지 전용</b>이고
 * {@code auth/} 는 이 단계에서 수정이 금지되어 있다. 공개로 올리는 것도 남의 패키지 수정이다.
 * 공통으로 올릴 자리가 생기면 둘을 하나로 합쳐야 한다(보고서 후속 항목).
 */
final class BoundedCache<K, V> {

    private final Map<K, V> map;

    BoundedCache(int maxEntries) {
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

    synchronized void put(K key, V value) {
        map.put(key, value);
    }

    synchronized void remove(K key) {
        map.remove(key);
    }

    /** 만료분 청소 · 특정 글의 통과 일괄 회수에 쓴다. */
    synchronized void removeIf(BiPredicate<K, V> predicate) {
        Iterator<Map.Entry<K, V>> iterator = map.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<K, V> entry = iterator.next();
            if (predicate.test(entry.getKey(), entry.getValue())) {
                iterator.remove();
            }
        }
    }

    synchronized int size() {
        return map.size();
    }

    synchronized void clear() {
        map.clear();
    }
}
