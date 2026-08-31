package com.zdmj.common.typehandler;

import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import com.zdmj.jobService.entity.Job;
import com.zdmj.resumeService.entity.Resume;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

class JacksonTypeHandlerGenericTest {

    @Test
    @SuppressWarnings("unchecked")
    void parse_listLong_shouldKeepLongElements() throws Exception {
        Field field = Resume.class.getDeclaredField("projects");
        JacksonTypeHandler handler = new JacksonTypeHandler(List.class, field);

        List<Long> parsed = (List<Long>) handler.parse("[1,2,3]");

        assertEquals(List.of(1L, 2L, 3L), parsed);
        assertInstanceOf(Long.class, parsed.getFirst());
    }

    @Test
    @SuppressWarnings("unchecked")
    void parse_listString_shouldKeepStringElements() throws Exception {
        Field field = Job.class.getDeclaredField("keywords");
        JacksonTypeHandler handler = new JacksonTypeHandler(List.class, field);

        List<String> parsed = (List<String>) handler.parse("[\"Java\",\"MySQL\"]");

        assertEquals(List.of("Java", "MySQL"), parsed);
    }
}
