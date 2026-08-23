"use client";

import { useState } from "react";
import { Screen, ScreenBody, TopBar } from "@/components/design-preview/chrome";
import type { ChatTurn } from "@/components/design-preview/mock";
import {
  ACTIVE_FARM,
  CHAT_OPENING,
  CHAT_PLACEHOLDER,
  CHAT_SIDE_NOTE,
  CHAT_SUGGESTIONS,
  PESTICIDE_ALERTS,
  PESTICIDE_SOURCE,
  PESTICIDES,
  UNREAD_ALARMS,
  WEATHER,
} from "@/components/design-preview/mock";
import { Card, CardTitle, LineChart, NoteBlock } from "@/components/design-preview/ui";

// 화면 05 — 부가 서비스 · AI 챗봇. 핸드오프 `Screens › 05` + `Interactions › 부가 서비스`.
// 추천 질문 칩을 누르면 즉시 전송되고, 응답 대기 중에는 AI 버블 자리에 로딩을 표시하며
// 입력·전송을 비활성화한다. 답변은 미리 준비된 목업이며 실제 LLM 호출은 없다.
export default function DesignPreviewServicesPage() {
  const [turns, setTurns] = useState<ChatTurn[]>(CHAT_OPENING);
  const [used, setUsed] = useState<string[]>([]);
  const [awaiting, setAwaiting] = useState(false);

  function ask(label: string) {
    const suggestion = CHAT_SUGGESTIONS.find((s) => s.label === label);
    if (!suggestion || used.includes(label) || awaiting) return;
    setTurns((prev) => [...prev, { role: "user", text: label }]);
    setUsed((prev) => [...prev, label]);
    setAwaiting(true);
    // 응답 대기 상태를 눈으로 볼 수 있게만 짧게 지연시킨다(목업).
    window.setTimeout(() => {
      setTurns((prev) => [...prev, suggestion.reply]);
      setAwaiting(false);
    }, 600);
  }

  return (
    <Screen>
      <TopBar status={[`알람 ${UNREAD_ALARMS}`]} />

      <ScreenBody
        section="services"
        aside={<div className="mt-4 rounded-lg bg-dp-inset p-3 text-[11.5px] leading-[1.6] text-dp-sub">{CHAT_SIDE_NOTE}</div>}
      >
        <div className="grid gap-3.5 min-[1440px]:grid-cols-[1fr_340px] min-[1920px]:grid-cols-[1fr_430px]">
          <Card className="flex min-h-[560px] flex-col overflow-hidden">
            <div className="flex flex-none flex-wrap items-center gap-2.5 border-b border-dp-line px-[18px] py-3.5">
              <span className="flex h-[26px] w-[26px] items-center justify-center rounded-[7px] bg-dp-green text-[11px] leading-none font-bold text-dp-on-green">
                AI
              </span>
              <span className="text-[14px] leading-none font-semibold text-dp-ink">스마트팜 어시스턴트</span>
              <span className="text-[11.5px] leading-none text-dp-muted">{ACTIVE_FARM} 연결됨</span>
              <div className="flex-1" />
              <span className="text-[11.5px] leading-none font-semibold text-dp-green">대화 기록</span>
            </div>

            <div className="flex min-h-0 flex-1 flex-col gap-3.5 overflow-y-auto p-[18px]">
              {turns.map((turn, i) => (
                <ChatBubble key={i} turn={turn} />
              ))}

              {awaiting ? (
                <div className="flex max-w-[76%] gap-1.5 self-start rounded-[12px] rounded-bl-[3px] bg-dp-inset px-4 py-3.5">
                  <Dot delay="0ms" />
                  <Dot delay="150ms" />
                  <Dot delay="300ms" />
                  <span className="sr-only">답변을 작성하고 있습니다</span>
                </div>
              ) : null}

              <div className="flex flex-wrap gap-2 self-start">
                {CHAT_SUGGESTIONS.map((s) => {
                  const spent = used.includes(s.label);
                  return (
                    <button
                      key={s.label}
                      type="button"
                      disabled={spent || awaiting}
                      onClick={() => ask(s.label)}
                      className={`rounded-[20px] border border-dp-line-strong px-3.5 py-2 text-[12px] leading-none font-medium transition-colors ${
                        spent || awaiting ? "cursor-default text-dp-faint" : "text-dp-body hover:bg-dp-inset"
                      }`}
                    >
                      {s.label}
                    </button>
                  );
                })}
              </div>
            </div>

            <div className="flex flex-none items-center gap-2.5 border-t border-dp-line px-[18px] py-3.5">
              <div
                className={`flex-1 rounded-[9px] bg-dp-inset px-3.5 py-3 text-[13px] leading-none ${
                  awaiting ? "text-dp-faint opacity-60" : "text-dp-faint"
                }`}
              >
                {CHAT_PLACEHOLDER}
              </div>
              <span
                className={`rounded-[9px] px-[18px] py-3 text-[13px] leading-none font-semibold ${
                  awaiting ? "bg-dp-off text-dp-faint" : "bg-dp-green text-dp-on-green"
                }`}
              >
                전송
              </span>
            </div>
          </Card>

          <div className="flex flex-col gap-3">
            <WeatherCard />
            <PesticideCard />
          </div>
        </div>
      </ScreenBody>
    </Screen>
  );
}

function Dot({ delay }: { delay: string }) {
  return (
    <span
      className="h-1.5 w-1.5 animate-pulse rounded-full bg-dp-muted"
      style={{ animationDelay: delay }}
      aria-hidden
    />
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
          <LineChart className="h-[52px]" viewBox="0 0 300 60" lines={[{ points: turn.chart.series, tone: "red" }]}>
            {/* 목표선 */}
            <div className="absolute top-[57%] right-0 left-0 border-t border-dashed border-dp-green-target" />
          </LineChart>
        </div>
      ) : null}

      {turn.tail ? <div className="mt-3">{turn.tail}</div> : null}
    </div>
  );
}

function WeatherCard() {
  return (
    <Card className="px-4 py-[15px]">
      <div className="mb-3 flex items-baseline">
        <CardTitle>날씨 예보</CardTitle>
        <div className="flex-1" />
        <span className="text-[11.5px] leading-none text-dp-muted">{WEATHER.place}</span>
      </div>
      <div className="flex items-baseline gap-2.5">
        <span className="text-[32px] leading-none font-bold text-dp-ink">{WEATHER.temp}</span>
        <span className="text-[12.5px] leading-[1.5] font-medium text-dp-sub">
          {WEATHER.summary[0]}
          <br />
          {WEATHER.summary[1]}
        </span>
      </div>
      <div className="mt-3.5 grid grid-cols-5 gap-1.5">
        {WEATHER.hourly.map((h) => (
          <div key={h.time} className={`rounded-[7px] py-2.5 text-center ${h.night ? "bg-dp-blue-tint" : "bg-dp-inset"}`}>
            <div className={`font-mono text-[10.5px] leading-none ${h.night ? "text-dp-blue-ink" : "text-dp-muted"}`}>
              {h.time}
            </div>
            <div
              className={`mt-1.5 text-[12.5px] leading-none font-semibold ${h.night ? "text-dp-blue-ink" : "text-dp-ink"}`}
            >
              {h.temp}
            </div>
          </div>
        ))}
      </div>
      <NoteBlock tone="warning" className="mt-3">
        {WEATHER.note}
      </NoteBlock>
    </Card>
  );
}

function PesticideCard() {
  return (
    <Card className="flex flex-1 flex-col px-4 py-[15px]">
      <div className="mb-3 flex items-baseline">
        <CardTitle>농약 정보</CardTitle>
        <div className="flex-1" />
        <span className="text-[11.5px] leading-none font-semibold text-dp-green">검색</span>
      </div>
      <div className="mb-3 rounded-lg bg-dp-inset px-3 py-3 text-[12px] leading-none text-dp-faint">
        작물 · 병해충명으로 검색
      </div>
      <div>
        {PESTICIDES.map((item, i) => (
          <div key={item.name} className={`py-2.5 ${i < PESTICIDES.length - 1 ? "border-b border-dp-line-row" : ""}`}>
            <div className="text-[12.5px] leading-[1.4] font-semibold text-dp-ink">{item.name}</div>
            <div className="mt-1 text-[11.5px] leading-[1.4] text-dp-muted">{item.detail}</div>
          </div>
        ))}
      </div>

      <div className="mt-3.5 border-t border-dp-line pt-3">
        <div className="mb-2.5 text-[12.5px] leading-none font-semibold text-dp-ink">이번 주 발생 주의</div>
        <div className="flex flex-col gap-2">
          {PESTICIDE_ALERTS.map((alert) => (
            <NoteBlock key={alert.text} tone={alert.tone === "warning" ? "warning" : "plain"}>
              {alert.text}
            </NoteBlock>
          ))}
        </div>
      </div>

      <div className="flex-1" />
      <div className="mt-2.5 border-t border-dp-line pt-2.5 text-[11px] leading-[1.5] text-dp-muted">
        {PESTICIDE_SOURCE}
      </div>
    </Card>
  );
}
