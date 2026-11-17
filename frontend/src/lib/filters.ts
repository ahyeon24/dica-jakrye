/** 기종 작례 필터 (flash, originalOnly). 기종 페이지와 작례 상세가 같이 쓴다. 알 수 없는 값은 무시 */
export type PhotoFilters = { flash?: "true" | "false"; originalOnly: boolean };

export function readFilters(sp: Record<string, string | string[] | undefined>): PhotoFilters {
  const flash = sp.flash === "true" || sp.flash === "false" ? sp.flash : undefined;
  const originalOnly = sp.originalOnly === "true";
  return { flash, originalOnly };
}

export function toQuery(f: PhotoFilters): string {
  const q = new URLSearchParams();
  if (f.flash) q.set("flash", f.flash);
  if (f.originalOnly) q.set("originalOnly", "true");
  return q.toString();
}
