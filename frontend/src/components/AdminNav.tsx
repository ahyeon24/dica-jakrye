import Link from "next/link";

const ITEMS = [
  { href: "/admin/reports", label: "신고 처리" },
  { href: "/admin/unclassified", label: "미분류 작례" },
] as const;

/** 관리자 화면 사이 이동. 지금 화면은 current로 받는다 (usePathname을 쓰면 Suspense가 필요해서) */
export function AdminNav({ current }: { current: (typeof ITEMS)[number]["href"] }) {
  return (
    <nav className="flex gap-4 border-b border-line text-sm">
      {ITEMS.map((it) => (
        <Link
          key={it.href}
          href={it.href}
          aria-current={it.href === current ? "page" : undefined}
          className={`-mb-px border-b-2 pb-2 ${it.href === current ? "border-foreground font-medium" : "border-transparent text-muted hover:text-foreground"}`}
        >
          {it.label}
        </Link>
      ))}
    </nav>
  );
}
