import { notFound } from "next/navigation";
import { Suspense } from "react";
import { PhotoViewer } from "@/components/PhotoViewer";
import { apiGet } from "@/lib/api";
import { readFilters, toQuery } from "@/lib/filters";
import type { PhotoDetail } from "@/lib/types";

type Props = PageProps<"/photos/[id]">;

export default function PhotoPage({ params, searchParams }: Props) {
  return (
    <Suspense fallback={<p className="text-sm text-muted">불러오는 중…</p>}>
      <PhotoContent params={params} searchParams={searchParams} />
    </Suspense>
  );
}

async function PhotoContent({ params, searchParams }: Pick<Props, "params" | "searchParams">) {
  const [{ id }, sp] = await Promise.all([params, searchParams]);
  if (!/^\d{1,18}$/.test(id)) notFound();
  const photo = await apiGet<PhotoDetail>(`/api/photos/${id}`);
  if (!photo) notFound();

  // 기종 격자에서 고른 필터를 그대로 이어서, 옆으로 넘길 때도 같은 작례들 안에서 움직인다
  const query = toQuery(readFilters(sp));
  return <PhotoViewer key={photo.id} initial={photo} query={query} />;
}
