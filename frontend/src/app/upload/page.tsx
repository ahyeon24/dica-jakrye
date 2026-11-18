import type { Metadata } from "next";
import { UploadForm } from "@/components/UploadForm";

export const metadata: Metadata = { title: "작례 올리기", robots: { index: false } };

export default function UploadPage() {
  return (
    <div className="mx-auto max-w-md space-y-4">
      <h1 className="text-xl font-bold">작례 올리기</h1>
      <UploadForm />
    </div>
  );
}
