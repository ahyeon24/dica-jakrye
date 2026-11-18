import { notFound } from "next/navigation";
import { Suspense } from "react";
import { PhotoGrid } from "@/components/PhotoGrid";
import { MyHiddenPhotos } from "@/components/MyHiddenPhotos";
import { ProfileOwnerActions } from "@/components/ProfileOwnerActions";
import { apiGet } from "@/lib/api";
import type { Page, PhotoSummary, Profile } from "@/lib/types";

type Props = PageProps<"/users/[nickname]">;

export default function ProfilePage({ params }: Props) {
  return (
    <Suspense fallback={<p className="text-sm text-muted">불러오는 중…</p>}>
      <ProfileContent params={params} />
    </Suspense>
  );
}

async function ProfileContent({ params }: Pick<Props, "params">) {
  const { nickname: raw } = await params;
  const nickname = decodeURIComponent(raw);
  const path = `/api/users/${encodeURIComponent(nickname)}`;
  const [profile, firstPage] = await Promise.all([
    apiGet<Profile>(path),
    apiGet<Page<PhotoSummary>>(`${path}/photos`),
  ]);
  if (!profile || !firstPage) notFound();

  const joined = new Date(profile.joinedAt);
  return (
    <div className="space-y-4">
      <div className="flex items-start justify-between gap-3">
        <div className="flex items-center gap-3">
          {/* 프로필 사진은 MVP에서 받지 않는다. 닉네임 첫 글자로 표시 (API.md 10장) */}
          <div className="flex h-14 w-14 shrink-0 items-center justify-center rounded-full bg-line text-xl font-bold">
            {profile.nickname.slice(0, 1)}
          </div>
          <div>
            <h1 className="text-lg font-bold">{profile.nickname}</h1>
            <p className="text-sm text-muted">
              작례 {profile.photoCount}장 · {joined.getFullYear()}.{String(joined.getMonth() + 1).padStart(2, "0")} 가입
            </p>
          </div>
        </div>
        <ProfileOwnerActions nickname={profile.nickname} />
      </div>

      <MyHiddenPhotos nickname={profile.nickname} />

      <PhotoGrid
        endpoint={`${path}/photos`}
        initial={firstPage}
        empty={{ title: "아직 올린 작례가 없어요" }}
      />
    </div>
  );
}
