// 백엔드 응답 타입. docs/API.md 와 맞춘다.

export type BrandItem = {
  slug: string;
  name: string;
  nameKo: string | null;
  cameraCount: number;
};

export type CameraItem = {
  slug: string;
  name: string;
  nameKo: string | null;
  releaseYear: number | null;
  photoCount: number;
  coverThumbnailUrl: string | null;
};

export type BrandDetail = {
  slug: string;
  name: string;
  nameKo: string | null;
  cameras: CameraItem[];
};

export type SearchItem = {
  slug: string;
  name: string;
  nameKo: string | null;
  brandName: string;
  matchedAlias: string | null;
  photoCount: number;
  coverThumbnailUrl: string | null;
};

export type CameraDetail = {
  slug: string;
  name: string;
  nameKo: string | null;
  brand: { slug: string; name: string; nameKo: string | null };
  releaseYear: number | null;
  sensorType: string | null;
  aliases: string[];
  photoCount: number;
  flashPhotoCount: number;
  originalSizePhotoCount: number;
  nativeResolution: string | null;
};

export type PhotoSummary = {
  id: number;
  source: "UPLOAD" | "COMMONS";
  thumbnailUrl: string;
  width: number | null;
  height: number | null;
  flashFired: boolean | null;
  isOriginalSize: boolean | null;
  camera: { slug: string; name: string; nameKo: string | null };
  credit: string | null;
};

export type Page<T> = {
  items: T[];
  nextCursor: string | null;
};
