package com.zdmj.common.storage;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import com.zdmj.common.context.CurrentActor;
import com.zdmj.common.exception.BusinessException;
import com.zdmj.common.exception.ErrorCode;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;

class FileUploadServiceTest {
    @TempDir Path root;
    @org.junit.jupiter.api.BeforeEach
    void canonicalRoot() throws IOException { root = root.toRealPath(); }
    private final CurrentActor actor = new CurrentActor(8L);

    @Test
    void 保存原文件并保留下载名称_重复上传生成独立标识_删除幂等() throws Exception {
        var service = new FileUploadService(root.toString());
        var file = new MockMultipartFile("file", "项目说明.md", "text/markdown", "Java项目".getBytes(java.nio.charset.StandardCharsets.UTF_8));
        var target = service.prepare(actor, file);
        assertThat(target.key()).matches("user-8/[a-f0-9]{32}\\.md");
        assertThat(target.fileName()).isEqualTo("项目说明.md");
        assertThat(service.prepare(actor, file).key()).isNotEqualTo(target.key());
        assertThat(service.save(actor, target, file)).hasSize(64);
        try (var input = service.openInputStream(actor, target.key())) {
            assertThat(input.readAllBytes()).isEqualTo(file.getBytes());
        }
        assertThat(Files.exists(root.resolve(target.key() + ".part"))).isFalse();
        service.delete(actor, target.key());
        service.delete(actor, target.key());
        assertThat(Files.exists(root.resolve(target.key()))).isFalse();
    }

    @ParameterizedTest
    @ValueSource(strings = {"../secret", "/etc/passwd", "user-8/../secret.md", "user-8/a.md", "https://evil.example/a.pdf",
            "user-8/aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa%2f.md", "user-8/aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa.exe"})
    void 非法标识拒绝所有文件操作(String key) {
        var service = new FileUploadService(root.toString());
        assertThatThrownBy(() -> service.openInputStream(actor, key)).isInstanceOf(BusinessException.class)
                .extracting("code").isEqualTo(ErrorCode.VALIDATION_ERROR.getCode());
        assertThatThrownBy(() -> service.delete(actor, key)).isInstanceOf(BusinessException.class);
    }

    @Test
    void 跨用户访问与符号链接目录均拒绝() throws Exception {
        var service = new FileUploadService(root.toString());
        String key = "user-8/" + "a".repeat(32) + ".md";
        assertThatThrownBy(() -> service.openInputStream(new CurrentActor(80L), key))
                .extracting("code").isEqualTo(ErrorCode.NO_PERMISSION.getCode());
        Path outside = Files.createDirectory(root.resolve("outside"));
        Files.createSymbolicLink(root.resolve("user-8"), outside);
        assertThatThrownBy(() -> service.openInputStream(actor, key)).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> service.delete(actor, key)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void 文件符号链接不能用于读取() throws Exception {
        var service = new FileUploadService(root.toString());
        Path user = Files.createDirectory(root.resolve("user-8"));
        Path source = Files.writeString(root.resolve("secret"), "secret");
        String key = "user-8/" + "a".repeat(32) + ".md";
        Files.createSymbolicLink(user.resolve("a".repeat(32) + ".md"), source);
        assertThatThrownBy(() -> service.openInputStream(actor, key)).hasCauseInstanceOf(IOException.class);
    }

    @Test
    void 目录不存在或相对路径_启动失败() {
        assertThatThrownBy(() -> new FileUploadService(root.resolve("missing").toString()))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> new FileUploadService("data/knowledge")).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void 空文件_超限_类型与名称非法均拒绝() {
        var service = new FileUploadService(root.toString());
        assertThatThrownBy(() -> service.prepare(actor, new MockMultipartFile("file", new byte[0])))
                .extracting("code").isEqualTo(ErrorCode.FILE_EMPTY.getCode());
        assertThatThrownBy(() -> service.prepare(actor, new MockMultipartFile("file", "a.exe", "", new byte[]{1})))
                .extracting("code").isEqualTo(ErrorCode.FILE_TYPE_NOT_EXISTS.getCode());
        assertThatThrownBy(() -> service.prepare(actor, new MockMultipartFile("file", "a\n.md", "", new byte[]{1})))
                .extracting("code").isEqualTo(ErrorCode.VALIDATION_ERROR.getCode());
        MultipartFile large = mock(MultipartFile.class);
        when(large.getSize()).thenReturn(21L * 1024 * 1024);
        assertThatThrownBy(() -> service.prepare(actor, large)).extracting("code").isEqualTo(ErrorCode.FILE_SIZE_EXCEEDED.getCode());
    }

    @Test
    void 写入失败关闭输入流_保留临时文件可显式清理() throws Exception {
        var service = new FileUploadService(root.toString());
        MultipartFile file = mock(MultipartFile.class);
        when(file.getOriginalFilename()).thenReturn("a.pdf");
        when(file.getSize()).thenReturn(5L);
        InputStream input = mock(InputStream.class);
        when(input.read(any(byte[].class), anyInt(), anyInt())).thenThrow(new IOException("disk"));
        when(file.getInputStream()).thenReturn(input);
        var target = service.prepare(actor, file);
        assertThatThrownBy(() -> service.save(actor, target, file)).hasCauseInstanceOf(IOException.class);
        verify(input).close();
        assertThat(root.resolve(target.key() + ".part")).exists();
        service.delete(actor, target.key());
        assertThat(root.resolve(target.key() + ".part")).doesNotExist();
    }

    @Test
    void 扩展名和内容不符_拒绝发布正式文件() {
        var service = new FileUploadService(root.toString());
        for (var file : java.util.List.of(
                new MockMultipartFile("file", "fake.pdf", "application/pdf", "not a pdf".getBytes()),
                new MockMultipartFile("file", "binary.md", "text/markdown", new byte[]{0, 1, 2}),
                new MockMultipartFile("file", "invalid.md", "text/markdown", new byte[]{(byte) 0xff}))) {
            var target = service.prepare(actor, file);
            assertThatThrownBy(() -> service.save(actor, target, file)).extracting("code")
                    .isEqualTo(ErrorCode.VALIDATION_ERROR.getCode());
            assertThat(root.resolve(target.key())).doesNotExist();
            service.delete(actor, target.key());
        }
    }

    @Test
    void 已存在的文件不会被覆盖_缺失文件读取立即失败() throws Exception {
        var service = new FileUploadService(root.toString());
        var file = new MockMultipartFile("file", "a.pdf", "application/pdf", "%PDF-".getBytes());
        var target = service.prepare(actor, file);
        service.save(actor, target, file);
        assertThatThrownBy(() -> service.save(actor, target, file)).isInstanceOf(IllegalStateException.class);
        service.delete(actor, target.key());
        assertThatThrownBy(() -> service.openInputStream(actor, target.key()))
                .extracting("code").isEqualTo(ErrorCode.FILE_STORAGE_UNAVAILABLE.getCode());
    }
}
