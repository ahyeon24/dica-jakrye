/**
 * 썸네일. 백엔드가 이미 640px로 줄여 둔 이미지라 Next 이미지 최적화를 거치지 않고 그대로 쓴다.
 * (운영은 R2 + Cloudflare 캐시)
 */
export function Thumb({ src, alt, className = "" }: { src: string | null; alt: string; className?: string }) {
  if (!src) {
    return <div className={`bg-line ${className}`} aria-hidden />;
  }
  // eslint-disable-next-line @next/next/no-img-element -- 이미 크기를 맞춘 이미지라 next/image 최적화를 쓰지 않음
  return <img src={src} alt={alt} loading="lazy" decoding="async" className={`bg-line object-cover ${className}`} />;
}
