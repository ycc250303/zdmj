package com.zdmj.common.storage;

import com.zdmj.common.context.CurrentActor;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.io.InputStream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import static org.mockito.Mockito.mockConstruction;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;

import com.qcloud.cos.COSClient;
import com.qcloud.cos.exception.CosClientException;
import com.qcloud.cos.exception.CosServiceException;
import com.qcloud.cos.model.COSObject;
import com.qcloud.cos.model.COSObjectInputStream;
import com.qcloud.cos.model.COSObjectSummary;
import com.qcloud.cos.model.GetObjectRequest;
import com.qcloud.cos.model.ListObjectsRequest;
import com.qcloud.cos.model.ObjectListing;
import com.qcloud.cos.model.PutObjectRequest;
import com.qcloud.cos.model.PutObjectResult;
import com.zdmj.common.exception.BusinessException;
import com.zdmj.common.exception.ErrorCode;

class FileUploadServiceTest {

    private final CurrentActor actor = new CurrentActor(8L);

    private static final String BUCKET = "demo-bucket";
    private static final String REGION = "ap-guangzhou";
    private static final long USER_ID = 8L;

    @Test
    void uploadFile_empty_shouldReject() {
        FileUploadService service = ready();
        MockMultipartFile empty = new MockMultipartFile("file", "a.pdf", "application/pdf", new byte[0]);
        assertThatThrownBy(() -> service.uploadFile(actor, empty, FileCategory.RESUME))
                .isInstanceOf(BusinessException.class)
                .extracting("code")
                .isEqualTo(ErrorCode.FILE_EMPTY.getCode());
    }

    @Test
    void uploadFile_pdf_shouldStoreUnderUserPrefix() throws Exception {
        FileUploadService service = ready();
        COSClient cos = cosClient;
        PutObjectResult stored = new PutObjectResult();
        stored.setETag("etag-1");
        when(cos.putObject(any(PutObjectRequest.class))).thenReturn(stored);
        MockMultipartFile file = new MockMultipartFile("file", "简历.pdf", "application/pdf", "pdf".getBytes());

        FileUploadResponse response = service.uploadFile(actor, file, FileCategory.RESUME);

        assertThat(response.getKey()).startsWith("user-8/resume/");
        assertThat(response.getKey()).endsWith(".pdf");
        assertThat(response.getUrl()).startsWith("https://demo-bucket.cos.ap-guangzhou.myqcloud.com/user-8/resume/");
        assertThat(response.getFileName()).isEqualTo("简历.pdf");
        assertThat(response.getContentType()).isEqualTo("application/pdf");
    }

    @Test
    void uploadFile_blankName_shouldUseGeneratedName() throws Exception {
        FileUploadService service = ready();
        when(cosClient.putObject(any(PutObjectRequest.class))).thenReturn(new PutObjectResult());
        MockMultipartFile file = new MockMultipartFile("file", ".pdf", null, "x".getBytes());

        FileUploadResponse response = service.uploadFile(actor, file, FileCategory.RESUME);

        assertThat(response.getKey()).startsWith("user-8/resume/file-");
    }

    @Test
    void uploadFile_withoutContentType_shouldStillStore() throws Exception {
        FileUploadService service = ready();
        when(cosClient.putObject(any(PutObjectRequest.class))).thenReturn(new PutObjectResult());
        MockMultipartFile file = new MockMultipartFile("file", "note", null, "txt".getBytes());

        FileUploadResponse response = service.uploadFile(actor, file, FileCategory.FILES);

        assertThat(response.getKey()).startsWith("user-8/files/");
        assertThat(response.getContentType()).isNull();
    }

    @Test
    void uploadFile_streamFailure_shouldWrapIoException() throws Exception {
        FileUploadService service = ready();
        MultipartFile file = mock(MultipartFile.class);
        when(file.isEmpty()).thenReturn(false);
        when(file.getOriginalFilename()).thenReturn("a.pdf");
        when(file.getInputStream()).thenThrow(new IOException("disk"));

        assertThatThrownBy(() -> service.uploadFile(actor, file, FileCategory.RESUME))
                .isInstanceOf(RuntimeException.class)
                .extracting("code").isEqualTo(ErrorCode.FILE_READ_FAILED.getCode());
    }

    @Test
    void deleteOwnedByUrl_blank_shouldIgnore() {
        ready().deleteOwnedByUrl(actor, "  ", FileCategory.RESUME);
    }

    @Test
    void deleteOwnedByUrl_otherArea_shouldReject() throws Exception {
        FileUploadService service = ready();
        String url = "https://demo-bucket.cos.ap-guangzhou.myqcloud.com/user-8/resume/a.pdf";

        assertThatThrownBy(() -> service.deleteOwnedByUrl(actor, url, FileCategory.PROFILE))
                .isInstanceOf(BusinessException.class)
                .extracting("code")
                .isEqualTo(ErrorCode.NO_PERMISSION.getCode());
    }

    @Test
    void deleteOwnedByUrl_ownedKey_shouldDelete() throws Exception {
        FileUploadService service = ready();
        String url = "https://demo-bucket.cos.ap-guangzhou.myqcloud.com/user-8/resume/a.pdf";

        service.deleteOwnedByUrl(actor, url, FileCategory.RESUME);

        verify(cosClient).deleteObject(BUCKET, "user-8/resume/a.pdf");
    }

    @Test
    void deleteByKey_ownedKey_shouldDelete() throws Exception {
        FileUploadService service = ready();

        service.deleteByKey(actor, "user-8/resume/a.pdf");

        verify(cosClient).deleteObject(BUCKET, "user-8/resume/a.pdf");
    }

    @Test
    void deleteByKey_invalidPath_shouldReject() {
        FileUploadService service = ready();
        assertThatThrownBy(() -> service.deleteByKey(actor, "../secret"))
                .isInstanceOf(BusinessException.class)
                .extracting("code")
                .isEqualTo(ErrorCode.VALIDATION_ERROR.getCode());
    }

    @Test
    void deleteByKey_otherUser_shouldReject() {
        FileUploadService service = ready();
        assertThatThrownBy(() -> service.deleteByKey(actor, "user-9/resume/a.pdf"))
                .isInstanceOf(BusinessException.class)
                .extracting("code")
                .isEqualTo(ErrorCode.NO_PERMISSION.getCode());
    }

    @Test
    void listUploadedFiles_shouldSkipDirectoriesAndForeignKeys() throws Exception {
        FileUploadService service = ready();
        ObjectListing first = listing(true, "next",
                summary("user-8/resume/a.pdf"),
                summary("user-8/resume/"),
                summary("user-9/resume/b.pdf"));
        ObjectListing second = listing(false, null, summary("user-8/avatar/c.png"));
        ObjectListing resumePage = listing(false, null, summary("user-8/resume/a.pdf"));
        when(cosClient.listObjects(any(ListObjectsRequest.class))).thenReturn(first, second, resumePage);

        List<FileUploadListItemResponse> all = service.listUploadedFiles(actor, null);
        List<FileUploadListItemResponse> resume = service.listUploadedFiles(actor, FileCategory.RESUME);

        assertThat(all).extracting(FileUploadListItemResponse::getKey)
                .containsExactly("user-8/resume/a.pdf", "user-8/avatar/c.png");
        assertThat(all.get(0).getBizArea()).isEqualTo("resume");
        assertThat(all.get(0).getFileName()).isEqualTo("a.pdf");
        assertThat(resume).extracting(FileUploadListItemResponse::getBizArea).containsOnly("resume");
    }

    @Test
    void exists_missingObject_shouldReturnFalse() throws Exception {
        FileUploadService service = ready();
        CosServiceException missing = new CosServiceException("missing");
        missing.setStatusCode(404);
        when(cosClient.getObjectMetadata(any())).thenThrow(missing);

        assertThat(service.existsByUrl(actor, "https://demo-bucket.cos.ap-guangzhou.myqcloud.com/user-8/resume/a.pdf")).isFalse();
    }

    @Test
    void exists_serviceError_shouldWrap() throws Exception {
        FileUploadService service = ready();
        CosServiceException denied = new CosServiceException("denied");
        denied.setStatusCode(403);
        when(cosClient.getObjectMetadata(any())).thenThrow(denied);

        assertThatThrownBy(() -> service.existsByUrl(actor, "https://demo-bucket.cos.ap-guangzhou.myqcloud.com/user-8/resume/a.pdf"))
                .isInstanceOf(RuntimeException.class)
                .extracting("code").isEqualTo(ErrorCode.OBJECT_STORAGE_UNAVAILABLE.getCode());
    }

    @Test
    void exists_present_shouldReturnTrue() throws Exception {
        FileUploadService service = ready();
        when(cosClient.getObjectMetadata(any())).thenReturn(null);

        assertThat(service.existsByUrl(actor, "https://demo-bucket.cos.ap-guangzhou.myqcloud.com/user-8/resume/a.pdf")).isTrue();
    }

    @Test
    void openInputStream_ownedUrl_shouldReturnContent() throws Exception {
        FileUploadService service = ready();
        COSObject object = mock(COSObject.class);
        COSObjectInputStream content = mock(COSObjectInputStream.class);
        when(content.readAllBytes()).thenReturn("pdf".getBytes());
        when(object.getObjectContent()).thenReturn(content);
        when(cosClient.getObject(any(GetObjectRequest.class))).thenReturn(object);
        String url = "https://demo-bucket.cos.ap-guangzhou.myqcloud.com/user-8/resume/a.pdf";

        assertThat(service.openInputStreamFromUrl(actor, url).readAllBytes()).isEqualTo("pdf".getBytes());
    }

    @Test
    void openInputStream_unmanagedOrMissingActor_shouldReject() {
        FileUploadService service = ready();
        assertThatThrownBy(() -> service.openInputStreamFromUrl(new CurrentActor(USER_ID), "https://example.com/a"))
                .isInstanceOf(BusinessException.class)
                .extracting("code")
                .isEqualTo(ErrorCode.URL_FORMAT_ERROR.getCode());
        assertThatThrownBy(() -> service.openInputStreamFromUrl(null, "https://demo-bucket.cos.ap-guangzhou.myqcloud.com/user-8/resume/a.pdf"))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void deleteOwnedByUrl_foreignHostWithOwnedPath_shouldRejectBeforeDeleting() throws Exception {
        FileUploadService service = ready();
        assertThatThrownBy(() -> service.deleteOwnedByUrl(actor,
                "https://evil.example/user-8/resume/a.pdf", FileCategory.RESUME))
                .isInstanceOf(BusinessException.class).extracting("code")
                .isEqualTo(ErrorCode.URL_FORMAT_ERROR.getCode());
        org.mockito.Mockito.verifyNoInteractions(cosClient);
    }

    @Test
    void openInputStream_plusInPath_shouldPreserveObjectKey() throws Exception {
        FileUploadService service = ready();
        COSObject object = new COSObject();
        object.setObjectContent(new COSObjectInputStream(new java.io.ByteArrayInputStream(new byte[0]),
                new org.apache.http.client.methods.HttpGet()));
        when(cosClient.getObject(any(GetObjectRequest.class))).thenReturn(object);
        try (var input = service.openInputStreamFromUrl(actor,
                "https://demo-bucket.cos.ap-guangzhou.myqcloud.com/user-8/resume/a+b.pdf")) {
            assertThat(input.read()).isEqualTo(-1);
        }
        org.mockito.ArgumentCaptor<GetObjectRequest> request = org.mockito.ArgumentCaptor.forClass(GetObjectRequest.class);
        verify(cosClient).getObject(request.capture());
        assertThat(request.getValue().getKey()).isEqualTo("user-8/resume/a+b.pdf");
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "https://[", "file:///user-8/resume/a.pdf", "user-8/resume/a.pdf",
        "http://demo-bucket.cos.ap-guangzhou.myqcloud.com/user-8/resume/a.pdf",
        "https://evil.example/user-8/resume/a.pdf",
        "https://user:pw@demo-bucket.cos.ap-guangzhou.myqcloud.com/user-8/resume/a.pdf",
        "https://demo-bucket.cos.ap-guangzhou.myqcloud.com:444/user-8/resume/a.pdf",
        "https://demo-bucket.cos.ap-guangzhou.myqcloud.com/user-8/resume/a.pdf?token=x",
        "https://demo-bucket.cos.ap-guangzhou.myqcloud.com/user-8/resume/a.pdf#fragment",
        "https://demo-bucket.cos.ap-guangzhou.myqcloud.com/user-8/resume/a%2Fb.pdf",
        "https://demo-bucket.cos.ap-guangzhou.myqcloud.com/user-8/resume/a%5Cb.pdf",
        "https://demo-bucket.cos.ap-guangzhou.myqcloud.com/user-8/resume/%"})
    void urlOperations_shouldRejectInvalidSourceBeforeCallingSdk(String url) {
        FileUploadService service = ready();
        assertCode(() -> service.openInputStreamFromUrl(actor, url), ErrorCode.URL_FORMAT_ERROR);
        assertCode(() -> service.existsByUrl(actor, url), ErrorCode.URL_FORMAT_ERROR);
        if (!url.isBlank()) {
            assertCode(() -> service.deleteOwnedByUrl(actor, url, FileCategory.RESUME), ErrorCode.URL_FORMAT_ERROR);
        }
        org.mockito.Mockito.verifyNoInteractions(cosClient);
    }

    @ParameterizedTest
    @ValueSource(strings = {"user-8/../secret", "user-8/%2e%2e/secret", "user-8/resume//a.pdf"})
    void urlOperations_shouldRejectTraversal(String path) {
        FileUploadService service = ready();
        String url = "https://demo-bucket.cos.ap-guangzhou.myqcloud.com/" + path;
        assertCode(() -> service.openInputStreamFromUrl(actor, url), ErrorCode.VALIDATION_ERROR);
        assertCode(() -> service.existsByUrl(actor, url), ErrorCode.VALIDATION_ERROR);
        assertCode(() -> service.deleteOwnedByUrl(actor, url, FileCategory.RESUME), ErrorCode.VALIDATION_ERROR);
        org.mockito.Mockito.verifyNoInteractions(cosClient);
    }

    @Test
    void urlOperations_shouldRejectForeignUser() {
        FileUploadService service = ready();
        String url = "https://demo-bucket.cos.ap-guangzhou.myqcloud.com/user-80/resume/a.pdf";
        assertCode(() -> service.openInputStreamFromUrl(actor, url), ErrorCode.NO_PERMISSION);
        assertCode(() -> service.existsByUrl(actor, url), ErrorCode.NO_PERMISSION);
        assertCode(() -> service.deleteOwnedByUrl(actor, url, FileCategory.RESUME), ErrorCode.NO_PERMISSION);
        org.mockito.Mockito.verifyNoInteractions(cosClient);
    }

    @Test
    void upload_storageFailure_shouldCloseStreamAndRetainCause() throws Exception {
        FileUploadService service = ready();
        InputStream input = mock(InputStream.class);
        MultipartFile file = mock(MultipartFile.class);
        when(file.getInputStream()).thenReturn(input);
        CosClientException cause = new CosClientException("down");
        when(cosClient.putObject(any(PutObjectRequest.class))).thenThrow(cause);
        assertThatThrownBy(() -> service.uploadFile(actor, file, FileCategory.RESUME))
                .hasCause(cause).extracting("code").isEqualTo(ErrorCode.OBJECT_STORAGE_UNAVAILABLE.getCode());
        verify(input).close();
    }

    @Test
    void list_brokenPagination_shouldFailFast() {
        ObjectListing broken = listing(true, null);
        when(cosClient.listObjects(any(ListObjectsRequest.class))).thenReturn(broken);
        assertThatThrownBy(() -> ready().listUploadedFiles(actor, null)).isInstanceOf(IllegalStateException.class);
    }

    private static void assertCode(org.assertj.core.api.ThrowableAssert.ThrowingCallable action, ErrorCode code) {
        assertThatThrownBy(action).isInstanceOf(BusinessException.class).extracting("code").isEqualTo(code.getCode());
    }

    private final COSClient cosClient = mock(COSClient.class);

    private FileUploadService ready() {
        return new FileUploadService(cosClient, BUCKET, REGION);
    }

    private static ObjectListing listing(boolean truncated, String marker, COSObjectSummary... summaries) {
        ObjectListing listing = mock(ObjectListing.class);
        when(listing.getObjectSummaries()).thenReturn(List.of(summaries));
        when(listing.isTruncated()).thenReturn(truncated);
        if (truncated) {
            when(listing.getNextMarker()).thenReturn(marker);
        }
        return listing;
    }

    private static COSObjectSummary summary(String key) {
        COSObjectSummary summary = new COSObjectSummary();
        summary.setKey(key);
        return summary;
    }

    private final ApplicationContextRunner context = new ApplicationContextRunner()
            .withUserConfiguration(FileUploadService.CosStorageConfiguration.class)
            .withPropertyValues("cos.secret-id=test-id", "cos.secret-key=test-key",
                    "cos.region=ap-guangzhou", "cos.bucket-name=test-bucket");

    @ParameterizedTest
    @ValueSource(strings = {"cos.secret-id", "cos.secret-key", "cos.region", "cos.bucket-name"})
    void missingConfiguration_shouldFailAtStartup(String property) {
        context.withPropertyValues(property + "= ").run(result -> assertThat(result).hasFailed());
    }

    @ParameterizedTest
    @ValueSource(strings = {"local", "stub", "invalid"})
    void unsupportedMode_shouldFailAtStartup(String mode) {
        context.withPropertyValues("zdmj.storage.mode=" + mode).run(result -> assertThat(result).hasFailed());
    }

    @Test
    void container_shouldCreateAndShutdownClient() {
        try (var clients = mockConstruction(COSClient.class)) {
            context.run(result -> assertThat(result).hasSingleBean(COSClient.class));
            assertThat(clients.constructed()).hasSize(1);
            verify(clients.constructed().getFirst()).shutdown();
        }
    }

    @Test
    void testProfile_shouldNotCreateRealClient() {
        context.withPropertyValues("spring.profiles.active=test")
                .run(result -> assertThat(result).doesNotHaveBean(COSClient.class));
    }
}
