package com.zdmj.common.async;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zdmj.careerReportService.dto.CareerReportGenerateRequest;
import com.zdmj.resumeService.dto.CapabilityProfileGenerateRequest;

class AsyncTaskPayloadsTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void tree_blank_shouldThrow() {
        assertThrows(IllegalArgumentException.class, () -> AsyncTaskPayloads.tree("  ", mapper));
    }

    @Test
    void tree_invalidJson_shouldThrow() {
        assertThrows(IllegalArgumentException.class, () -> AsyncTaskPayloads.tree("{", mapper));
    }

    @Test
    void requireId_missing_shouldThrow() throws Exception {
        JsonNode node = mapper.readTree("{\"foo\":1}");
        assertThrows(IllegalArgumentException.class, () -> AsyncTaskPayloads.requireId(node, "jobId"));
    }

    @Test
    void requireId_nonPositive_shouldThrow() throws Exception {
        JsonNode node = mapper.readTree("{\"jobId\":0}");
        assertThrows(IllegalArgumentException.class, () -> AsyncTaskPayloads.requireId(node, "jobId"));
    }

    @Test
    void requireId_numeric_shouldReturn() throws Exception {
        JsonNode node = mapper.readTree("{\"jobId\":8}");
        assertEquals(8L, AsyncTaskPayloads.requireId(node, "jobId"));
    }

    @Test
    void read_shouldBindRequest() {
        CapabilityProfileGenerateRequest req = AsyncTaskPayloads.read(
                "{\"rawText\":\"hello\"}", CapabilityProfileGenerateRequest.class, mapper);
        assertEquals("hello", req.getRawText());
    }

    @Test
    void convertRemoving_shouldDropPathId() throws Exception {
        JsonNode node = mapper.readTree("{\"jobId\":3,\"userPreference\":\"上海\"}");
        CareerReportGenerateRequest req = AsyncTaskPayloads.convertRemoving(
                node, CareerReportGenerateRequest.class, mapper, "jobId");
        assertEquals("上海", req.getUserPreference());
        assertTrue(node.has("jobId"));
    }
}
