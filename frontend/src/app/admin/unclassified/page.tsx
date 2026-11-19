import type { Metadata } from "next";
import { AdminNav } from "@/components/AdminNav";
import { AdminUnclassified } from "@/components/AdminUnclassified";

export const metadata: Metadata = { title: "미분류 작례", robots: { index: false } };

export default function AdminUnclassifiedPage() {
  return (
    <div className="space-y-4">
      <AdminNav current="/admin/unclassified" />
      <h1 className="text-xl font-bold">미분류 작례</h1>
      <AdminUnclassified />
    </div>
  );
}
