"use client";

import { useState, type FormEvent } from "react";
import FormField from "@/components/ui/FormField";
import Modal from "@/components/ui/Modal";
import { resolveErrorMessage } from "@/lib/api/errorMessage";
import { createRack, createZone, deleteRack, deleteZone, updateRack, updateZone } from "@/lib/api/zones";
import type { RackRequest, ZoneRequest, ZoneTreeRackNode, ZoneTreeResponse, ZoneTreeZoneNode } from "@/types";

interface ZoneRackManagerProps {
  farmId: string;
  tree: ZoneTreeResponse;
  // ADMIN 전용(이슈 #123 — 구 OWNER 전용에서 이름만 갱신, 서열은 동일). 호출부(FarmDeviceManagement)의
  // hasFarmRoleAtLeast 판정 결과를 그대로 받는다.
  canManageStructure: boolean;
  /** 존·랙 CRUD 성공 후 상위(FarmDeviceManagement)의 존 트리·장비 목록을 함께 재조회시킨다. */
  onChanged: () => void;
}

type ZoneModalState = { mode: "create" } | { mode: "edit"; zone: ZoneTreeZoneNode } | null;
type RackModalState =
  | { mode: "create"; zoneId: number; zoneName: string }
  | { mode: "edit"; zoneId: number; zoneName: string; rack: ZoneTreeRackNode }
  | null;

// 존·랙 구조 관리(이슈 #99 리뷰 반영, contract §4.10) — 진입로가 없으면 신규 사용자는 랙 도면도
// 장비 등록도 영영 쓸 수 없어 devices 화면에 최소 범위(이름·코드·층수)로 붙인다.
// R004(랙 구조 변경 불가 — 하위 활성 장비 잔존)는 ERROR_MESSAGES에 이미 전용 문구가 있어
// resolveErrorMessage가 그대로 정확한 안내를 반환한다(일반 오류로 뭉개지 않음).
export default function ZoneRackManager({ farmId, tree, canManageStructure, onChanged }: ZoneRackManagerProps) {
  const [error, setError] = useState<string | null>(null);
  const [busyId, setBusyId] = useState<number | null>(null);
  const [zoneModal, setZoneModal] = useState<ZoneModalState>(null);
  const [rackModal, setRackModal] = useState<RackModalState>(null);

  async function handleDeleteZone(zone: ZoneTreeZoneNode) {
    if (!window.confirm(`"${zone.name}" 존을 삭제하시겠습니까? 하위 랙·층도 함께 삭제됩니다.`)) return;
    setError(null);
    setBusyId(zone.id);
    try {
      await deleteZone(farmId, zone.id);
      onChanged();
    } catch (err) {
      setError(resolveErrorMessage(err));
    } finally {
      setBusyId(null);
    }
  }

  async function handleDeleteRack(rack: ZoneTreeRackNode) {
    if (!window.confirm(`"${rack.code}" 랙을 삭제하시겠습니까? 하위 층도 함께 삭제됩니다.`)) return;
    setError(null);
    setBusyId(rack.id);
    try {
      await deleteRack(farmId, rack.id);
      onChanged();
    } catch (err) {
      setError(resolveErrorMessage(err));
    } finally {
      setBusyId(null);
    }
  }

  return (
    <div className="flex flex-col gap-3 rounded-md border border-zinc-200 p-4 dark:border-zinc-800">
      <div className="flex items-center justify-between">
        <h3 className="text-sm font-semibold text-zinc-900 dark:text-zinc-50">존 · 랙 구성</h3>
        {canManageStructure && (
          <button
            type="button"
            onClick={() => setZoneModal({ mode: "create" })}
            className="rounded-md border border-zinc-300 px-2.5 py-1 text-xs font-medium text-zinc-700 hover:bg-zinc-50 dark:border-zinc-700 dark:text-zinc-300 dark:hover:bg-zinc-900"
          >
            존 추가
          </button>
        )}
      </div>

      {error && <p className="text-sm text-red-600 dark:text-red-400">{error}</p>}

      {tree.zones.length === 0 && (
        <p className="text-sm text-zinc-500 dark:text-zinc-400">
          등록된 존이 없습니다.{canManageStructure ? " 존을 먼저 만들어야 랙·장비를 등록할 수 있습니다." : ""}
        </p>
      )}

      <div className="flex flex-col gap-3">
        {tree.zones.map((zone) => (
          <div key={zone.id} className="rounded-md border border-zinc-100 p-3 dark:border-zinc-900">
            <div className="flex items-center justify-between gap-2">
              <span className="text-sm font-medium text-zinc-900 dark:text-zinc-50">{zone.name}</span>
              {canManageStructure && (
                <div className="flex flex-none gap-2 text-xs">
                  <button
                    type="button"
                    onClick={() => setZoneModal({ mode: "edit", zone })}
                    className="text-zinc-500 hover:underline dark:text-zinc-400"
                  >
                    수정
                  </button>
                  <button
                    type="button"
                    disabled={busyId === zone.id}
                    onClick={() => handleDeleteZone(zone)}
                    className="text-red-600 hover:underline disabled:opacity-60 dark:text-red-400"
                  >
                    삭제
                  </button>
                  <button
                    type="button"
                    onClick={() => setRackModal({ mode: "create", zoneId: zone.id, zoneName: zone.name })}
                    className="text-zinc-500 hover:underline dark:text-zinc-400"
                  >
                    랙 추가
                  </button>
                </div>
              )}
            </div>

            {zone.racks.length === 0 ? (
              <p className="mt-2 text-xs text-zinc-400 dark:text-zinc-500">등록된 랙이 없습니다.</p>
            ) : (
              <ul className="mt-2 flex flex-col gap-1.5">
                {zone.racks.map((rack) => (
                  <li key={rack.id} className="flex items-center justify-between gap-2 text-xs">
                    <span className="text-zinc-600 dark:text-zinc-300">
                      {rack.code} · {rack.levelCount}층
                    </span>
                    {canManageStructure && (
                      <div className="flex flex-none gap-2">
                        <button
                          type="button"
                          onClick={() => setRackModal({ mode: "edit", zoneId: zone.id, zoneName: zone.name, rack })}
                          className="text-zinc-500 hover:underline dark:text-zinc-400"
                        >
                          수정
                        </button>
                        <button
                          type="button"
                          disabled={busyId === rack.id}
                          onClick={() => handleDeleteRack(rack)}
                          className="text-red-600 hover:underline disabled:opacity-60 dark:text-red-400"
                        >
                          삭제
                        </button>
                      </div>
                    )}
                  </li>
                ))}
              </ul>
            )}
          </div>
        ))}
      </div>

      <Modal
        open={zoneModal !== null}
        onClose={() => setZoneModal(null)}
        title={zoneModal?.mode === "edit" ? "존 수정" : "존 추가"}
      >
        {zoneModal && (
          <ZoneForm
            farmId={farmId}
            modal={zoneModal}
            onClose={() => setZoneModal(null)}
            onSaved={() => {
              setZoneModal(null);
              onChanged();
            }}
          />
        )}
      </Modal>

      <Modal
        open={rackModal !== null}
        onClose={() => setRackModal(null)}
        title={rackModal?.mode === "edit" ? "랙 수정" : `랙 추가 · ${rackModal?.zoneName ?? ""}`}
      >
        {rackModal && (
          <RackForm
            farmId={farmId}
            modal={rackModal}
            onClose={() => setRackModal(null)}
            onSaved={() => {
              setRackModal(null);
              onChanged();
            }}
          />
        )}
      </Modal>
    </div>
  );
}

interface ZoneFormProps {
  farmId: string;
  modal: { mode: "create" } | { mode: "edit"; zone: ZoneTreeZoneNode };
  onClose: () => void;
  onSaved: () => void;
}

function ZoneForm({ farmId, modal, onClose, onSaved }: ZoneFormProps) {
  const initial = modal.mode === "edit" ? modal.zone : null;
  const [name, setName] = useState(initial?.name ?? "");
  const [displayOrder, setDisplayOrder] = useState(initial?.displayOrder?.toString() ?? "");
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);

  async function handleSubmit(e: FormEvent<HTMLFormElement>) {
    e.preventDefault();
    setError(null);
    setSubmitting(true);
    const payload: ZoneRequest = {
      name,
      displayOrder: displayOrder ? Number(displayOrder) : undefined,
    };
    try {
      if (modal.mode === "edit") {
        await updateZone(farmId, modal.zone.id, payload);
      } else {
        await createZone(farmId, payload);
      }
      onSaved();
    } catch (err) {
      setError(resolveErrorMessage(err));
    } finally {
      setSubmitting(false);
    }
  }

  return (
    <form onSubmit={handleSubmit} className="flex flex-col gap-4">
      <FormField id="zone-name" label="존 이름" required maxLength={50} value={name} onChange={(e) => setName(e.target.value)} />
      <FormField
        id="zone-display-order"
        label="표시 순서 (선택)"
        type="number"
        value={displayOrder}
        onChange={(e) => setDisplayOrder(e.target.value)}
      />
      {error && <p className="text-sm text-red-600 dark:text-red-400">{error}</p>}
      <div className="flex gap-2">
        <button
          type="submit"
          disabled={submitting}
          className="rounded-md bg-zinc-900 px-4 py-2 text-sm font-medium text-white transition-colors hover:bg-zinc-700 disabled:cursor-not-allowed disabled:opacity-60 dark:bg-zinc-50 dark:text-zinc-900 dark:hover:bg-zinc-200"
        >
          {submitting ? "저장 중..." : modal.mode === "edit" ? "저장" : "추가"}
        </button>
        <button type="button" onClick={onClose} className="rounded-md border border-zinc-300 px-4 py-2 text-sm dark:border-zinc-700">
          취소
        </button>
      </div>
    </form>
  );
}

interface RackFormProps {
  farmId: string;
  modal:
    | { mode: "create"; zoneId: number; zoneName: string }
    | { mode: "edit"; zoneId: number; zoneName: string; rack: ZoneTreeRackNode };
  onClose: () => void;
  onSaved: () => void;
}

function RackForm({ farmId, modal, onClose, onSaved }: RackFormProps) {
  const initial = modal.mode === "edit" ? modal.rack : null;
  const [code, setCode] = useState(initial?.code ?? "");
  const [levelCount, setLevelCount] = useState(initial?.levelCount?.toString() ?? "5");
  const [displayOrder, setDisplayOrder] = useState(initial?.displayOrder?.toString() ?? "");
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);

  async function handleSubmit(e: FormEvent<HTMLFormElement>) {
    e.preventDefault();
    setError(null);
    setSubmitting(true);
    const payload: RackRequest = {
      code,
      levelCount: Number(levelCount),
      displayOrder: displayOrder ? Number(displayOrder) : undefined,
    };
    try {
      if (modal.mode === "edit") {
        await updateRack(farmId, modal.rack.id, payload);
      } else {
        await createRack(farmId, modal.zoneId, payload);
      }
      onSaved();
    } catch (err) {
      setError(resolveErrorMessage(err));
    } finally {
      setSubmitting(false);
    }
  }

  return (
    <form onSubmit={handleSubmit} className="flex flex-col gap-4">
      <FormField id="rack-code" label="랙 코드" required maxLength={30} value={code} onChange={(e) => setCode(e.target.value)} />
      <FormField
        id="rack-level-count"
        label="층수 (1~50)"
        type="number"
        min={1}
        max={50}
        required
        value={levelCount}
        onChange={(e) => setLevelCount(e.target.value)}
      />
      {modal.mode === "edit" && (
        <p className="text-xs text-amber-600 dark:text-amber-400">
          층수를 줄이면 잘려나가는 층에 활성 장비가 있을 때 저장이 거부됩니다(장비를 먼저 이동·삭제하세요).
        </p>
      )}
      <FormField
        id="rack-display-order"
        label="표시 순서 (선택)"
        type="number"
        value={displayOrder}
        onChange={(e) => setDisplayOrder(e.target.value)}
      />
      {error && <p className="text-sm text-red-600 dark:text-red-400">{error}</p>}
      <div className="flex gap-2">
        <button
          type="submit"
          disabled={submitting}
          className="rounded-md bg-zinc-900 px-4 py-2 text-sm font-medium text-white transition-colors hover:bg-zinc-700 disabled:cursor-not-allowed disabled:opacity-60 dark:bg-zinc-50 dark:text-zinc-900 dark:hover:bg-zinc-200"
        >
          {submitting ? "저장 중..." : modal.mode === "edit" ? "저장" : "추가"}
        </button>
        <button type="button" onClick={onClose} className="rounded-md border border-zinc-300 px-4 py-2 text-sm dark:border-zinc-700">
          취소
        </button>
      </div>
    </form>
  );
}
