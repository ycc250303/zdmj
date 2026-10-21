package com.zdmj.common.prompt;

import java.io.IOException;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.DefaultResourceLoader;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class PromptLoaderTest {
    @Test
    void readsAndCachesUtf8Content() {
        var loader = new PromptLoader(new DefaultResourceLoader());
        String content = loader.load("job-detect");
        assertTrue(content.contains("岗位分类器"));
        assertSame(content, loader.load("job-detect"));
    }

    @Test
    void rejectsInvalidPathsAndMissingResources() {
        var loader = new PromptLoader(new DefaultResourceLoader());
        for (String path : new String[]{"", "/system", "../system", "a/../system", "https://example.com", "file:/system", "a\\system", "system.md"}) {
            assertThrows(IllegalArgumentException.class, () -> loader.load(path));
        }
        assertThrows(IllegalArgumentException.class, () -> loader.load(null));
        assertThrows(IllegalArgumentException.class, () -> loader.load("missing-resource"));
    }

    @Test
    void readFailurePropagates() throws Exception {
        ResourceLoader resources = mock(ResourceLoader.class);
        Resource resource = mock(Resource.class);
        when(resources.getResource("classpath:prompts/system.md")).thenReturn(resource);
        when(resource.exists()).thenReturn(true);
        when(resource.getInputStream()).thenThrow(new IOException("读取失败"));
        assertThrows(IllegalStateException.class, () -> new PromptLoader(resources).load("system"));
    }
}
