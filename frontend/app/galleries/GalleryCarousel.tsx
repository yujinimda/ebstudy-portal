"use client";

import { useState, type KeyboardEvent } from "react";
import styles from "./gallery.module.css";
import type { GalleryImageResponse } from "./types";

/**
 * 이미지 캐러셀 — 요구사항 5.2 <i>"좌우 화살표로 이전/다음, 하단 점(dot)으로 이동"</i>.
 *
 * ★ **접근성**
 *   - 무대(stage)가 `tabIndex=0` 이라 키보드 포커스를 받고 ← → Home End 로 넘긴다.
 *     화살표 버튼만 두면 마우스 없이도 되긴 하지만 탭을 두 번 눌러야 한 장이 넘어간다
 *   - 점은 `<div>` 가 아니라 **버튼**이다. 클릭 가능한 것은 버튼이어야 키보드·스크린리더가
 *     같은 방식으로 쓸 수 있다. 지금 장은 `aria-current` 로 알린다
 *   - "3 / 12" 를 `aria-live="polite"` 로 두어 화면을 못 보는 사용자도 이동을 안다
 *   - 장식이 아니라 **내용**이므로 `alt` 에 원본 파일명을 넣는다(서버가 준 값 그대로)
 *
 * ★ 순서를 여기서 정렬하지 않는다. 서버가 이미 순서대로 준다(`GalleryDetailResponse` 주석) —
 *   화면이 다시 정렬하면 목록 썸네일(첫 장)과 캐러셀 첫 장이 어긋날 수 있다.
 */
export default function GalleryCarousel({ images }: { images: GalleryImageResponse[] }) {
  const [index, setIndex] = useState(0);
  /** 이미지 집합이 바뀌면(수정 후 재조회 등) 범위를 벗어난 index 를 되돌린다. */
  const [syncedKey, setSyncedKey] = useState(() => images.map((i) => i.id).join(","));

  const key = images.map((i) => i.id).join(",");
  if (key !== syncedKey) {
    // effect 가 아니라 **렌더 중 조정**이다 — effect 로 하면 한 프레임 동안 잘못된 장이 보이고,
    // 프로젝트 규약("effect 본문 동기 setState 금지")에도 걸린다
    setSyncedKey(key);
    setIndex(0);
  }

  if (images.length === 0) return null;

  const total = images.length;
  const current = images[Math.min(index, total - 1)];

  // 끝에서 처음으로 돌아간다 — 캐러셀에서 마지막 다음이 막히면 "고장난 버튼"처럼 보인다
  const go = (next: number) => setIndex(((next % total) + total) % total);

  function handleKeyDown(event: KeyboardEvent<HTMLDivElement>) {
    if (event.key === "ArrowLeft") {
      event.preventDefault();
      go(index - 1);
    } else if (event.key === "ArrowRight") {
      event.preventDefault();
      go(index + 1);
    } else if (event.key === "Home") {
      event.preventDefault();
      setIndex(0);
    } else if (event.key === "End") {
      event.preventDefault();
      setIndex(total - 1);
    }
  }

  return (
    <div
      className={styles.carousel}
      role="group"
      aria-roledescription="캐러셀"
      aria-label={`갤러리 이미지 ${total}장`}
    >
      <div className={styles.stage} tabIndex={0} onKeyDown={handleKeyDown}>
        {/* eslint-disable-next-line @next/next/no-img-element -- 서버 API 가 내려주는 동적
            바이너리 경로다(`/api/galleries/{id}/images/{imageId}`). next/image 는 remotePatterns
            설정이 필요하고, 서버가 이미 immutable 캐시 지시를 붙여 보낸다 */}
        <img src={current.url} alt={current.originalName} />

        {total > 1 && (
          <>
            <button
              type="button"
              className={`${styles.navButton} ${styles.navPrev}`}
              onClick={() => go(index - 1)}
              aria-label="이전 이미지"
            >
              ‹
            </button>
            <button
              type="button"
              className={`${styles.navButton} ${styles.navNext}`}
              onClick={() => go(index + 1)}
              aria-label="다음 이미지"
            >
              ›
            </button>
          </>
        )}

        <span className={styles.counter} aria-live="polite">
          {Math.min(index, total - 1) + 1} / {total}
        </span>
      </div>

      {total > 1 && (
        <div className={styles.dots}>
          {images.map((image, i) => (
            <button
              key={image.id}
              type="button"
              className={`${styles.dot} ${i === index ? styles.dotCurrent : ""}`}
              onClick={() => setIndex(i)}
              aria-label={`${i + 1}번째 이미지`}
              aria-current={i === index ? "true" : undefined}
            />
          ))}
        </div>
      )}

      {total > 1 && (
        <span className={styles.carouselHint}>
          이미지에 포커스를 두고 ← → 키로도 넘길 수 있습니다.
        </span>
      )}
    </div>
  );
}
