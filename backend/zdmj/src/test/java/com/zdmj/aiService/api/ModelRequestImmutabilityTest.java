package com.zdmj.aiService.api;

import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ModelRequestImmutabilityTest {
    @Test
    void requestsCopyVariablesAndPreventMutation() {
        Map<String, Object> original = new HashMap<>();
        original.put("question", "原始问题");
        original.put("optional", null);
        var text = new ModelRequest("消息", "system", original);
        var structured = StructuredModelRequest.of("消息", "system", original, String.class);
        var stream = new StreamingModelRequest(1L, "消息", "system", original);
        original.clear();
        for (Map<String, Object> variables : java.util.List.of(text.promptVars(), structured.promptVars(), stream.promptVars())) {
            assertEquals("原始问题", variables.get("question"));
            assertTrue(variables.containsKey("optional"));
            assertNull(variables.get("optional"));
            assertThrows(UnsupportedOperationException.class, () -> variables.put("question", "修改"));
        }
    }

    @Test
    void nullVariablesRemainNull() {
        assertNull(new ModelRequest("消息", "system", null).promptVars());
        assertNull(StructuredModelRequest.of("消息", "system", null, String.class).promptVars());
        assertNull(new StreamingModelRequest(1L, "消息", "system", null).promptVars());
    }
}
