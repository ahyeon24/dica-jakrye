import type { Metadata } from "next";
import { SignupForm } from "@/components/SignupForm";

export const metadata: Metadata = { title: "가입", robots: { index: false } };

export default function SignupPage() {
  return (
    <div className="mx-auto max-w-sm space-y-4 py-10">
      <h1 className="text-xl font-bold">가입</h1>
      <SignupForm />
    </div>
  );
}
