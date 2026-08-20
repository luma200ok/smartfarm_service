package com.smartfarm.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;

/**
 * 통합 테스트 공통 지원 — Testcontainers PostgreSQL 16 싱글턴 컨테이너.
 * (운영 DB와 동일 엔진으로 Flyway PG16 문법·partial index까지 검증)
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
public abstract class IntegrationTestSupport {

    static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:16-alpine");

    /**
     * image.storage-dir(ImageProperties, contract §3 Phase 3)은 기본값 없이 필수(@Validated)라,
     * 이 값이 없으면 어떤 테스트든 전체 컨텍스트 기동 자체가 실패한다 — 전 통합 테스트 공통 base에서
     * 임시 디렉터리를 주입한다(전 테스트 클래스가 공유 — 컨텍스트 캐싱 유지, farmId가 유니크해 격리됨).
     */
    protected static final Path IMAGE_STORAGE_DIR = createImageStorageDir();

    static {
        POSTGRES.start();
    }

    private static Path createImageStorageDir() {
        try {
            return Files.createTempDirectory("smartfarm-diagnosis-images-test");
        } catch (IOException e) {
            throw new UncheckedIOException("이미지 저장 임시 디렉터리 생성 실패", e);
        }
    }

    @DynamicPropertySource
    static void datasourceProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("image.storage-dir", IMAGE_STORAGE_DIR::toString);
    }

    @Autowired
    protected MockMvc mockMvc;

    @Autowired
    protected ObjectMapper objectMapper;
}
