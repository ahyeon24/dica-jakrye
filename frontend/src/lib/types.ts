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

export type PhotoDetail = {
  id: number;
  source: "UPLOAD" | "COMMONS";
  displayUrl: string;
  thumbnailUrl: string;
  width: number | null;
  height: number | null;
  isOriginalSize: boolean | null;
  camera: { slug: string; name: string; nameKo: string | null; brandName: string } | null;
  exif: {
    takenAt: string | null;
    flashFired: boolean | null;
    fNumber: number | null;
    exposureTime: string | null;
    iso: number | null;
    focalLength: number | null;
  };
  uploader: { nickname: string; profileImageUrl: string | null } | null;
  commons: {
    author: string;
    licenseName: string;
    licenseUrl: string | null;
    pageUrl: string;
    modified: boolean;
  } | null;
  createdAt: string;
  isMine: boolean;
};

export type Adjacent = {
  prev: PhotoDetail | null; // 격자에서 바로 앞 (더 최신)
  next: PhotoDetail | null; // 바로 뒤 (더 오래됨)
};

export type Me = {
  id: number;
  nickname: string;
  profileImageUrl: string | null;
  role: "USER" | "ADMIN";
  provider: "GOOGLE" | "KAKAO";
  photoCount: number;
};

export type SignupInfo = {
  provider: "GOOGLE" | "KAKAO";
  terms: { type: "TERMS_OF_SERVICE" | "PRIVACY_COLLECTION"; version: string; url: string }[];
  redirect: string;
};
