package com.zdmj.common.typehandler;

import org.junit.jupiter.api.Test;
import org.postgresql.util.PGobject;

import java.sql.ResultSet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class JsonbStringTypeHandlerTest {

    private final JsonbStringTypeHandler handler = new JsonbStringTypeHandler();

    @Test
    void getByColumnName_whenString_shouldReturnJsonText() throws Exception {
        ResultSet rs = mock(ResultSet.class);
        when(rs.getObject("content")).thenReturn("[{\"type\":\"开发语言\"}]");

        assertEquals("[{\"type\":\"开发语言\"}]", handler.getNullableResult(rs, "content"));
    }

    @Test
    void getByColumnName_whenPGobject_shouldReturnValue() throws Exception {
        PGobject pg = new PGobject();
        pg.setType("jsonb");
        pg.setValue("{\"score\":85}");
        ResultSet rs = mock(ResultSet.class);
        when(rs.getObject("content")).thenReturn(pg);

        assertEquals("{\"score\":85}", handler.getNullableResult(rs, "content"));
    }

    @Test
    void getByColumnName_whenNull_shouldReturnNull() throws Exception {
        ResultSet rs = mock(ResultSet.class);
        when(rs.getObject("content")).thenReturn(null);

        assertNull(handler.getNullableResult(rs, "content"));
    }
}
