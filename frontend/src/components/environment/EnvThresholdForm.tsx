"use client";

import { useEffect, useState, type FormEvent } from "react";
import FormField from "@/components/ui/FormField";
import { ENV_THRESHOLD_RANGE } from "@/constants";
import { resolveErrorMessage } from "@/lib/api/errorMessage";
import { getEnvThresholds, updateEnvThresholds } from "@/lib/api/environment";
import type { EnvThresholdsRequest, EnvThresholdsResponse } from "@/types";

interface EnvThresholdFormProps {
  farmId: number | string;
}

type FieldKey = "indoorTempMin" | "indoorTempMax" | "indoorHumidityMin" | "indoorHumidityMax";

const FIELD_CONFIG: Record<FieldKey, { label: string; min: number; max: number }> = {
  indoorTempMin: { label: "내부 온도 하한(℃)", min: ENV_THRESHOLD_RANGE.temp.min, max: ENV_THRESHOLD_RANGE.temp.max },
  indoorTempMax: { label: "내부 온도 상한(℃)", min: ENV_THRESHOLD_RANGE.temp.min, max: ENV_THRESHOLD_RANGE.temp.max },
  indoorHumidityMin: {
    label: "내부 습도 하한(%)",
    min: ENV_THRESHOLD_RANGE.humidity.min,
    max: ENV_THRESHOLD_RANGE.humidity.max,
  },
  indoorHumidityMax: {
    label: "내부 습도 상한(%)",
    min: ENV_THRESHOLD_RANGE.humidity.min,
    max: ENV_THRESHOLD_RANGE.humidity.max,
  },
};

const FIELD_KEYS = Object.keys(FIELD_CONFIG) as FieldKey[];

function toInputValue(v: number | null | undefined): string {
  return v === null || v === undefined ? "" : String(v);
}

function parseField(v: string): number | null {
  if (v.trim() === "") return null;
  const n = Number(v);
  return Number.isNaN(n) ? null : n;
}

// 임계치 알림 설정 폼(이슈 #53, 다함 벤치마킹 2) — 호출부(FarmOverview)가 myRole==='OWNER'일 때만 마운트.
// 상하한 역전·범위 초과 등 값 검증은 서버가 C001로 판정 — 메시지 문자열 매칭 대신
// resolveErrorMessage(ErrorCode 기준)로만 분기한다(레포 컨벤션).
export default function EnvThresholdForm({ farmId }: EnvThresholdFormProps) {
  const [loaded, setLoaded] = useState(false);
  const [loadError, setLoadError] = useState<string | null>(null);
  const [enabled, setEnabled] = useState(false);
  const [fields, setFields] = useState<Record<FieldKey, string>>({
    indoorTempMin: "",
    indoorTempMax: "",
    indoorHumidityMin: "",
    indoorHumidityMax: "",
  });
  const [saving, setSaving] = useState(false);
  const [saveError, setSaveError] = useState<string | null>(null);
  const [saveSuccess, setSaveSuccess] = useState(false);

  useEffect(() => {
    let cancelled = false;

    function applyThresholds(res: EnvThresholdsResponse) {
      setEnabled(res.enabled);
      setFields({
        indoorTempMin: toInputValue(res.indoorTempMin),
        indoorTempMax: toInputValue(res.indoorTempMax),
        indoorHumidityMin: toInputValue(res.indoorHumidityMin),
        indoorHumidityMax: toInputValue(res.indoorHumidityMax),
      });
    }

    getEnvThresholds(farmId)
      .then((res) => {
        if (cancelled) return;
        applyThresholds(res);
        setLoaded(true);
      })
      .catch((err) => {
        if (cancelled) return;
        setLoadError(resolveErrorMessage(err));
        setLoaded(true);
      });

    return () => {
      cancelled = true;
    };
  }, [farmId]);

  function handleFieldChange(key: FieldKey, value: string) {
    setFields((prev) => ({ ...prev, [key]: value }));
  }

  async function handleSubmit(e: FormEvent<HTMLFormElement>) {
    e.preventDefault();
    setSaveError(null);
    setSaveSuccess(false);
    setSaving(true);

    const payload: EnvThresholdsRequest = {
      enabled,
      indoorTempMin: parseField(fields.indoorTempMin),
      indoorTempMax: parseField(fields.indoorTempMax),
      indoorHumidityMin: parseField(fields.indoorHumidityMin),
      indoorHumidityMax: parseField(fields.indoorHumidityMax),
    };

    try {
      const updated = await updateEnvThresholds(farmId, payload);
      setEnabled(updated.enabled);
      setFields({
        indoorTempMin: toInputValue(updated.indoorTempMin),
        indoorTempMax: toInputValue(updated.indoorTempMax),
        indoorHumidityMin: toInputValue(updated.indoorHumidityMin),
        indoorHumidityMax: toInputValue(updated.indoorHumidityMax),
      });
      setSaveSuccess(true);
    } catch (err) {
      setSaveError(resolveErrorMessage(err));
    } finally {
      setSaving(false);
    }
  }

  if (!loaded) {
    return (
      <section className="rounded-lg border border-zinc-200 p-4 text-sm text-zinc-500 dark:border-zinc-800 dark:text-zinc-400">
        임계치 설정 불러오는 중...
      </section>
    );
  }

  if (loadError) {
    return (
      <section className="rounded-lg border border-zinc-200 p-4 text-sm text-red-600 dark:border-zinc-800 dark:text-red-400">
        {loadError}
      </section>
    );
  }

  return (
    <form
      onSubmit={handleSubmit}
      className="flex flex-col gap-3 rounded-lg border border-zinc-200 p-4 dark:border-zinc-800"
    >
      <div className="flex items-center justify-between">
        <h3 className="text-sm font-semibold text-zinc-900 dark:text-zinc-50">임계치 알림 설정</h3>
        <label className="flex items-center gap-2 text-sm text-zinc-600 dark:text-zinc-400">
          <input
            type="checkbox"
            checked={enabled}
            onChange={(e) => setEnabled(e.target.checked)}
            className="h-4 w-4 rounded border-zinc-300 dark:border-zinc-700"
          />
          알림 사용
        </label>
      </div>

      <div className="grid grid-cols-2 gap-3">
        {FIELD_KEYS.map((key) => (
          <FormField
            key={key}
            id={`threshold-${key}`}
            label={FIELD_CONFIG[key].label}
            type="number"
            step="0.1"
            min={FIELD_CONFIG[key].min}
            max={FIELD_CONFIG[key].max}
            value={fields[key]}
            onChange={(e) => handleFieldChange(key, e.target.value)}
            disabled={!enabled}
          />
        ))}
      </div>

      {saveError && <p className="text-sm text-red-600 dark:text-red-400">{saveError}</p>}
      {saveSuccess && <p className="text-sm text-emerald-600 dark:text-emerald-400">저장되었습니다.</p>}

      <button
        type="submit"
        disabled={saving}
        className="self-start rounded-md bg-zinc-900 px-3 py-1.5 text-sm font-medium text-white disabled:opacity-60 dark:bg-zinc-50 dark:text-zinc-900"
      >
        {saving ? "저장 중..." : "저장"}
      </button>
    </form>
  );
}
