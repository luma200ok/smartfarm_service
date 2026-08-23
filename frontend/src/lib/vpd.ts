// VPD(포화수증기압 차이)·이슬점 순수 계산 (contract §4.8 — BE 없음, FE 전용).
//
// 근거·출처:
// - 포화수증기압(es, Tetens 근사식): FAO Irrigation and Drainage Paper No.56
//   (Allen, Pereira, Raes, Smith, 1998) 식 11 — es(T) = 0.6108 * exp(17.27*T / (T+237.3)) [kPa]
// - VPD = es * (1 - RH/100) — 실제 수증기압(ea = es * RH/100)과의 차이.
// - 이슬점(Td, Magnus-Tetens 역산): Lawrence, M.G. (2005), "The Relationship between
//   Relative Humidity and the Dewpoint Temperature in Moist Air", BAMS 86(2) — 동일 계수(17.27, 237.3)
//   를 사용한 역산식.
// - 토마토 생육 권장 VPD 범위 0.4~1.2 kPa: 온실원예 통용 가이드라인(과습·수분스트레스 방지 구간).
export const TOMATO_VPD_RANGE_KPA = { min: 0.4, max: 1.2 } as const;

/** 포화수증기압 es(T) [kPa] — FAO-56 식 11 (Tetens 근사). */
export function saturationVaporPressureKpa(tempC: number): number {
  return 0.6108 * Math.exp((17.27 * tempC) / (tempC + 237.3));
}

/** VPD(포화수증기압차) [kPa] = es(T) * (1 - RH/100). */
export function vaporPressureDeficitKpa(tempC: number, relativeHumidityPercent: number): number {
  const es = saturationVaporPressureKpa(tempC);
  return es * (1 - relativeHumidityPercent / 100);
}

/** 이슬점 Td [℃] — Magnus-Tetens 역산(계수는 saturationVaporPressureKpa와 동일). */
export function dewPointC(tempC: number, relativeHumidityPercent: number): number {
  const rh = Math.max(relativeHumidityPercent, 0.001); // ln(0) 방지(RH=0은 물리적으로 이슬점 미정의)
  const alpha = Math.log(rh / 100) + (17.27 * tempC) / (237.3 + tempC);
  return (237.3 * alpha) / (17.27 - alpha);
}

export type VpdLevel = "low" | "optimal" | "high";

/** 토마토 권장 VPD(0.4~1.2 kPa) 구간 대비 현재 수준 분류 — 안내 문구용. */
export function classifyVpdLevel(vpdKpa: number): VpdLevel {
  if (vpdKpa < TOMATO_VPD_RANGE_KPA.min) return "low";
  if (vpdKpa > TOMATO_VPD_RANGE_KPA.max) return "high";
  return "optimal";
}
