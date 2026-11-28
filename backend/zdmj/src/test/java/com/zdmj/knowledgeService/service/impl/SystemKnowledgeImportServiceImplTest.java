package com.zdmj.knowledgeService.service.impl;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.mock;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zdmj.common.exception.BusinessException;
import com.zdmj.knowledgeService.config.SystemKnowledgeImportConfig;
import com.zdmj.knowledgeService.dto.SystemKnowledgeImportRequest;
import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SystemKnowledgeImportServiceImplTest {
    @TempDir Path root;
    private final ObjectMapper json = new ObjectMapper();
    private final String run = "20261127T000000Z-012345abcdef";

    @Test
    void 读取固定提交全文并复制字节数组() throws Exception {
        var request = fixture("docs/java/test.md", "SYSTEM", false);
        var source = service().readSources(request).getFirst();
        assertThat(new String(source.bytes(), java.nio.charset.StandardCharsets.UTF_8)).isEqualTo("# Java\n知识内容\n");
        byte[] changed = source.bytes(); changed[0] = 0;
        assertThat(source.bytes()[0]).isEqualTo((byte) '#');
    }

    @Test
    void 拒绝错误摘要及运行编号() throws Exception {
        var request = fixture("docs/java/test.md", "SYSTEM", false);
        assertThatThrownBy(() -> service().readSources(new SystemKnowledgeImportRequest(run, "0".repeat(64))))
                .isInstanceOf(BusinessException.class).hasMessageContaining("清单摘要");
        assertThatThrownBy(() -> new SystemKnowledgeImportRequest("../test", request.manifestHash()))
                .isInstanceOf(IllegalArgumentException.class);
        Files.writeString(root.resolve("versions").resolve(run).resolve("docs/java/test.md"), "被修改的内容");
        assertThatThrownBy(() -> service().readSources(request)).hasMessageContaining("摘要不一致");
    }

    @Test
    void 拒绝越界路径错误范围及符号链接() throws Exception {
        for (String path : List.of("docs/../../private.md", "docs/./test.md", "docs//test.md", "docs\\test.md", "/docs/test.md")) {
            var request = fixture(path, "SYSTEM", false);
            assertThatThrownBy(() -> service().readSources(request)).isInstanceOf(BusinessException.class);
        }
        var wrongScope = fixture("docs/java/test.md", "USER", false);
        assertThatThrownBy(() -> service().readSources(wrongScope)).hasMessageContaining("范围非法");
        var symlink = fixture("docs/java/test.md", "SYSTEM", true);
        assertThatThrownBy(() -> service().readSources(symlink)).hasMessageContaining("普通文件");
    }

    @Test
    void 拒绝目录符号链接及非当前版本() throws Exception {
        var request = fixture("docs/java/test.md", "SYSTEM", false);
        Path dir = root.resolve("versions").resolve(run);
        Path docs = dir.resolve("docs");
        Files.move(docs, dir.resolve("other"));
        Files.createSymbolicLink(docs, Path.of("other"));
        assertThatThrownBy(() -> service().readSources(request)).hasMessageContaining("目录含符号链接");
        Files.delete(root.resolve("current"));
        Files.createSymbolicLink(root.resolve("current"), Path.of("versions/missing"));
        assertThatThrownBy(() -> service().readSources(request)).isInstanceOf(BusinessException.class);
    }

    private SystemKnowledgeImportServiceImpl service() throws Exception {
        return new SystemKnowledgeImportServiceImpl(new SystemKnowledgeImportConfig(root.toRealPath().toString()), json,
                mock(com.zdmj.common.util.PdfParserUtil.class), new com.zdmj.knowledgeService.config.KnowledgeDocumentConfig(1_000_000),
                mock(com.zdmj.aiService.api.EmbeddingGateway.class), mock(com.zdmj.knowledgeService.mapper.KnowledgeDocumentMapper.class),
                mock(com.zdmj.knowledgeService.mapper.KnowledgeVectorMapper.class), mock(org.springframework.ai.transformer.splitter.TextSplitter.class),
                mock(org.springframework.transaction.support.TransactionTemplate.class), java.time.Clock.systemUTC());
    }

    private SystemKnowledgeImportRequest fixture(String path, String scope, boolean symlink) throws Exception {
        Path dir = root.resolve("versions").resolve(run);
        Files.createDirectories(dir.resolve("docs/java"));
        Path file = dir.resolve("docs/java/test.md");
        Files.deleteIfExists(file);
        Files.writeString(file, "# Java\n知识内容\n");
        String hash = SystemKnowledgeImportServiceImpl.hash(Files.readAllBytes(file));
        if (symlink) { Files.move(file, dir.resolve("original.md")); Files.createSymbolicLink(file, Path.of("../../original.md")); }
        String commit = "a".repeat(40), repo = "https://github.com/Snailclimb/JavaGuide";
        var doc = Map.of("source_path", path, "output_path", path, "scope", scope, "title", "Java",
                "source_url", repo + "/blob/" + commit + "/" + path, "content_hash", hash);
        byte[] manifest = json.writeValueAsBytes(Map.of("repository", repo, "source_commit", commit,
                "hash_algorithm", "SHA-256", "rules_hash", "b".repeat(64), "cleaner_hash", "c".repeat(64), "documents", List.of(doc)));
        Files.write(dir.resolve("manifest.json"), manifest);
        Files.deleteIfExists(root.resolve("current"));
        Files.createSymbolicLink(root.resolve("current"), Path.of("versions", run));
        return new SystemKnowledgeImportRequest(run, SystemKnowledgeImportServiceImpl.hash(manifest));
    }
}
