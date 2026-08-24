"use client";

import { useEffect, useState, type FormEvent } from "react";
import { Card, CardTitle } from "@/components/monitoring/ui";
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

// 상하한 역전 선검증(리뷰 픽스 #53 P2-1) — FarmOverview.handleEditSubmit과 동일하게
// 클라이언트에서 먼저 막아 왕복을 줄인다. 둘 다 입력된 경우에만 비교하고, 값 범위(-50~80·0~100)
// 등 나머지 검증은 여전히 서버 C001을 최종 방어선으로 둔다(레포 컨벤션 — 서버 검증 로직 중복 최소화).
function findCrossFieldError(payload: EnvThresholdsRequest): string | null {
  const { indoorTempMin, indoorTempMax, indoorHumidityMin, indoorHumidityMax } = payload;
  if (indoorTempMin != null && indoorTempMax != null && indoorTempMin >= indoorTempMax) {
    return "내부 온도 하한은 상한보다 작아야 합니다.";
  }
  if (indoorHumidityMin != null && indoorHumidityMax != null && indoorHumidityMin >= indoorHumidityMax) {
    return "내부 습도 하한은 상한보다 작아야 합니다.";
  }
  return null;
}

// 임계치 알림 설정 폼(이슈 #53, 다함 벤치마킹 2) — 호출부(FarmOverview)가 myRole==='OWNER'일 때만 마운트.
// 상하한 역전·범위 초과 등 값 검증은 서버가 C001로 판정 — 메시지 문자열 매칭 대신
// resolveErrorMessage(ErrorCode 기준)로만 분기한다(레포 컨벤션).
// 표현은 --dp-* 토큰 기반 공용 프리미티브(Card·CardTitle)로 통일한다(이슈 #109). FormField는 폼
// 계열 공용 컴포넌트라 그대로 쓴다.
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

    const payload: EnvThresholdsRequest = {
      enabled,
      indoorTempMin: parseField(fields.indoorTempMin),
      indoorTempMax: parseField(fields.indoorTempMax),
      indoorHumidityMin: parseField(fields.indoorHumidityMin),
      indoorHumidityMax: parseField(fields.indoorHumidityMax),
    };

    const crossFieldError = findCrossFieldError(payload);
    if (crossFieldError) {
      setSaveError(crossFieldError);
      return;
    }

    setSaving(true);
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
    return <Card className="p-4 text-sm text-dp-sub">임계치 설정 불러오는 중...</Card>;
  }

  if (loadError) {
    return <Card className="p-4 text-sm text-dp-red-ink">{loadError}</Card>;
  }

  return (
    <Card className="p-4">
      <form onSubmit={handleSubmit} className="flex flex-col gap-3">
        <div className="flex items-center justify-between">
          <CardTitle>임계치 알림 설정</CardTitle>
          <label className="flex items-center gap-2 text-sm text-dp-sub">
            <input
              type="checkbox"
              checked={enabled}
              onChange={(e) => setEnabled(e.target.checked)}
              className="h-4 w-4 rounded border-dp-line-strong"
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

        {saveError && <p className="text-sm text-dp-red-ink">{saveError}</p>}
        {saveSuccess && <p className="text-sm text-dp-green-ink">저장되었습니다.</p>}

        <button
          type="submit"
          disabled={saving}
          className="self-start rounded-md bg-dp-ink px-3 py-1.5 text-sm font-medium text-dp-surface disabled:opacity-40"
        >
          {saving ? "저장 중..." : "저장"}
        </button>
      </form>
    </Card>
  );
}
