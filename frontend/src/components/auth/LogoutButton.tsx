"use client";

import { useRouter } from "next/navigation";
import { useState } from "react";
import { logout } from "@/lib/api/auth";

export default function LogoutButton() {
  const router = useRouter();
  const [submitting, setSubmitting] = useState(false);

  async function handleLogout() {
    setSubmitting(true);
    try {
      await logout();
    } finally {
      router.replace("/login");
    }
  }

  return (
    <button
      type="button"
      onClick={handleLogout}
      disabled={submitting}
      className="rounded-md border border-zinc-300 px-3 py-1.5 text-sm font-medium text-zinc-700 transition-colors hover:bg-zinc-100 disabled:cursor-not-allowed disabled:opacity-60 dark:border-zinc-700 dark:text-zinc-300 dark:hover:bg-zinc-900"
    >
      로그아웃
    </button>
  );
}
