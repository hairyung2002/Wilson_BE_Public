package com.piuda.wilson.integration;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willAnswer;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.piuda.wilson.domain.auth.repository.LoginAttemptRepository;
import com.piuda.wilson.domain.auth.repository.RefreshTokenRepository;
import com.piuda.wilson.domain.auth.repository.TokenRevocationRepository;
import com.piuda.wilson.domain.caregiver.repository.InviteCodeRepository;
import com.piuda.wilson.domain.conversation.entity.Conversation;
import com.piuda.wilson.domain.conversation.entity.ConversationChannel;
import com.piuda.wilson.domain.conversation.entity.ConversationType;
import com.piuda.wilson.domain.conversation.entity.Message;
import com.piuda.wilson.domain.conversation.entity.MessageSender;
import com.piuda.wilson.domain.conversation.repository.ConversationRepository;
import com.piuda.wilson.domain.conversation.repository.MessageRepository;
import com.piuda.wilson.domain.elderly.repository.ElderlyRepository;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;

/** 결정 B4·B1: 등록 시 동의를 받고, 보호자는 원문 공유 동의가 있을 때만 대화 원문을 본다. */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class ConsentFlowIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ElderlyRepository elderlyRepository;

    @Autowired
    private ConversationRepository conversationRepository;

    @Autowired
    private MessageRepository messageRepository;

    @MockitoBean
    private RefreshTokenRepository refreshTokenRepository;

    @MockitoBean
    private TokenRevocationRepository tokenRevocationRepository;

    @MockitoBean
    private LoginAttemptRepository loginAttemptRepository;

    @MockitoBean
    private InviteCodeRepository inviteCodeRepository;

    private final Map<String, Long> codes = new ConcurrentHashMap<>();

    @BeforeEach
    void setUp() {
        given(inviteCodeRepository.issue(anyString(), anyLong(), any()))
                .willAnswer(invocation -> codes.putIfAbsent(invocation.getArgument(0), invocation.getArgument(1)) == null);
        given(inviteCodeRepository.findElderlyId(anyString()))
                .willAnswer(invocation -> Optional.ofNullable(codes.get(invocation.<String>getArgument(0))));
        willAnswer(invocation -> codes.remove(invocation.<String>getArgument(0)))
                .given(inviteCodeRepository).delete(anyString(), anyLong());
    }

    @Test
    @DisplayName("민감정보 동의 없이는 등록할 수 없고, 원문 공유 동의가 생기면 그때부터 보호자가 원문을 본다")
    void consentFlow() throws Exception {
        // given
        String owner = signUpAndLogin("owner@wilson.com");

        // then: 필수 동의가 없으면 등록 불가
        perform(post("/api/v1/elderlies"), owner, """
                {"name":"김철수","birth":"1945-03-11","gender":"MALE","relationship":"아들",
                 "legalBasis":"FAMILY_DELEGATION","consents":[{"type":"TRANSCRIPT_SHARING","agreed":true,"version":1}]}
                """).andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("CS001"));

        // given: 원문 공유 동의 없이 등록하고 대화가 쌓임
        Integer elderlyId = JsonPath.read(perform(post("/api/v1/elderlies"), owner, """
                {"name":"김철수","birth":"1945-03-11","gender":"MALE","relationship":"아들",
                 "legalBasis":"FAMILY_DELEGATION","consents":[{"type":"SENSITIVE_INFO","agreed":true,"version":1},
                 {"type":"TRANSCRIPT_SHARING","agreed":false,"version":1}]}
                """).andExpect(status().isCreated()).andReturn().getResponse().getContentAsString(), "$.data.elderlyId");
        Long conversationId = conversationWithMessages(elderlyId.longValue());

        // then: 원문은 숨겨진다
        perform(get("/api/v1/conversations/" + conversationId), owner, null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.transcriptVisible").value(false))
                .andExpect(jsonPath("$.data.messages.length()").value(0));

        // then: MEMBER는 동의를 바꿀 수 없고, 민감정보 동의는 철회할 수 없다
        String daughter = signUpAndLogin("daughter@wilson.com");
        String code = JsonPath.read(perform(post("/api/v1/elderlies/" + elderlyId + "/invite-code"), owner, null)
                .andReturn().getResponse().getContentAsString(), "$.data.inviteCode");
        perform(post("/api/v1/care-relationships"), daughter, """
                {"inviteCode":"%s","relation":"딸"}
                """.formatted(code)).andExpect(status().isCreated());
        perform(put("/api/v1/elderlies/" + elderlyId + "/consents/TRANSCRIPT_SHARING"), daughter, """
                {"agreed":true,"version":1,"legalBasis":"FAMILY_DELEGATION"}
                """).andExpect(status().isForbidden());
        perform(put("/api/v1/elderlies/" + elderlyId + "/consents/SENSITIVE_INFO"), owner, """
                {"agreed":false,"version":1,"legalBasis":"FAMILY_DELEGATION"}
                """).andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("CS002"));

        // when: 주보호자가 대리로 원문 공유에 동의
        perform(put("/api/v1/elderlies/" + elderlyId + "/consents/TRANSCRIPT_SHARING"), owner, """
                {"agreed":true,"version":1,"legalBasis":"FAMILY_DELEGATION"}
                """).andExpect(status().isNoContent());

        // then: MEMBER도 원문을 보고, 동의 이력은 최신 상태로 보인다
        perform(get("/api/v1/conversations/" + conversationId), daughter, null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.transcriptVisible").value(true))
                .andExpect(jsonPath("$.data.messages[0].text").value("식사는 하셨어요?"));
        perform(get("/api/v1/elderlies/" + elderlyId + "/consents"), daughter, null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].type").value("TRANSCRIPT_SHARING"))
                .andExpect(jsonPath("$.data[0].agreed").value(true))
                .andExpect(jsonPath("$.data.length()").value(2));
    }

    @Test
    @DisplayName("노인 본인 가입에도 민감정보 처리 동의가 필요하다")
    void elderlySignUp_requiresConsent() throws Exception {
        // when & then
        mockMvc.perform(post("/api/v1/members").contentType(MediaType.APPLICATION_JSON).content("""
                        {"email":"grandma@wilson.com","password":"raw-password","name":"김할머니","memberType":"ELDERLY"}
                        """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("CS001"));
        // 테스트 전체가 한 트랜잭션이라 위 실패한 가입의 회원 행이 보이므로 다른 이메일을 쓴다 (실제 요청은 롤백된다)
        mockMvc.perform(post("/api/v1/members").contentType(MediaType.APPLICATION_JSON).content("""
                        {"email":"grandma2@wilson.com","password":"raw-password","name":"김할머니","memberType":"ELDERLY",
                         "consents":[{"type":"SENSITIVE_INFO","agreed":true,"version":1}]}
                        """))
                .andExpect(status().isCreated());
    }

    private Long conversationWithMessages(Long elderlyId) {
        Conversation conversation = conversationRepository.save(Conversation.builder()
                .elderly(elderlyRepository.findById(elderlyId).orElseThrow())
                .channel(ConversationChannel.VOICE).type(ConversationType.MEAL)
                .startedAt(LocalDateTime.now()).build());
        messageRepository.save(Message.builder().conversation(conversation).sender(MessageSender.AI)
                .content("식사는 하셨어요?").turnId("t-1").build());
        messageRepository.save(Message.builder().conversation(conversation).sender(MessageSender.ELDERLY)
                .content("응 먹었어").turnId("t-1").build());
        return conversation.getId();
    }

    private ResultActions perform(MockHttpServletRequestBuilder request, String token, String body) throws Exception {
        request.header(HttpHeaders.AUTHORIZATION, "Bearer " + token);
        if (body != null) {
            request.contentType(MediaType.APPLICATION_JSON).content(body);
        }
        return mockMvc.perform(request);
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
