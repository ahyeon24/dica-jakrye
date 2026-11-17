/** "IXUS 30 · 익서스 30" 처럼 대표 이름과 한글 이름을 같이. 같거나 없으면 하나만 */
export function cameraTitle(name: string, nameKo: string | null): string {
  return nameKo && nameKo !== name ? `${name} · ${nameKo}` : name;
}

export function brandTitle(name: string, nameKo: string | null): string {
  return nameKo ? `${nameKo} ${name}` : name;
}
