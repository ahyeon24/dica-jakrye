import type { Metadata } from "next";
import { AdminReports } from "@/components/AdminReports";

export const metadata: Metadata = { title: "신고 처리", robots: { index: false } };

export default function AdminReportsPage() {
  return (
    <div className="space-y-4">
      <h1 className="text-xl font-bold">신고 처리</h1>
      <AdminReports />
    </div>
  );
}
