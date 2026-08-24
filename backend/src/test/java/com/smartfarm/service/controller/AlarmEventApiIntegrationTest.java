package com.smartfarm.service.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import static org.assertj.core.api.Assertions.assertThat;

import com.smartfarm.service.FarmTestSupport;
import com.smartfarm.service.dto.AlarmMemoRequest;
import com.smartfarm.service.entity.AlarmEvent;
import com.smartfarm.service.entity.AlarmSeverity;
import com.smartfarm.service.entity.AlarmSourceType;
import com.smartfarm.service.repository.AlarmEventRepository;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

/**
 * 알람 이벤트 API 통합 테스트(이슈 #116) — 목록 페이지네이션·상세 타임라인·상태 전이(확인/처리완료/
 * 전체확인)·메모·통계·미확인건수·farm-scoped 접근 제어를 실제 DB(Testcontainers PostgreSQL)로
 * 검증한다. 테스트마다 유니크 farmId로 자연 격리되므로 클래스 레벨 @Transactional은 두지 않는다
 * (ReadingApiIntegrationTest 선례와 동일 원칙).
 */
class AlarmEventApiIntegrationTest extends FarmTestSupport {

    @Autowired
    private AlarmEventRepository alarmEventRepository;

    private AlarmEvent saveEvent(long farmId, String metricKey, LocalDateTime occurredAt) {
        return alarmEventRepository.save(AlarmEvent.builder()
                .farmId(farmId)
                .severity(AlarmSeverity.WARNING)
                .sourceType(AlarmSourceType.ENV_THRESHOLD)
                .metricKey(metricKey)
                .message(metricKey + " 이탈")
                .occurredAt(occurredAt)
                .build());
    }

    // ── 목록 조회(페이지네이션·필터) ──────────────────────────────────

    @Test
    @DisplayName("목록 조회는 페이지네이션 파라미터대로 페이지를 나눠 반환한다")
    void listAlarmEventsPaginated() throws Exception {
        String token = signupAndLogin("알람농부-페이지");
        long farmId = createFarm(token, "페이지농장");
        LocalDateTime now = LocalDateTime.now();
        saveEvent(farmId, "INDOOR_TEMP_HIGH", now.minusMinutes(1));
        saveEvent(farmId, "INDOOR_TEMP_LOW", now.minusMinutes(2));
        saveEvent(farmId, "INDOOR_HUMIDITY_HIGH", now.minusMinutes(3));

        mockMvc.perform(get("/api/farms/" + farmId + "/alarm-events")
                        .param("page", "0")
                        .param("size", "2")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(2))
                .andExpect(jsonPath("$.totalElements").value(3))
                .andExpect(jsonPath("$.totalPages").value(2))
                .andExpect(jsonPath("$.page").value(0));

        mockMvc.perform(get("/api/farms/" + farmId + "/alarm-events")
                        .param("page", "1")
                        .param("size", "2")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1));
    }

    @Test
    @DisplayName("status 필터로 UNACKNOWLEDGED만 조회할 수 있다")
    void listAlarmEventsFilteredByStatus() throws Exception {
        String token = signupAndLogin("알람농부-필터");
        long farmId = createFarm(token, "필터농장");
        long eventId = saveEvent(farmId, "INDOOR_TEMP_HIGH", LocalDateTime.now()).getId();
        saveEvent(farmId, "INDOOR_TEMP_LOW", LocalDateTime.now());

        mockMvc.perform(patch("/api/farms/" + farmId + "/alarm-events/" + eventId + "/acknowledge")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/farms/" + farmId + "/alarm-events")
                        .param("status", "UNACKNOWLEDGED")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].metricKey").value("INDOOR_TEMP_LOW"));
    }

    @Test
    @DisplayName("cross-tenant: 미멤버가 목록 조회 시 403 F002를 반환한다")
    void listAlarmEventsAsNonMemberForbidden() throws Exception {
        String ownerToken = signupAndLogin("알람농장주-격리");
        String otherToken = signupAndLogin("알람남남-격리");
        long farmId = createFarm(ownerToken, "격리농장");

        mockMvc.perform(get("/api/farms/" + farmId + "/alarm-events")
                        .header("Authorization", "Bearer " + otherToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("F002"));
    }

    // ── 상세 조회 ──────────────────────────────────────────────────

    @Test
    @DisplayName("상세 조회는 이벤트 정보와 타임라인을 함께 반환한다")
    void getAlarmEventDetailWithTimeline() throws Exception {
        String token = signupAndLogin("알람농부-상세");
        long farmId = createFarm(token, "상세농장");
        long eventId = saveEvent(farmId, "INDOOR_TEMP_HIGH", LocalDateTime.now()).getId();

        mockMvc.perform(post("/api/farms/" + farmId + "/alarm-events/" + eventId + "/memo")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new AlarmMemoRequest("확인 중입니다"))))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/farms/" + farmId + "/alarm-events/" + eventId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.event.id").value(eventId))
                .andExpect(jsonPath("$.event.metricKey").value("INDOOR_TEMP_HIGH"))
                .andExpect(jsonPath("$.timeline.length()").value(1))
                .andExpect(jsonPath("$.timeline[0].action").value("MEMO_ADDED"))
                .andExpect(jsonPath("$.timeline[0].note").value("확인 중입니다"));
    }

    @Test
    @DisplayName("cross-tenant: 타 농장 alarmEventId로 상세 조회 시 404 AL001을 반환한다(farm 스코프)")
    void getAlarmEventCrossTenantNotFound() throws Exception {
        String ownerAToken = signupAndLogin("알람농장주A");
        String ownerBToken = signupAndLogin("알람농장주B");
        long farmA = createFarm(ownerAToken, "알람농장A");
        long farmB = createFarm(ownerBToken, "알람농장B");
        long eventIdInFarmA = saveEvent(farmA, "INDOOR_TEMP_HIGH", LocalDateTime.now()).getId();

        mockMvc.perform(get("/api/farms/" + farmB + "/alarm-events/" + eventIdInFarmA)
                        .header("Authorization", "Bearer " + ownerBToken))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("AL001"));
    }

    // ── 상태 전이(확인/처리완료) ──────────────────────────────────────

    @Test
    @DisplayName("UNACKNOWLEDGED 이벤트를 acknowledge하면 200과 함께 ACKNOWLEDGED로 전이한다")
    void acknowledgeSuccess() throws Exception {
        String token = signupAndLogin("알람농부-확인");
        long farmId = createFarm(token, "확인농장");
        long eventId = saveEvent(farmId, "INDOOR_TEMP_HIGH", LocalDateTime.now()).getId();

        mockMvc.perform(patch("/api/farms/" + farmId + "/alarm-events/" + eventId + "/acknowledge")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACKNOWLEDGED"))
                .andExpect(jsonPath("$.acknowledgedAt").isNotEmpty());
    }

    @Test
    @DisplayName("ACKNOWLEDGED → RESOLVED로 resolve하면 200을 반환한다")
    void resolveSuccess() throws Exception {
        String token = signupAndLogin("알람농부-처리완료");
        long farmId = createFarm(token, "처리완료농장");
        long eventId = saveEvent(farmId, "INDOOR_TEMP_HIGH", LocalDateTime.now()).getId();
        mockMvc.perform(patch("/api/farms/" + farmId + "/alarm-events/" + eventId + "/acknowledge")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/farms/" + farmId + "/alarm-events/" + eventId + "/resolve")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("RESOLVED"));
    }

    @Test
    @DisplayName("상태전이 가드: RESOLVED 이벤트에 acknowledge를 시도하면 409 AL002를 반환한다")
    void acknowledgeOnResolvedConflict() throws Exception {
        String token = signupAndLogin("알람농부-가드");
        long farmId = createFarm(token, "가드농장");
        long eventId = saveEvent(farmId, "INDOOR_TEMP_HIGH", LocalDateTime.now()).getId();
        mockMvc.perform(patch("/api/farms/" + farmId + "/alarm-events/" + eventId + "/acknowledge")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/farms/" + farmId + "/alarm-events/" + eventId + "/resolve")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());

        mockMvc.perform(patch("/api/farms/" + farmId + "/alarm-events/" + eventId + "/acknowledge")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("AL002"));
    }

    @Test
    @DisplayName("상태전이 가드: UNACKNOWLEDGED 이벤트에 resolve를 시도하면(확인 건너뛰기) 409 AL002를 반환한다")
    void resolveWithoutAcknowledgeConflict() throws Exception {
        String token = signupAndLogin("알람농부-가드2");
        long farmId = createFarm(token, "가드농장2");
        long eventId = saveEvent(farmId, "INDOOR_TEMP_HIGH", LocalDateTime.now()).getId();

        mockMvc.perform(post("/api/farms/" + farmId + "/alarm-events/" + eventId + "/resolve")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("AL002"));
    }

    @Test
    @DisplayName("acknowledge-all은 UNACKNOWLEDGED 전체를 ACKNOWLEDGED로 일괄 전이하고 처리 건수를 반환한다")
    void acknowledgeAllSuccess() throws Exception {
        String token = signupAndLogin("알람농부-전체확인");
        long farmId = createFarm(token, "전체확인농장");
        long e1 = saveEvent(farmId, "INDOOR_TEMP_HIGH", LocalDateTime.now()).getId();
        long e2 = saveEvent(farmId, "INDOOR_TEMP_LOW", LocalDateTime.now()).getId();
        // 이미 ACKNOWLEDGED인 이벤트는 acknowledge-all 대상에서 제외된다.
        long e3 = saveEvent(farmId, "INDOOR_HUMIDITY_HIGH", LocalDateTime.now()).getId();
        mockMvc.perform(patch("/api/farms/" + farmId + "/alarm-events/" + e3 + "/acknowledge")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/farms/" + farmId + "/alarm-events/acknowledge-all")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.acknowledgedCount").value(2));

        mockMvc.perform(get("/api/farms/" + farmId + "/alarm-events/" + e1)
                        .header("Authorization", "Bearer " + token))
                .andExpect(jsonPath("$.event.status").value("ACKNOWLEDGED"));
        mockMvc.perform(get("/api/farms/" + farmId + "/alarm-events/" + e2)
                        .header("Authorization", "Bearer " + token))
                .andExpect(jsonPath("$.event.status").value("ACKNOWLEDGED"));
    }

    @Test
    @DisplayName("P3: 동시 acknowledge 요청 중 하나는 200, 다른 하나는 409(AL002 또는 C005)를 반환한다 —"
            + " 500이 되면 안 된다(GlobalExceptionHandler#handleOptimisticLockingFailure의 실제 HTTP"
            + " 라운드트립 검증 — 서비스 레벨 테스트는 예외 발생만 검증해 핸들러 자체를 삭제해도"
            + " 초록으로 남는 공백이 있었다. ControlConcurrencyTest 선례와 동일하게 CountDownLatch로"
            + " 실제 스레드를 동시 출발시켜 진짜 레이스를 재현한다 — MockMvc 호출을 한 스레드에서"
            + " 순차 실행하면 경합이 재현되지 않는다). 두 요청의 SELECT가 완전히 겹치면 버전 충돌"
            + "(C005)로, 한쪽이 이미 커밋을 마친 뒤 다른 쪽이 뒤늦게 읽으면 상태 가드(AL002)로 거부"
            + "된다 — 타이밍에 따라 둘 다 가능하고 실측(로컬 반복 실행)으로도 둘 다 관찰됨. 어느 쪽"
            + "이든 500이 아닌 409라는 게 핵심이며, C005 핸들러가 삭제되면 버전 충돌로 겹친 실행에서"
            + "는 500이 돼 이 테스트가 잡아낸다.")
    void concurrentAcknowledgeReturnsOneSuccessAndOneOptimisticLockConflict() throws Exception {
        String token = signupAndLogin("알람농부-동시성");
        long farmId = createFarm(token, "동시성농장");
        long eventId = saveEvent(farmId, "INDOOR_TEMP_HIGH", LocalDateTime.now()).getId();
        String url = "/api/farms/" + farmId + "/alarm-events/" + eventId + "/acknowledge";

        int threadCount = 2;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(threadCount);
        List<Integer> statuses = new CopyOnWriteArrayList<>();
        List<String> conflictCodes = new CopyOnWriteArrayList<>();

        for (int i = 0; i < threadCount; i++) {
            executor.submit(() -> {
                try {
                    startLatch.await();
                    MvcResult result = mockMvc.perform(patch(url).header("Authorization", "Bearer " + token))
                            .andReturn();
                    int httpStatus = result.getResponse().getStatus();
                    statuses.add(httpStatus);
                    if (httpStatus != 200) {
                        conflictCodes.add(readJson(result).get("code").asText());
                    }
                } catch (Exception e) {
                    throw new RuntimeException(e);
                } finally {
                    doneLatch.countDown();
                }
            });
        }
        startLatch.countDown();
        assertThat(doneLatch.await(30, TimeUnit.SECONDS)).isTrue();
        executor.shutdownNow();

        assertThat(statuses).containsExactlyInAnyOrder(200, 409);
        assertThat(conflictCodes).hasSize(1);
        assertThat(conflictCodes.get(0)).isIn("C005", "AL002");
    }

    // ── 메모 ──────────────────────────────────────────────────────

    @Test
    @DisplayName("메모 추가는 상태를 바꾸지 않고 타임라인에만 기록한다")
    void addMemoDoesNotChangeStatus() throws Exception {
        String token = signupAndLogin("알람농부-메모");
        long farmId = createFarm(token, "메모농장");
        long eventId = saveEvent(farmId, "INDOOR_TEMP_HIGH", LocalDateTime.now()).getId();

        mockMvc.perform(post("/api/farms/" + farmId + "/alarm-events/" + eventId + "/memo")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new AlarmMemoRequest("점검 예정"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.event.status").value("UNACKNOWLEDGED"))
                .andExpect(jsonPath("$.timeline.length()").value(1));
    }

    @Test
    @DisplayName("메모 내용이 비어 있으면 400 C001을 반환한다")
    void addMemoBlankValidationFailure() throws Exception {
        String token = signupAndLogin("알람농부-메모검증");
        long farmId = createFarm(token, "메모검증농장");
        long eventId = saveEvent(farmId, "INDOOR_TEMP_HIGH", LocalDateTime.now()).getId();

        mockMvc.perform(post("/api/farms/" + farmId + "/alarm-events/" + eventId + "/memo")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new AlarmMemoRequest("   "))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("C001"));
    }

    // ── 통계·미확인건수 ────────────────────────────────────────────

    @Test
    @DisplayName("stats는 severity별 건수와 평균 확인 소요시간(분)을 반환한다")
    void statsReturnsCountsAndAverage() throws Exception {
        String token = signupAndLogin("알람농부-통계");
        long farmId = createFarm(token, "통계농장");
        long eventId = saveEvent(farmId, "INDOOR_TEMP_HIGH", LocalDateTime.now().minusMinutes(30)).getId();
        saveEvent(farmId, "INDOOR_TEMP_LOW", LocalDateTime.now());

        mockMvc.perform(patch("/api/farms/" + farmId + "/alarm-events/" + eventId + "/acknowledge")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/farms/" + farmId + "/alarm-events/stats")
                        .param("days", "7")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.days").value(7))
                .andExpect(jsonPath("$.countBySeverity.WARNING").value(2))
                .andExpect(jsonPath("$.avgAcknowledgeMinutes").isNotEmpty());
    }

    @Test
    @DisplayName("P2-C: stats days=0은 하한(1) 미달로 400 C001을 반환한다"
            + "(음수·0은 since가 미래라 조용히 빈 통계가 되므로 서버 검증 필요)")
    void statsDaysBelowMinimumReturnsBadRequest() throws Exception {
        String token = signupAndLogin("알람농부-통계하한");
        long farmId = createFarm(token, "통계하한농장");

        mockMvc.perform(get("/api/farms/" + farmId + "/alarm-events/stats")
                        .param("days", "0")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("C001"));
    }

    @Test
    @DisplayName("P2-C: stats days=999는 상한(90) 초과로 400 C001을 반환한다"
            + "(극단값은 LocalDateTime.now().minusDays(days)에서 DateTimeException → 미처리 500을 유발할 수 있음)")
    void statsDaysAboveMaximumReturnsBadRequest() throws Exception {
        String token = signupAndLogin("알람농부-통계상한");
        long farmId = createFarm(token, "통계상한농장");

        mockMvc.perform(get("/api/farms/" + farmId + "/alarm-events/stats")
                        .param("days", "999")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("C001"));
    }

    @Test
    @DisplayName("unacknowledged-count는 UNACKNOWLEDGED 상태 건수만 반환한다")
    void unacknowledgedCountReturnsOnlyUnacknowledged() throws Exception {
        String token = signupAndLogin("알람농부-배지");
        long farmId = createFarm(token, "배지농장");
        saveEvent(farmId, "INDOOR_TEMP_HIGH", LocalDateTime.now());
        long acknowledgedId = saveEvent(farmId, "INDOOR_TEMP_LOW", LocalDateTime.now()).getId();
        mockMvc.perform(patch("/api/farms/" + farmId + "/alarm-events/" + acknowledgedId + "/acknowledge")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/farms/" + farmId + "/alarm-events/unacknowledged-count")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.count").value(1));
    }
}
