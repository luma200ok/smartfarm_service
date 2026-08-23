"use client";

import { useState } from "react";
import { Screen, ScreenBody, SubNav, TopBar } from "@/components/design-preview/chrome";
import type { ChatTurn } from "@/components/design-preview/mock";
import {
  ACTIVE_FARM,
  CHAT_OPENING,
  CHAT_PLACEHOLDER,
  CHAT_SIDE_NOTE,
  CHAT_SUGGESTIONS,
  PESTICIDE_SOURCE,
  PESTICIDES,
  WEATHER,
} from "@/components/design-preview/mock";
import { Card, CardTitle, LineChart } from "@/components/design-preview/ui";

// 시안 2d — 부가 서비스 · AI 챗봇 중심 + 날씨·농약 패널.
// 제안 칩을 누르면 미리 준비된 답변이 대화에 이어붙는다(목업 — 실제 LLM 호출 없음).
// 입력창은 시안대로 자리만 잡고, 눌러도 서버로 나가는 요청이 없다는 걸 안내로 밝힌다.
export default function DesignPreviewServicesPage() {
  const [turns, setTurns] = useState<ChatTurn[]>(CHAT_OPENING);
  const [used, setUsed] = useState<string[]>([]);

  function ask(label: string) {
    const suggestion = CHAT_SUGGESTIONS.find((s) => s.label === label);
    if (!suggestion || used.includes(label)) return;
    setTurns((prev) => [...prev, { role: "user", text: label }, suggestion.reply]);
    setUsed((prev) => [...prev, label]);
  }

  return (
    <Screen>
      <TopBar status={["알람 3"]} compact />

      <ScreenBody>
        <SubNav section="부가 서비스">
          <div className="mt-4 rounded-lg bg-dp-inset p-3 text-[11.5px] leading-[1.6] text-dp-body">
            {CHAT_SIDE_NOTE}
          </div>
        </SubNav>

        <div className="flex min-w-0 flex-1 gap-3.5 overflow-hidden px-5 py-4.5">
          <Card className="flex min-w-0 flex-1 flex-col overflow-hidden">
            <div className="flex flex-none items-center gap-2.5 border-b border-dp-line px-4.5 py-3.5">
              <span className="flex h-[26px] w-[26px] items-center justify-center rounded-[7px] bg-dp-green text-[11px] leading-none font-bold text-dp-on-green">
                AI
              </span>
              <span className="text-[14px] leading-none font-semibold text-dp-ink">스마트팜 어시스턴트</span>
              <span className="text-[11.5px] leading-none text-dp-muted">{ACTIVE_FARM} 연결됨</span>
              <div className="flex-1" />
              <span className="text-[11.5px] leading-none font-semibold text-dp-green">대화 기록</span>
            </div>

            <div className="flex min-h-0 flex-1 flex-col gap-3.5 overflow-y-auto p-4.5">
              {turns.map((turn, i) => (
                <ChatBubble key={i} turn={turn} />
              ))}

              <div className="flex flex-wrap gap-2 self-start">
                {CHAT_SUGGESTIONS.map((s) => {
                  const spent = used.includes(s.label);
                  return (
                    <button
                      key={s.label}
                      type="button"
                      disabled={spent}
                      onClick={() => ask(s.label)}
                      className={`rounded-[20px] border border-dp-line-strong px-3.5 py-2 text-[12px] leading-none font-medium transition-colors ${
                        spent ? "cursor-default text-dp-faint" : "text-dp-body hover:bg-dp-inset"
                      }`}
                    >
                      {s.label}
                    </button>
                  );
                })}
              </div>
            </div>

            <div className="flex flex-none items-center gap-2.5 border-t border-dp-line px-4.5 py-3.5">
              <div className="flex-1 rounded-[9px] bg-dp-inset px-3.5 py-3 text-[13px] leading-none text-dp-faint">
                {CHAT_PLACEHOLDER}
              </div>
              <span className="rounded-[9px] bg-dp-green px-4.5 py-3 text-[13px] leading-none font-semibold text-dp-on-green">
                전송
              </span>
            </div>
          </Card>

          <div className="flex w-[330px] min-h-0 flex-none flex-col gap-3">
            <WeatherCard />
            <PesticideCard />
          </div>
        </div>
      </ScreenBody>
    </Screen>
  );
}

function ChatBubble({ turn }: { turn: ChatTurn }) {
  if (turn.role === "user") {
    return (
      <div className="max-w-[62%] self-end rounded-[12px] rounded-br-[3px] bg-dp-green-tint-2 px-3.5 py-3 text-[13px] leading-[1.6] text-dp-ink">
        {turn.text}
      </div>
    );
  }

  return (
    <div className="max-w-[76%] self-start rounded-[12px] rounded-bl-[3px] bg-dp-inset px-4 py-3.5 text-[13px] leading-[1.65] text-dp-ink">
      {turn.text}

      {turn.chart ? (
        <div className="mt-3 rounded-lg border border-dp-line bg-dp-surface px-3.5 py-3">
          <div className="mb-2 flex justify-between font-mono text-[11px] leading-none font-semibold text-dp-muted">
            <span>{turn.chart.title}</span>
            <span>{turn.chart.target}</span>
          </div>
          <LineChart
            className="h-[52px]"
            viewBox="0 0 300 60"
            lines={[{ points: turn.chart.series, tone: "red" }]}
          >
            {/* 목표선 */}
            <div className="absolute top-[57%] right-0 left-0 border-t border-dashed border-dp-green-line" />
          </LineChart>
        </div>
      ) : null}

      {turn.tail ? <div className="mt-3">{turn.tail}</div> : null}
    </div>
  );
}

function WeatherCard() {
  return (
    <Card className="px-4 py-3.5">
      <div className="mb-3 flex items-baseline">
        <CardTitle>날씨 예보</CardTitle>
        <div className="flex-1" />
        <span className="text-[11.5px] leading-none text-dp-muted">{WEATHER.place}</span>
      </div>
      <div className="flex items-baseline gap-2.5">
        <span className="text-[32px] leading-none font-bold text-dp-ink">{WEATHER.temp}</span>
        <span className="text-[12.5px] leading-[1.5] font-medium text-dp-body">
          {WEATHER.summary[0]}
          <br />
          {WEATHER.summary[1]}
        </span>
      </div>
      <div className="mt-3.5 grid grid-cols-5 gap-1.5">
        {WEATHER.hourly.map((h) => (
          <div
            key={h.time}
            className={`rounded-[7px] py-2.5 text-center ${h.night ? "bg-dp-blue-tint" : "bg-dp-inset"}`}
          >
            <div className={`font-mono text-[10.5px] leading-none ${h.night ? "text-dp-blue-ink" : "text-dp-muted"}`}>
              {h.time}
            </div>
            <div
              className={`mt-1.5 text-[12.5px] leading-none font-semibold ${
                h.night ? "text-dp-blue-ink" : "text-dp-ink"
              }`}
            >
              {h.temp}
            </div>
          </div>
        ))}
      </div>
      <p className="mt-3 rounded-lg border border-dp-amber-line bg-dp-amber-tint px-3 py-3 text-[11.5px] leading-[1.6] font-medium text-dp-amber-sub">
        {WEATHER.note}
      </p>
    </Card>
  );
}

function PesticideCard() {
  return (
    <Card className="flex min-h-0 flex-1 flex-col overflow-hidden px-4 py-3.5">
      <div className="mb-3 flex items-baseline">
        <CardTitle>농약 정보</CardTitle>
        <div className="flex-1" />
        <span className="text-[11.5px] leading-none font-semibold text-dp-green">검색</span>
      </div>
      <div className="mb-3 rounded-lg bg-dp-inset px-3 py-3 text-[12px] leading-none text-dp-faint">
        작물 · 병해충명으로 검색
      </div>
      <div className="min-h-0 flex-1 overflow-y-auto">
        {PESTICIDES.map((item, i) => (
          <div key={item.name} className={`py-2.5 ${i < PESTICIDES.length - 1 ? "border-b border-dp-line" : ""}`}>
            <div className="text-[12.5px] leading-[1.4] font-semibold text-dp-ink">{item.name}</div>
            <div className="mt-1 text-[11.5px] leading-[1.4] text-dp-muted">{item.detail}</div>
          </div>
        ))}
      </div>
      <div className="mt-2.5 border-t border-dp-line pt-2.5 text-[11px] leading-[1.5] text-dp-muted">
        {PESTICIDE_SOURCE}
      </div>
    </Card>
  );
}
