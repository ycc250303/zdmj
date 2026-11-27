package com.zdmj.knowledgeService.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zdmj.knowledgeService.config.SystemKnowledgeImportConfig;
import java.nio.file.*;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import org.springframework.ai.transformer.splitter.TextSplitter;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.zdmj.aiService.api.EmbeddingGateway;
import com.zdmj.common.exception.BusinessException;
import com.zdmj.common.exception.ErrorCode;
import com.zdmj.common.util.PdfParserUtil;
import com.zdmj.knowledgeService.config.KnowledgeDocumentConfig;
import com.zdmj.knowledgeService.dto.SystemKnowledgeImportRequest;
import com.zdmj.knowledgeService.dto.SystemKnowledgeSource;
import com.zdmj.knowledgeService.dto.SystemKnowledgeImportSnapshot;
import com.zdmj.knowledgeService.dto.SystemKnowledgePreparedDocument;
import com.zdmj.knowledgeService.dto.SystemKnowledgeImportResult;
import com.zdmj.knowledgeService.entity.KnowledgeDocument;
import com.zdmj.knowledgeService.entity.KnowledgeVector;
import com.zdmj.knowledgeService.enums.KnowledgeEmbeddingStatusEnum;
import com.zdmj.knowledgeService.enums.KnowledgeScopeEnum;
import com.zdmj.knowledgeService.enums.KnowledgeTypeEnum;
import com.zdmj.knowledgeService.mapper.KnowledgeDocumentMapper;
import com.zdmj.knowledgeService.mapper.KnowledgeVectorMapper;
import com.zdmj.knowledgeService.service.SystemKnowledgeImportService;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.transaction.support.TransactionTemplate;

/** 命令行增量导入；按成功入库摘要识别变化，新增和变更文档的向量准备成功后单事务发布。 */
@Slf4j
@RequiredArgsConstructor
public class SystemKnowledgeImportServiceImpl implements SystemKnowledgeImportService {
    private static final String REPOSITORY = "https://github.com/Snailclimb/JavaGuide";
    private final SystemKnowledgeImportConfig sourceConfig;
    private final ObjectMapper objectMapper;
    private final PdfParserUtil parser;
    private final KnowledgeDocumentConfig documentConfig;
    private final EmbeddingGateway gateway;
    private final KnowledgeDocumentMapper documents;
    private final KnowledgeVectorMapper vectors;
    private final TextSplitter textSplitter;
    private final TransactionTemplate transactions;
    private final Clock clock;
    /**
     * 读取并校验清理全文，对比成功入库摘要，仅生成新增和变更文档的向量，再单事务发布。
     * @param request 运行编号及清单摘要，由服务器更新脚本提供
     * @return 数据库已提交的导入摘要，相同运行编号成功后直接返回已有摘要
     * @throws BusinessException 来源或正文非法、文件不可用或模型处理失败
     */
    @Override
    public SystemKnowledgeImportResult importDocuments(SystemKnowledgeImportRequest request) {
        Objects.requireNonNull(request, "导入参数不能为空");
        long started = System.nanoTime();
        List<SystemKnowledgeSource> sources = readSources(request);
        SystemKnowledgeImportSnapshot baseline = snapshot(false);
        SystemKnowledgeImportResult existing = findCommitted(request, sources, baseline);
        if (existing != null) { return existing; }
        List<SystemKnowledgePreparedDocument> prepared = new ArrayList<>();
        for (var source : sources) {
            if (Thread.currentThread().isInterrupted()) { throw new IllegalStateException("系统导入已中断"); }
            KnowledgeDocument previous = baseline.documents().get("javaguide:" + source.path());
            if (previous != null && source.hash().equals(previous.getContentHash())
                    && Objects.equals(previous.getFileStatus(), KnowledgeDocument.FileStatus.READY.code())
                    && Objects.equals(previous.getEmbeddingStatus(), KnowledgeEmbeddingStatusEnum.SUCCESS.getCode())) {
                prepared.add(new SystemKnowledgePreparedDocument(source, List.of()));
                continue;
            }
            try (var input = new ByteArrayInputStream(source.bytes())) {
                String text = parser.extractText(input, documentConfig.maxExtractedChars());
                prepared.add(new SystemKnowledgePreparedDocument(source, KnowledgeEmbeddingServiceImpl.generateVectors(textSplitter, text, gateway::embedSystem)));
            } catch (IOException exception) {
                throw new BusinessException(ErrorCode.FILE_STORAGE_UNAVAILABLE, "系统资料解析读取失败", exception);
            }
        }
        SystemKnowledgeImportResult result = Objects.requireNonNull(transactions.execute(status -> publish(request, prepared, baseline)));
        int added = 0, updated = 0, generated = 0;
        Set<String> retained = new HashSet<>();
        for (SystemKnowledgePreparedDocument item : prepared) {
            retained.add("javaguide:" + item.source().path());
            if (item.rows().isEmpty()) { continue; }
            KnowledgeDocument previous = baseline.documents().get("javaguide:" + item.source().path());
            if (previous == null) { added++; } else { updated++; }
            generated += item.rows().size();
            log.info("系统资料文档发布成功: operationId={}, sourcePath={}, action={}, previousDocumentId={}, removedChunks={}, generatedChunks={}",
                    request.runId(), item.source().path(), previous == null ? "ADDED" : "UPDATED",
                    previous == null ? null : previous.getId(), previous == null ? 0 : baseline.chunkIds().get(previous.getId()).size(), item.rows().size());
        }
        int removed = 0;
        for (KnowledgeDocument obsolete : baseline.documents().values()) {
            if (!retained.contains(obsolete.getContent())) {
                removed++;
                log.info("系统资料文档移除成功: operationId={}, sourcePath={}, documentId={}, removedChunks={}",
                        request.runId(), obsolete.getContent().substring("javaguide:".length()), obsolete.getId(), baseline.chunkIds().get(obsolete.getId()).size());
            }
        }
        log.info("系统资料增量发布成功: operationId={}, documents={}, chunks={}, added={}, updated={}, unchanged={}, removed={}, generatedChunks={}, elapsedMs={}",
                result.runId(), result.documents(), result.chunks(), added, updated, prepared.size() - added - updated,
                removed, generated, (System.nanoTime() - started) / 1_000_000);
        return result;
    }

    /** 从已提交文档确认本运行编号的整批成功结果，不保存进行中状态。 */
    private SystemKnowledgeImportResult findCommitted(SystemKnowledgeImportRequest request,
            List<SystemKnowledgeSource> sources, SystemKnowledgeImportSnapshot baseline) {
        List<KnowledgeDocument> current = List.copyOf(baseline.documents().values());
        List<KnowledgeDocument> matching = current.stream()
                .filter(doc -> request.runId().equals(doc.getMetadata().get("importRunId"))).toList();
        if (matching.isEmpty()) { return null; }
        if (matching.size() != current.size() || current.size() != sources.size() || matching.stream().anyMatch(doc ->
                !Objects.equals(doc.getMetadata().get("manifestHash"), request.manifestHash())
                || !Objects.equals(doc.getEmbeddingStatus(), KnowledgeEmbeddingStatusEnum.SUCCESS.getCode())
                || !Objects.equals(doc.getFileStatus(), KnowledgeDocument.FileStatus.READY.code()))
                || sources.stream().anyMatch(source -> {
                    KnowledgeDocument doc = baseline.documents().get("javaguide:" + source.path());
                    return doc == null || !source.hash().equals(doc.getContentHash());
                })) {
            throw new IllegalStateException("运行编号对应的系统资料提交记录不完整或清单摘要不一致");
        }
        return new SystemKnowledgeImportResult(request.runId(), request.manifestHash(), matching.size(),
                matching.stream().mapToInt(KnowledgeDocument::getChunkCount).sum());
    }

    /** 短事务内锁定并核对比较依据，保留编号及未变化片段，替换变化片段并清理移除文档。 */
    private SystemKnowledgeImportResult publish(SystemKnowledgeImportRequest request, List<SystemKnowledgePreparedDocument> prepared, SystemKnowledgeImportSnapshot baseline) {
        if (!baseline.equals(snapshot(true))) {
            throw new IllegalStateException("系统知识库文档或片段在向量生成期间发生变化，停止本次发布");
        }
        Map<String, KnowledgeDocument> previous = new HashMap<>(baseline.documents());
        LocalDateTime completed = LocalDateTime.now(clock);
        int totalChunks = 0;
        for (SystemKnowledgePreparedDocument item : prepared) {
            var source = item.source();
            String key = "javaguide:" + source.path();
            KnowledgeDocument doc = previous.remove(key);
            Map<String, Object> metadata = Map.of("source", "javaguide", "sourcePath", source.path(),
                    "sourceUrl", source.url(), "sourceCommit", source.commit(), "importRunId", request.runId(),
                    "manifestHash", request.manifestHash(), "docCategory", source.path().split("/")[1]);
            if (doc == null) {
                doc = new KnowledgeDocument();
                doc.setScope(KnowledgeScopeEnum.SYSTEM.getCode());
                doc.setUserId(null);
                doc.setType(KnowledgeTypeEnum.GITHUB_REPO.getCode());
                doc.setContent(key);
                doc.setTitle(source.title());
                doc.setContentHash(source.hash());
                doc.setFileStatus(KnowledgeDocument.FileStatus.READY.code());
                doc.setEmbeddingStatus(KnowledgeEmbeddingStatusEnum.SUCCESS.getCode());
                doc.setChunkCount(item.rows().size());
                doc.setLastEmbeddedAt(completed);
                doc.setCreatedAt(completed);
                doc.setUpdatedAt(completed);
                doc.setMetadata(metadata);
                if (documents.insert(doc) != 1 || doc.getId() == null) { throw new IllegalStateException("系统文档新增数量非法"); }
            } else {
                var update = sourceUpdate(doc.getId(), key)
                        .set(KnowledgeDocument::getTitle, source.title())
                        .set(KnowledgeDocument::getContentHash, source.hash())
                        .set(KnowledgeDocument::getType, KnowledgeTypeEnum.GITHUB_REPO.getCode())
                        .set(KnowledgeDocument::getFileStatus, KnowledgeDocument.FileStatus.READY.code())
                        .set(KnowledgeDocument::getEmbeddingStatus, KnowledgeEmbeddingStatusEnum.SUCCESS.getCode())
                        .set(KnowledgeDocument::getLastError, null)
                        .set(KnowledgeDocument::getUpdatedAt, completed)
                        .set(KnowledgeDocument::getMetadata, metadata,
                                "typeHandler=com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler");
                if (!item.rows().isEmpty()) {
                    update.set(KnowledgeDocument::getChunkCount, item.rows().size())
                            .set(KnowledgeDocument::getLastEmbeddedAt, completed);
                }
                if (documents.update(null, update) != 1) {
                    throw new IllegalStateException("系统文档更新数量非法");
                }
            }
            if (item.rows().isEmpty()) {
                totalChunks += doc.getChunkCount();
                continue;
            }
            KnowledgeEmbeddingServiceImpl.replaceSystem(vectors, doc.getId(), baseline.documents().containsKey(key)
                    ? baseline.chunkIds().get(doc.getId()).size() : 0, source.url(), item.rows());
            totalChunks += item.rows().size();
        }
        for (var obsolete : previous.values()) {
            KnowledgeEmbeddingServiceImpl.deleteSystem(vectors, obsolete.getId(), baseline.chunkIds().get(obsolete.getId()).size());
            if (documents.delete(sourceQuery().eq(KnowledgeDocument::getId, obsolete.getId())
                    .eq(KnowledgeDocument::getContent, obsolete.getContent())) != 1) {
                throw new IllegalStateException("系统文档删除数量非法");
            }
        }
        return new SystemKnowledgeImportResult(request.runId(), request.manifestHash(), prepared.size(), totalChunks);
    }

    /** 读取来源内的文档及其片段编号；成功记录的片段数量异常时立即终止。 */
    private SystemKnowledgeImportSnapshot snapshot(boolean lock) {
        var query = sourceQuery().orderByAsc(KnowledgeDocument::getId);
        if (lock) { query.last("FOR UPDATE"); }
        Map<String, KnowledgeDocument> current = new HashMap<>();
        Map<Long, List<Long>> chunkIds = new HashMap<>();
        for (var doc : documents.selectList(query)) {
            current.put(doc.getContent(), doc);
            chunkIds.put(doc.getId(), new ArrayList<>());
        }
        if (!current.isEmpty()) {
            var chunks = vectors.selectList(new LambdaQueryWrapper<KnowledgeVector>()
                    .select(KnowledgeVector::getId, KnowledgeVector::getDocumentId)
                    .in(KnowledgeVector::getDocumentId, chunkIds.keySet()).orderByAsc(KnowledgeVector::getId));
            for (var chunk : chunks) { chunkIds.get(chunk.getDocumentId()).add(chunk.getId()); }
        }
        for (var doc : current.values()) {
            if (Objects.equals(doc.getEmbeddingStatus(), KnowledgeEmbeddingStatusEnum.SUCCESS.getCode())
                    && (!Objects.equals(doc.getChunkCount(), chunkIds.get(doc.getId()).size()) || chunkIds.get(doc.getId()).isEmpty())) {
                throw new IllegalStateException("系统知识库成功文档的实际片段数量与记录不一致，文档编号=" + doc.getId());
            }
        }
        chunkIds.replaceAll((id, ids) -> List.copyOf(ids));
        return new SystemKnowledgeImportSnapshot(current, chunkIds);
    }

    private static LambdaQueryWrapper<KnowledgeDocument> sourceQuery() {
        return new LambdaQueryWrapper<KnowledgeDocument>().eq(KnowledgeDocument::getScope, KnowledgeScopeEnum.SYSTEM.getCode())
                .isNull(KnowledgeDocument::getUserId).likeRight(KnowledgeDocument::getContent, "javaguide:docs/");
    }

    private static LambdaUpdateWrapper<KnowledgeDocument> sourceUpdate(Long id, String key) {
        return new LambdaUpdateWrapper<KnowledgeDocument>().eq(KnowledgeDocument::getId, id)
                .eq(KnowledgeDocument::getScope, KnowledgeScopeEnum.SYSTEM.getCode())
                .isNull(KnowledgeDocument::getUserId).eq(KnowledgeDocument::getContent, key);
    }

    /**
     * 固定 current 的真实目录并读取不可变正文快照。
     * @param request 运行目录编号和清单摘要
     * @return 全部已验证资料，最多2000篇，每篇最多4MiB，正文总计最多100MiB
     * @throws BusinessException 清单、来源、路径或摘要非法，或服务器文件不可用
     */
    List<SystemKnowledgeSource> readSources(SystemKnowledgeImportRequest request) {
        Objects.requireNonNull(request, "导入参数不能为空");
        try {
            Path root = Path.of(sourceConfig.root());
            if (!root.isAbsolute() || !root.equals(root.toRealPath())) { throw invalid("资料根目录必须为真实绝对路径"); }
            Path versions = root.resolve("versions");
            requireDirectory(versions);
            Path directory = versions.resolve(request.runId());
            requireDirectory(directory);
            Path pointer = root.resolve("current");
            if (!Files.isSymbolicLink(pointer) || !pointer.toRealPath().equals(directory)) {
                throw invalid("运行编号必须指向当前成功资料");
            }
            byte[] manifest = readFile(directory.resolve("manifest.json"), 4 * 1024 * 1024);
            if (!hash(manifest).equals(request.manifestHash())) { throw invalid("清单摘要不一致"); }
            JsonNode json = objectMapper.readTree(manifest);
            String commit = text(json, "source_commit");
            if (!REPOSITORY.equals(text(json, "repository")) || !commit.matches("[0-9a-f]{40}")
                    || !"SHA-256".equals(text(json, "hash_algorithm"))) { throw invalid("清单来源非法"); }
            for (String name : List.of("rules_hash", "cleaner_hash")) {
                if (!text(json, name).matches("[0-9a-f]{64}")) { throw invalid("清理规则摘要非法"); }
            }
            JsonNode documents = json.path("documents");
            if (!documents.isArray() || documents.isEmpty() || documents.size() > 2000) { throw invalid("清单文档数量非法"); }
            List<SystemKnowledgeSource> sources = new ArrayList<>();
            Set<String> names = new HashSet<>();
            long totalBytes = 0;
            for (JsonNode document : documents) {
                String name = text(document, "source_path");
                Path relative = Path.of(name);
                if (!name.startsWith("docs/") || !name.endsWith(".md") || name.contains("\\")
                        || relative.isAbsolute() || relative.getNameCount() < 2
                        || !relative.normalize().equals(relative)
                        || java.util.stream.StreamSupport.stream(relative.spliterator(), false)
                            .anyMatch(part -> part.toString().equals("..") || part.toString().equals("."))
                        || !name.equals(relative.toString()) || !names.add(name)
                        || !name.equals(text(document, "output_path")) || !"SYSTEM".equals(text(document, "scope"))) {
                    throw invalid("资料路径或范围非法");
                }
                String title = text(document, "title"), digest = text(document, "content_hash");
                String url = REPOSITORY + "/blob/" + commit + "/" + name;
                if (title.isBlank() || title.length() > 500 || !digest.matches("[0-9a-f]{64}")
                        || !url.equals(text(document, "source_url"))) { throw invalid("资料标题、来源或摘要非法"); }
                Path file = directory.resolve(relative);
                for (Path parent = file.getParent(); !parent.equals(directory); parent = parent.getParent()) {
                    requireDirectory(parent);
                }
                byte[] bytes = readFile(file, 4 * 1024 * 1024);
                totalBytes += bytes.length;
                if (totalBytes > 100L * 1024 * 1024 || !hash(bytes).equals(digest)) { throw invalid("正文容量超限或摘要不一致"); }
                // UTF-8 解码必须完整成功，防止非法字节被解析器替换。
                java.nio.charset.StandardCharsets.UTF_8.newDecoder().decode(java.nio.ByteBuffer.wrap(bytes));
                sources.add(new SystemKnowledgeSource(name, title, digest, url, commit, bytes));
            }
            return List.copyOf(sources);
        } catch (IOException exception) {
            throw new BusinessException(ErrorCode.FILE_STORAGE_UNAVAILABLE, "系统资料读取失败", exception);
        }
    }

    static String hash(byte[] bytes) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)); }
        catch (NoSuchAlgorithmException exception) { throw new IllegalStateException(exception); }
    }

    private static void requireDirectory(Path directory) {
        if (Files.isSymbolicLink(directory) || !Files.isDirectory(directory, LinkOption.NOFOLLOW_LINKS)) {
            throw invalid("资料目录含符号链接或缺失");
        }
    }

    private static byte[] readFile(Path path, int maxBytes) throws IOException {
        if (!Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)) { throw invalid("资料必须为普通文件"); }
        try (var input = Files.newInputStream(path, LinkOption.NOFOLLOW_LINKS)) {
            byte[] bytes = input.readNBytes(maxBytes + 1);
            if (bytes.length == 0 || bytes.length > maxBytes) { throw invalid("资料文件容量非法"); }
            return bytes;
        }
    }

    private static String text(JsonNode json, String name) {
        if (!json.path(name).isTextual()) { throw invalid("清单字段类型非法：" + name); }
        return json.path(name).textValue();
    }

    private static BusinessException invalid(String message) {
        return new BusinessException(ErrorCode.VALIDATION_ERROR, message);
    }
}
