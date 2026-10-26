package com.piuda.wilson.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.piuda.wilson.domain.analysis.entity.CognitiveMetric;
import com.piuda.wilson.domain.analysis.repository.CognitiveMetricRepository;
import com.piuda.wilson.domain.auth.repository.RefreshTokenRepository;
import com.piuda.wilson.domain.auth.repository.TokenRevocationRepository;
import com.piuda.wilson.domain.caregiver.repository.CareRelationshipRepository;
import com.piuda.wilson.domain.conversation.entity.Conversation;
import com.piuda.wilson.domain.conversation.entity.ConversationChannel;
import com.piuda.wilson.domain.conversation.entity.ConversationType;
import com.piuda.wilson.domain.conversation.entity.Message;
import com.piuda.wilson.domain.conversation.entity.MessageSender;
import com.piuda.wilson.domain.conversation.repository.ConversationContextRepository;
import com.piuda.wilson.domain.conversation.repository.ConversationRepository;
import com.piuda.wilson.domain.conversation.repository.MessageRepository;
import com.piuda.wilson.domain.elderly.entity.Elderly;
import com.piuda.wilson.domain.elderly.repository.ConsentRepository;
import com.piuda.wilson.domain.elderly.repository.ElderlyRepository;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

/** 결정 B5: 노인을 삭제하면 실명·원문·연결·동의는 파기되고, 인지지표는 연구 보관 동의가 있을 때만 가명으로 남는다. */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class ElderlyDeletionIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ElderlyRepository elderlyRepository;

    @Autowired
    private ConversationRepository conversationRepository;

    @Autowired
    private MessageRepository messageRepository;

    @Autowired
    private CareRelationshipRepository careRelationshipRepository;

    @Autowired
    private ConsentRepository consentRepository;

    @Autowired
    private CognitiveMetricRepository cognitiveMetricRepository;

    @MockitoBean
    private RefreshTokenRepository refreshTokenRepository;

    @MockitoBean
    private TokenRevocationRepository tokenRevocationRepository;

    @MockitoBean
    private ConversationContextRepository conversationContextRepository;

    @Test
    @DisplayName("연구 보관 동의가 없으면 실명·원문·연결·동의·지표를 모두 파기한다")
    void erase_withoutResearchRetention() throws Exception {
        // given
        String owner = signUpAndLogin("owner1@wilson.com");
        Long elderlyId = register(owner, false);
        Elderly elderly = elderlyRepository.findById(elderlyId).orElseThrow();
        UUID subjectId = elderly.getSubjectId();
        Long conversationId = conversationWithMessage(elderly);
        metric(subjectId);

        // when
        mockMvc.perform(delete("/api/v1/elderlies/" + elderlyId).header(HttpHeaders.AUTHORIZATION, "Bearer " + owner))
                .andExpect(status().isNoContent());

        // then
        Elderly erased = elderlyRepository.findById(elderlyId).orElseThrow();
        assertThat(erased.isDeleted()).isTrue();
        assertThat(erased.getName()).isEqualTo("삭제된 어르신");
        assertThat(erased.getBirthDate()).isNull();
        assertThat(erased.getSubjectId()).isNotEqualTo(subjectId);
        assertThat(messageRepository.findByConversationIdOrderByCreatedAtAscIdAsc(conversationId)).isEmpty();
        assertThat(conversationRepository.findByIdAndDeletedFalse(conversationId)).isEmpty();
        assertThat(careRelationshipRepository.findByElderlyId(elderlyId)).isEmpty();
        assertThat(consentRepository.findByElderlyIdOrderByIdDesc(elderlyId)).isEmpty();
        assertThat(cognitiveMetricRepository.findAll()).extracting(CognitiveMetric::getSubjectId)
                .doesNotContain(subjectId);
    }

    @Test
    @DisplayName("연구 보관 동의가 있으면 지표는 남기되 노인과의 연결(가명 식별자)은 끊는다")
    void erase_withResearchRetention() throws Exception {
        // given
        String owner = signUpAndLogin("owner2@wilson.com");
        Long elderlyId = register(owner, true);
        UUID subjectId = elderlyRepository.findById(elderlyId).orElseThrow().getSubjectId();
        metric(subjectId);

        // when
        mockMvc.perform(delete("/api/v1/elderlies/" + elderlyId).header(HttpHeaders.AUTHORIZATION, "Bearer " + owner))
                .andExpect(status().isNoContent());

        // then
        assertThat(cognitiveMetricRepository.findAll()).extracting(CognitiveMetric::getSubjectId).contains(subjectId);
        assertThat(elderlyRepository.findById(elderlyId).orElseThrow().getSubjectId()).isNotEqualTo(subjectId);
    }

    private Long register(String token, boolean researchRetention) throws Exception {
        String body = """
                {"name":"김철수","birth":"1945-03-11","gender":"MALE","relationship":"아들","legalBasis":"FAMILY_DELEGATION",
                 "consents":[{"type":"SENSITIVE_INFO","agreed":true,"version":1},
                             {"type":"RESEARCH_RETENTION","agreed":%s,"version":1}]}
                """.formatted(researchRetention);
        Integer id = JsonPath.read(mockMvc.perform(post("/api/v1/elderlies")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString(), "$.data.elderlyId");
        return id.longValue();
    }

    private Long conversationWithMessage(Elderly elderly) {
        Conversation conversation = conversationRepository.save(Conversation.builder().elderly(elderly)
                .channel(ConversationChannel.VOICE).type(ConversationType.DAILY).startedAt(LocalDateTime.now()).build());
        messageRepository.save(Message.builder().conversation(conversation).sender(MessageSender.ELDERLY)
                .content("우리 아들 이름은 김민수야").turnId("t-1").build());
        return conversation.getId();
    }

    private void metric(UUID subjectId) {
        cognitiveMetricRepository.save(CognitiveMetric.builder().turnId(UUID.randomUUID().toString())
                .subjectId(subjectId).analyzable(false).analyzedAt(Instant.now()).build());
    }

    private String signUpAndLogin(String email) throws Exception {
        mockMvc.perform(post("/api/v1/members").contentType(MediaType.APPLICATION_JSON).content("""
                        {"email":"%s","password":"raw-password","name":"보호자","memberType":"CAREGIVER"}
                        """.formatted(email)))
                .andExpect(status().isCreated());
        return JsonPath.read(mockMvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s","password":"raw-password"}
                                """.formatted(email)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(), "$.data.accessToken");
    }
}
