package com.smartfarm.service.dto;

import com.smartfarm.service.entity.CropType;
import com.smartfarm.service.entity.FarmRole;

/**
 * 내 농장 목록 1건 (contract §3 {@code GET /api/farms}).
 *
 * <p>{@code id}는 <b>농장</b> id, {@code memberId}는 <b>요청자 본인의 멤버십</b> id다 — 헷갈리기
 * 쉬우니 필드 용도를 여기서 못박아 둔다.
 *
 * <p><b>왜 memberId가 필요한가</b>(이슈 #122): 승인 대기자({@link FarmRole#PENDING})는 자기
 * 대기 상태를 스스로 취소할 수 있는데({@code DELETE /api/farms/{farmId}/members/{memberId}}),
 * 그 호출에 필요한 memberId를 알아낼 방법이 없었다 — 멤버 목록({@code GET .../members})은
 * {@code requireMember}가 PENDING을 F008로 막고, 농장 상세·초대 수락 응답에도 그 값이 없다.
 * 이 엔드포인트는 farm-scoped 가드를 타지 않아 <b>PENDING도 접근할 수 있는 유일한 표면</b>이라
 * 여기에 실어 탈출구의 "문고리"를 만든다.
 *
 * <p>⚠️ <b>본인 멤버십 id만 실린다.</b> 이 값을 만드는 쿼리({@code FarmMemberRepository#findMyFarms})가
 * {@code fm.userId = :userId}로 스코프돼 있어, 구조적으로 타인의 memberId가 들어올 수 없다 —
 * 쿼리를 고칠 때 이 스코프를 유지할 것.
 */
public record FarmSummaryResponse(
        Long id,
        String name,
        CropType cropType,
        FarmRole myRole,
        Long memberId
) {
}
