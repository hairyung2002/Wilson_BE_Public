package com.piuda.wilson.integration;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

/** 결정 I1: K8s liveness·readiness 프로브는 인증 없이 열고, 그 밖의 actuator는 열지 않는다. */
@SpringBootTest
@AutoConfigureMockMvc
class HealthProbeIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    @DisplayName("liveness·readiness 프로브는 인증 없이 UP을 돌려준다")
    void probes() throws Exception {
        // when & then
        mockMvc.perform(get("/actuator/health/liveness"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));
        mockMvc.perform(get("/actuator/health/readiness"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));
    }

    @Test
    @DisplayName("프로브가 아닌 actuator 경로는 인증 없이 볼 수 없다")
    void otherEndpointsClosed() throws Exception {
        // when & then
        mockMvc.perform(get("/actuator/health")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/actuator/env")).andExpect(status().isUnauthorized());
    }
}
