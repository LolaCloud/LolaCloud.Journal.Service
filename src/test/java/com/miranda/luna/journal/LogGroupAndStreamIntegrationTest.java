package com.miranda.luna.journal;

import com.miranda.luna.journal.domain.repository.LogGroupRepository;
import com.miranda.luna.journal.infrastructure.web.dto.CreateLogGroupRequest;
import com.miranda.luna.journal.infrastructure.web.dto.IngestLogEventRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import tools.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@ActiveProfiles("test")
@TestPropertySource(locations = "classpath:application-test.properties")
class LogGroupAndStreamIntegrationTest {

    private static final String TEST_GROUP = "/test/application-alpha";

    @Autowired
    private WebApplicationContext webApplicationContext;

    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private LogGroupRepository logGroupRepository;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext)
                .apply(springSecurity())
                .build();
        cleanupTestGroup();
    }

    @AfterEach
    void tearDown() {
        cleanupTestGroup();
    }

    private void cleanupTestGroup() {
        logGroupRepository.findByName(TEST_GROUP).ifPresent(logGroupRepository::delete);
    }

    @Test
    void testSecurityUnauthorizedWithoutToken() throws Exception {
        mockMvc.perform(get("/api/v1/journal/groups"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.data.code").value("UNAUTHORIZED"));
    }

    @Test
    void testSecurityUnauthorizedWithInvalidToken() throws Exception {
        mockMvc.perform(get("/api/v1/journal/groups")
                        .header("Authorization", "Bearer invalid-token"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.data.code").value("UNAUTHORIZED"));
    }

    @Test
    void testSecurityForbiddenWithUnprivilegedToken() throws Exception {
        mockMvc.perform(get("/api/v1/journal/groups")
                        .header("Authorization", "Bearer valid-unauthorized-token"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.data.code").value("FORBIDDEN"));
    }

    @Test
    void testAutoSeededGroupsArePresent() throws Exception {
        mockMvc.perform(get("/api/v1/journal/groups")
                        .header("Authorization", "Bearer valid-journal-token"))
                .andExpect(status().isOk())
                .andExpect(header().exists("X-Request-ID"))
                .andExpect(jsonPath("$.data").isArray());
    }

    @Test
    void testCompleteLogLifecycle() throws Exception {
        // 1. Create log group
        CreateLogGroupRequest createRequest = new CreateLogGroupRequest(
                TEST_GROUP,
                "Test application log group for verification",
                14
        );

        mockMvc.perform(post("/api/v1/journal/groups")
                        .header("Authorization", "Bearer valid-journal-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createRequest)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.name").value(TEST_GROUP))
                .andExpect(jsonPath("$.data.retentionDays").value(14))
                .andExpect(jsonPath("$.data.streamCount").value(0));

        // 2. Get group
        mockMvc.perform(get("/api/v1/journal/groups" + TEST_GROUP)
                        .header("Authorization", "Bearer valid-journal-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.name").value(TEST_GROUP))
                .andExpect(jsonPath("$.data.retentionDays").value(14));

        // 3. Ingest log event into stream 'api-server'
        IngestLogEventRequest ingestReq = new IngestLogEventRequest(
                "INFO",
                "User 'alice' logged in successfully from 192.168.1.100",
                Map.of("user", "alice", "ip", "192.168.1.100", "requestId", "req-1234"),
                Instant.now()
        );

        mockMvc.perform(post("/api/v1/journal/groups" + TEST_GROUP + "/streams/api-server/events")
                        .header("Authorization", "Bearer valid-journal-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(ingestReq)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.streamName").value("api-server"))
                .andExpect(jsonPath("$.data.level").value("INFO"))
                .andExpect(jsonPath("$.data.message").value("User 'alice' logged in successfully from 192.168.1.100"))
                .andExpect(jsonPath("$.data.attributes.user").value("alice"));

        // 4. Ingest second event into stream 'api-server'
        IngestLogEventRequest ingestReq2 = new IngestLogEventRequest(
                "WARN",
                "Database query latency high: 450ms",
                Map.of("durationMs", 450, "query", "SELECT * FROM orders"),
                Instant.now()
        );

        mockMvc.perform(post("/api/v1/journal/groups" + TEST_GROUP + "/streams/api-server/events")
                        .header("Authorization", "Bearer valid-journal-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(ingestReq2)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.level").value("WARN"));

        // 5. List streams for group
        mockMvc.perform(get("/api/v1/journal/groups" + TEST_GROUP + "/streams")
                        .header("Authorization", "Bearer valid-journal-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data[0].name").value("api-server"));

        // 6. Query stream events
        mockMvc.perform(get("/api/v1/journal/groups" + TEST_GROUP + "/streams/api-server/events")
                        .header("Authorization", "Bearer valid-journal-token")
                        .param("query", "alice"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content").isArray())
                .andExpect(jsonPath("$.data.content[0].message").value("User 'alice' logged in successfully from 192.168.1.100"));

        // 7. Delete group
        mockMvc.perform(delete("/api/v1/journal/groups" + TEST_GROUP)
                        .header("Authorization", "Bearer valid-journal-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.message").value("Log group deleted successfully"));

        // 8. Verify group is removed
        mockMvc.perform(get("/api/v1/journal/groups" + TEST_GROUP)
                        .header("Authorization", "Bearer valid-journal-token"))
                .andExpect(status().isNotFound());
    }

    @Test
    void testLiveTailEndpointStartsStreaming() throws Exception {
        MvcResult mvcResult = mockMvc.perform(get("/api/v1/journal/tail")
                        .header("Authorization", "Bearer valid-journal-token")
                        .param("group", "/lola/relate/instances")
                        .param("stream", "db-main"))
                .andExpect(request().asyncStarted())
                .andReturn();

        assertTrue(mvcResult.getRequest().isAsyncStarted());
    }
}
