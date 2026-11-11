package com.zdmj.aiService.api;

import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ModelRequestImmutabilityTest {
    @Test
    void requestCopiesVariablesAndPreventsMutation() {
        Map<String, Object> original = new HashMap<>();
        original.put("question", "原始问题");
        original.put("optional", null);
        var text = new ModelRequest("消息", "system", original);
        original.clear();
        Map<String, Object> variables = text.promptVars();
        assertEquals("原始问题", variables.get("question"));
        assertTrue(variables.containsKey("optional"));
        assertNull(variables.get("optional"));
        assertThrows(UnsupportedOperationException.class, () -> variables.put("question", "修改"));
    }

    @Test
    void nullVariablesRemainNull() {
        assertNull(new ModelRequest("消息", "system", null).promptVars());
    }
}
