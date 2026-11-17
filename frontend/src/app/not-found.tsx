import Link from "next/link";

export default function NotFound() {
  return (
    <div className="space-y-2 py-16 text-center">
      <h1 className="text-lg font-bold">찾을 수 없는 페이지예요</h1>
      <Link href="/" className="text-sm text-muted underline">
        홈으로
      </Link>
    </div>
  );
}
