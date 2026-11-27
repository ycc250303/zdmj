package com.zdmj.aiService.api;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ModelRequestImmutabilityTest {
    @Test
    void requestCopiesHistoryAndVariablesAndPreventsMutation() {
        Map<String, Object> original = new HashMap<>();
        original.put("question", "原始问题");
        original.put("optional", null);
        var history = new ArrayList<ModelHistoryMessage>();
        var message = new ModelHistoryMessage(ModelMessageRole.USER, "任务");
        history.add(message);
        var text = new ModelRequest("消息", "system", original, history, "生成失败");
        original.clear();
        history.clear();
        Map<String, Object> variables = text.promptVars();
        assertEquals("原始问题", variables.get("question"));
        assertTrue(variables.containsKey("optional"));
        assertNull(variables.get("optional"));
        assertThrows(UnsupportedOperationException.class, () -> variables.put("question", "修改"));
        assertEquals(List.of(message), text.history());
        assertThrows(UnsupportedOperationException.class, () -> text.history().clear());
    }

    @Test
    void nullVariablesRemainNull() {
        var request = new ModelRequest("消息", "system", null);
        assertNull(request.promptVars());
        assertEquals(List.of(), request.history());
        assertEquals("", request.historyNotice());
    }

    @Test
    void requestRejectsInvalidHistory() {
        assertThrows(NullPointerException.class, () -> new ModelRequest("继续", null, null, null, ""));
        assertThrows(NullPointerException.class, () -> new ModelRequest("继续", null, null, List.of(), null));
        assertThrows(NullPointerException.class, () -> new ModelHistoryMessage(null, "任务"));
        assertThrows(IllegalArgumentException.class,
                () -> new ModelHistoryMessage(ModelMessageRole.USER, " "));
    }
}
