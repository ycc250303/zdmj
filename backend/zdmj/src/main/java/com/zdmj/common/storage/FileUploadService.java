package com.zdmj.common.storage;

import com.zdmj.common.context.CurrentActor;
import com.zdmj.common.exception.BusinessException;
import com.zdmj.common.exception.ErrorCode;
import java.io.IOException;
import java.io.InputStream;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

/** 用户原文件存储。路径仅由认证用户编号和服务器生成的 UUID 组成。 */
@Service
public class FileUploadService {
    private static final long MAX_SIZE = 20L * 1024 * 1024;
    private final Path root;

    /** 检查已有持久化目录并验证写入、原子移动能力，配置错误立即终止启动。 */
    public FileUploadService(@Value("${knowledge.storage.root}") String directory) {
        root = Path.of(directory).toAbsolutePath().normalize();
        try {
            if (!Path.of(directory).isAbsolute() || !Files.isDirectory(root, LinkOption.NOFOLLOW_LINKS)
                    || !root.equals(root.toRealPath())) {
                throw new IllegalStateException("知识文件根目录必须是已存在的绝对路径，且不能包含符号链接");
            }
            Path probe = Files.createTempFile(root, ".probe-", ".part");
            Path moved = probe.resolveSibling(probe.getFileName() + ".ready");
            try {
                Files.move(probe, moved, StandardCopyOption.ATOMIC_MOVE);
            } finally {
                Files.deleteIfExists(probe);
                Files.deleteIfExists(moved);
            }
        } catch (IOException exception) {
            throw new IllegalStateException("知识文件目录不可写或不支持原子移动", exception);
        }
    }

    /** 校验大小、类型和原始名称，并生成尚未写入的文件描述。 */
    public StoredFile prepare(CurrentActor actor, MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException(ErrorCode.FILE_EMPTY);
        }
        if (file.getSize() > MAX_SIZE) {
            throw new BusinessException(ErrorCode.FILE_SIZE_EXCEEDED);
        }
        String name = Objects.requireNonNullElse(file.getOriginalFilename(), "");
        name = name.substring(Math.max(name.lastIndexOf('/'), name.lastIndexOf('\\')) + 1);
        if (name.isBlank() || name.length() > 255 || name.chars().anyMatch(Character::isISOControl)) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "文件名称无效");
        }
        String lower = name.toLowerCase(Locale.ROOT);
        String extension = lower.endsWith(".pdf") ? ".pdf"
                : lower.endsWith(".md") || lower.endsWith(".markdown") ? ".md" : null;
        if (extension == null) {
            throw new BusinessException(ErrorCode.FILE_TYPE_NOT_EXISTS, "仅支持 PDF 和 Markdown 文件");
        }
        return new StoredFile("user-" + actor.userId() + "/" + UUID.randomUUID().toString().replace("-", "")
                + extension, name, file.getSize(), extension.equals(".pdf") ? "application/pdf" : "text/markdown");
    }

    /**
     * 同步写入临时文件、刷新内容并原子移动为正式文件，返回原文件 SHA-256。
     * @param actor 已认证操作者
     * @param target 本组件生成并已登记到数据库的文件描述
     * @param file 请求中的原文件
     * @return 原文件内容摘要
     * @throws BusinessException 读取失败、文件超限或持久化目录不可用
     */
    public String save(CurrentActor actor, StoredFile target, MultipartFile file) {
        Path destination = resolve(actor, target.key());
        Path temporary = destination.resolveSibling(destination.getFileName() + ".part");
        try {
            Files.createDirectories(destination.getParent());
            requireDirectory(destination.getParent());
            MessageDigest digest = sha256();
            try (InputStream input = new DigestInputStream(uploadInput(file), digest);
                    var output = FileChannel.open(temporary, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE,
                            LinkOption.NOFOLLOW_LINKS)) {
                byte[] buffer = new byte[8192];
                long size = 0;
                int count;
                while ((count = input.read(buffer)) != -1) {
                    size += count;
                    if (size > MAX_SIZE) {
                        throw new BusinessException(ErrorCode.FILE_SIZE_EXCEEDED);
                    }
                    var bytes = java.nio.ByteBuffer.wrap(buffer, 0, count);
                    while (bytes.hasRemaining()) {
                        output.write(bytes);
                    }
                }
                if (size != target.fileSize()) {
                    throw new IOException("文件实际长度与声明不一致");
                }
                output.force(true);
            }
            validateContent(temporary, target.contentType());
            if (Files.exists(destination, LinkOption.NOFOLLOW_LINKS)) {
                throw new IllegalStateException("原文件标识已被使用");
            }
            Files.move(temporary, destination, StandardCopyOption.ATOMIC_MOVE);
            try (FileChannel directory = FileChannel.open(destination.getParent(), StandardOpenOption.READ)) {
                directory.force(true);
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (IOException exception) {
            throw new BusinessException(ErrorCode.FILE_STORAGE_UNAVAILABLE, exception);
        }
    }

    /** 打开已授权原文件，调用方负责关闭；缺失或不可读立即返回存储故障。 */
    public InputStream openInputStream(CurrentActor actor, String key) {
        try {
            Path path = resolve(actor, key);
            requireDirectory(path.getParent());
            return Files.newInputStream(path, LinkOption.NOFOLLOW_LINKS);
        } catch (IOException exception) {
            throw new BusinessException(ErrorCode.FILE_STORAGE_UNAVAILABLE, exception);
        }
    }

    /** 删除原文件及未完成写入的临时文件；已经不存在时视为清理完成。 */
    public void delete(CurrentActor actor, String key) {
        try {
            Path path = resolve(actor, key);
            requireDirectory(path.getParent());
            Files.deleteIfExists(path);
            Files.deleteIfExists(path.resolveSibling(path.getFileName() + ".part"));
        } catch (IOException exception) {
            throw new BusinessException(ErrorCode.FILE_STORAGE_UNAVAILABLE, exception);
        }
    }

    /** 请求输入故障与磁盘写入故障分别保留对应异常语义。 */
    private InputStream uploadInput(MultipartFile file) {
        try {
            return new java.io.FilterInputStream(file.getInputStream()) {
                @Override
                public int read(byte[] bytes, int offset, int length) {
                    try {
                        return in.read(bytes, offset, length);
                    } catch (IOException exception) {
                        throw new BusinessException(ErrorCode.FILE_READ_FAILED, exception);
                    }
                }
                @Override
                public void close() {
                    try {
                        in.close();
                    } catch (IOException exception) {
                        throw new BusinessException(ErrorCode.FILE_READ_FAILED, exception);
                    }
                }
            };
        } catch (IOException exception) {
            throw new BusinessException(ErrorCode.FILE_READ_FAILED, exception);
        }
    }

    /** 验证 PDF 文件头或严格 UTF-8 文本，避免仅依赖客户端扩展名。 */
    private void validateContent(Path path, String contentType) throws IOException {
        if (contentType.equals("application/pdf")) {
            try (InputStream input = Files.newInputStream(path, LinkOption.NOFOLLOW_LINKS)) {
                if (!java.util.Arrays.equals(input.readNBytes(5), "%PDF-".getBytes(java.nio.charset.StandardCharsets.US_ASCII))) {
                    throw new BusinessException(ErrorCode.VALIDATION_ERROR, "PDF 文件内容无效");
                }
            }
        } else {
            var decoder = java.nio.charset.StandardCharsets.UTF_8.newDecoder();
            try (var reader = new java.io.InputStreamReader(Files.newInputStream(path, LinkOption.NOFOLLOW_LINKS), decoder)) {
                char[] buffer = new char[8192];
                int count;
                while ((count = reader.read(buffer)) != -1) {
                    for (int i = 0; i < count; i++) {
                        char character = buffer[i];
                        if (Character.isISOControl(character) && character != '\n' && character != '\r' && character != '\t') {
                            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "Markdown 文件必须是 UTF-8 文本");
                        }
                    }
                }
            } catch (java.nio.charset.CharacterCodingException exception) {
                throw new BusinessException(ErrorCode.VALIDATION_ERROR, "Markdown 文件必须是 UTF-8 文本", exception);
            }
        }
    }

    private Path resolve(CurrentActor actor, String key) {
        String prefix = "user-" + actor.userId() + "/";
        if (key == null || !key.matches("user-[1-9][0-9]*/[a-f0-9]{32}\\.(pdf|md)")) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "文件标识无效");
        }
        if (!key.startsWith(prefix)) {
            throw new BusinessException(ErrorCode.NO_PERMISSION);
        }
        Path path = root.resolve(key).normalize();
        if (!path.startsWith(root)) {
            throw new IllegalStateException("文件路径超出持久化目录");
        }
        requireDirectory(path.getParent());
        return path;
    }

    private void requireDirectory(Path directory) {
        try {
            if (!root.equals(root.toRealPath()) || (Files.exists(directory, LinkOption.NOFOLLOW_LINKS)
                    && (!Files.isDirectory(directory, LinkOption.NOFOLLOW_LINKS)
                    || !directory.equals(directory.toRealPath())))) {
                throw new IllegalStateException("文件目录包含符号链接或无效路径");
            }
        } catch (IOException exception) {
            throw new BusinessException(ErrorCode.FILE_STORAGE_UNAVAILABLE, exception);
        }
    }

    private static MessageDigest sha256() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(exception);
        }
    }

    /** 原文件描述：相对标识、下载文件名、字节数和服务器确定的媒体类型。 */
    public record StoredFile(String key, String fileName, long fileSize, String contentType) {
        public StoredFile {
            Objects.requireNonNull(key);
            Objects.requireNonNull(fileName);
            Objects.requireNonNull(contentType);
            if (fileSize <= 0 || fileSize > MAX_SIZE) {
                throw new IllegalArgumentException("原文件长度无效");
            }
        }
    }
}
