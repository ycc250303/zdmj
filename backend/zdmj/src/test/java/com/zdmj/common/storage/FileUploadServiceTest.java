package com.zdmj.common.storage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.lang.reflect.Field;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
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
import com.zdmj.common.context.UserContext;
import com.zdmj.common.context.UserHolder;
import com.zdmj.common.exception.BusinessException;
import com.zdmj.common.exception.ErrorCode;

class FileUploadServiceTest {

    private static final String BUCKET = "demo-bucket";
    private static final String REGION = "ap-guangzhou";
    private static final long USER_ID = 8L;

    @AfterEach
    void clearUser() {
        UserHolder.clear();
    }

    @Test
    void init_stubMode_shouldSkipClient() {
        FileUploadService service = service("stub", "id", "key", BUCKET);
        service.init();
        service.destroy();
    }

    @Test
    void init_missingBucket_shouldFail() {
        FileUploadService service = service("cos", "id", "key", " ");
        assertThatThrownBy(service::init).isInstanceOf(RuntimeException.class).hasMessageContaining("COS客户端初始化失败");
    }

    @Test
    void init_cosMode_shouldCreateClientAndDestroy() {
        FileUploadService service = service("cos", "", "", BUCKET);
        service.init();
        service.destroy();
    }

    @Test
    void uploadFile_empty_shouldReject() {
        FileUploadService service = ready();
        MockMultipartFile empty = new MockMultipartFile("file", "a.pdf", "application/pdf", new byte[0]);
        assertThatThrownBy(() -> service.uploadFile(empty, "resume"))
                .isInstanceOf(BusinessException.class)
                .extracting("code")
                .isEqualTo(ErrorCode.FILE_EMPTY.getCode());
    }

    @Test
    void uploadFile_pdf_shouldStoreUnderUserPrefix() throws Exception {
        FileUploadService service = ready();
        COSClient cos = clientOf(service);
        PutObjectResult stored = new PutObjectResult();
        stored.setETag("etag-1");
        when(cos.putObject(any(PutObjectRequest.class))).thenReturn(stored);
        UserHolder.set(UserContext.of(USER_ID, "u"));
        MockMultipartFile file = new MockMultipartFile("file", "简历.pdf", "application/pdf", "pdf".getBytes());

        FileUploadResponse response = service.uploadFile(file, "resume");

        assertThat(response.getKey()).startsWith("user-8/resume/");
        assertThat(response.getKey()).endsWith(".pdf");
        assertThat(response.getUrl()).startsWith("https://demo-bucket.cos.ap-guangzhou.myqcloud.com/user-8/resume/");
        assertThat(response.getFileName()).isEqualTo("简历.pdf");
        assertThat(response.getContentType()).isEqualTo("application/pdf");
    }

    @Test
    void uploadFile_blankName_shouldUseGeneratedName() throws Exception {
        FileUploadService service = ready();
        when(clientOf(service).putObject(any(PutObjectRequest.class))).thenReturn(new PutObjectResult());
        UserHolder.set(UserContext.of(USER_ID, "u"));
        MockMultipartFile file = new MockMultipartFile("file", ".pdf", null, "x".getBytes());

        FileUploadResponse response = service.uploadFile(file, "resume");

        assertThat(response.getKey()).startsWith("user-8/resume/file-");
    }

    @Test
    void uploadFile_withoutContentType_shouldStillStore() throws Exception {
        FileUploadService service = ready();
        when(clientOf(service).putObject(any(PutObjectRequest.class))).thenReturn(new PutObjectResult());
        UserHolder.set(UserContext.of(USER_ID, "u"));
        MockMultipartFile file = new MockMultipartFile("file", "note", null, "txt".getBytes());

        FileUploadResponse response = service.uploadFile(file, null);

        assertThat(response.getKey()).startsWith("user-8/files/");
        assertThat(response.getContentType()).isNull();
    }

    @Test
    void uploadFile_streamFailure_shouldWrapIoException() throws Exception {
        FileUploadService service = ready();
        UserHolder.set(UserContext.of(USER_ID, "u"));
        MultipartFile file = mock(MultipartFile.class);
        when(file.isEmpty()).thenReturn(false);
        when(file.getOriginalFilename()).thenReturn("a.pdf");
        when(file.getInputStream()).thenThrow(new IOException("disk"));

        assertThatThrownBy(() -> service.uploadFile(file, "resume"))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("文件上传失败");
    }

    @Test
    void uploadFile_cosClientFailure_shouldWrap() throws Exception {
        FileUploadService service = ready();
        when(clientOf(service).putObject(any(PutObjectRequest.class))).thenThrow(new CosClientException("down"));
        UserHolder.set(UserContext.of(USER_ID, "u"));
        MockMultipartFile file = new MockMultipartFile("file", "a.pdf", "text/plain", "x".getBytes());

        assertThatThrownBy(() -> service.uploadFile(file, "resume"))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("文件上传失败");
    }

    @Test
    void deleteOwnedByUrl_blank_shouldIgnore() {
        ready().deleteOwnedByUrl("  ", "resume");
    }

    @Test
    void deleteOwnedByUrl_otherArea_shouldReject() throws Exception {
        FileUploadService service = ready();
        UserHolder.set(UserContext.of(USER_ID, "u"));
        String url = "https://demo-bucket.cos.ap-guangzhou.myqcloud.com/user-8/resume/a.pdf";

        assertThatThrownBy(() -> service.deleteOwnedByUrl(url, "avatar"))
                .isInstanceOf(BusinessException.class)
                .extracting("code")
                .isEqualTo(ErrorCode.NO_PERMISSION.getCode());
    }

    @Test
    void deleteOwnedByUrl_ownedKey_shouldDelete() throws Exception {
        FileUploadService service = ready();
        UserHolder.set(UserContext.of(USER_ID, "u"));
        String url = "https://demo-bucket.cos.ap-guangzhou.myqcloud.com/user-8/resume/a.pdf";

        service.deleteOwnedByUrl(url, "resume");

        verify(clientOf(service)).deleteObject(BUCKET, "user-8/resume/a.pdf");
    }

    @Test
    void deleteByKey_ownedKey_shouldDelete() throws Exception {
        FileUploadService service = ready();
        UserHolder.set(UserContext.of(USER_ID, "u"));

        service.deleteByKey("user-8/resume/a.pdf");

        verify(clientOf(service)).deleteObject(BUCKET, "user-8/resume/a.pdf");
    }

    @Test
    void deleteByKey_invalidPath_shouldReject() {
        FileUploadService service = ready();
        UserHolder.set(UserContext.of(USER_ID, "u"));
        assertThatThrownBy(() -> service.deleteByKey("../secret"))
                .isInstanceOf(BusinessException.class)
                .extracting("code")
                .isEqualTo(ErrorCode.VALIDATION_ERROR.getCode());
    }

    @Test
    void deleteByKey_otherUser_shouldReject() {
        FileUploadService service = ready();
        UserHolder.set(UserContext.of(USER_ID, "u"));
        assertThatThrownBy(() -> service.deleteByKey("user-9/resume/a.pdf"))
                .isInstanceOf(BusinessException.class)
                .extracting("code")
                .isEqualTo(ErrorCode.NO_PERMISSION.getCode());
    }

    @Test
    void listUploadedFiles_shouldSkipDirectoriesAndForeignKeys() throws Exception {
        FileUploadService service = ready();
        UserHolder.set(UserContext.of(USER_ID, "u"));
        ObjectListing first = listing(true, "next",
                summary("user-8/resume/a.pdf"),
                summary("user-8/resume/"),
                summary("user-9/resume/b.pdf"));
        ObjectListing second = listing(false, null, summary("user-8/avatar/c.png"));
        ObjectListing resumePage = listing(false, null, summary("user-8/resume/a.pdf"));
        when(clientOf(service).listObjects(any(ListObjectsRequest.class))).thenReturn(first, second, resumePage);

        List<FileUploadListItemResponse> all = service.listUploadedFiles(" ");
        List<FileUploadListItemResponse> resume = service.listUploadedFiles("resume");

        assertThat(all).extracting(FileUploadListItemResponse::getKey)
                .containsExactly("user-8/resume/a.pdf", "user-8/avatar/c.png");
        assertThat(all.get(0).getBizArea()).isEqualTo("resume");
        assertThat(all.get(0).getFileName()).isEqualTo("a.pdf");
        assertThat(resume).extracting(FileUploadListItemResponse::getBizArea).containsOnly("resume");
    }

    @Test
    void exists_missingObject_shouldReturnFalse() throws Exception {
        FileUploadService service = ready();
        UserHolder.set(UserContext.of(USER_ID, "u"));
        CosServiceException missing = new CosServiceException("missing");
        missing.setStatusCode(404);
        when(clientOf(service).getObjectMetadata(any())).thenThrow(missing);

        assertThat(service.exists("user-8/resume/a.pdf")).isFalse();
    }

    @Test
    void exists_serviceError_shouldWrap() throws Exception {
        FileUploadService service = ready();
        UserHolder.set(UserContext.of(USER_ID, "u"));
        CosServiceException denied = new CosServiceException("denied");
        denied.setStatusCode(403);
        when(clientOf(service).getObjectMetadata(any())).thenThrow(denied);

        assertThatThrownBy(() -> service.exists("user-8/resume/a.pdf"))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("检查文件是否存在失败");
    }

    @Test
    void exists_present_shouldReturnTrue() throws Exception {
        FileUploadService service = ready();
        UserHolder.set(UserContext.of(USER_ID, "u"));
        when(clientOf(service).getObjectMetadata(any())).thenReturn(null);

        assertThat(service.exists("user-8/resume/a.pdf")).isTrue();
    }

    @Test
    void isManagedCosUrl_shouldAcceptOnlyBucketHost() {
        FileUploadService service = ready();
        String url = "https://demo-bucket.cos.ap-guangzhou.myqcloud.com/user-8/resume/a.pdf";
        assertThat(service.isManagedCosUrl(url)).isTrue();
        assertThat(service.isManagedCosUrl("http://demo-bucket.cos.ap-guangzhou.myqcloud.com/a")).isFalse();
        assertThat(service.isManagedCosUrl("https://user:pw@demo-bucket.cos.ap-guangzhou.myqcloud.com/a")).isFalse();
        assertThat(service.isManagedCosUrl("https://other.example.com/a")).isFalse();
        assertThat(service.isManagedCosUrl(" ")).isFalse();
        assertThat(service.isManagedCosUrl("https://[")).isFalse();
    }

    @Test
    void openInputStream_ownedUrl_shouldReturnContent() throws Exception {
        FileUploadService service = ready();
        COSObject object = mock(COSObject.class);
        COSObjectInputStream content = mock(COSObjectInputStream.class);
        when(content.readAllBytes()).thenReturn("pdf".getBytes());
        when(object.getObjectContent()).thenReturn(content);
        when(clientOf(service).getObject(any(GetObjectRequest.class))).thenReturn(object);
        String url = "https://demo-bucket.cos.ap-guangzhou.myqcloud.com/user-8/resume/a.pdf";

        assertThat(service.openInputStreamFromUrl(url, USER_ID).readAllBytes()).isEqualTo("pdf".getBytes());
        UserHolder.set(UserContext.of(USER_ID, "u"));
        assertThat(service.openInputStreamFromUrl(url).readAllBytes()).isEqualTo("pdf".getBytes());
    }

    @Test
    void openInputStream_unmanagedOrAnonymous_shouldReject() {
        FileUploadService service = ready();
        assertThatThrownBy(() -> service.openInputStreamFromUrl("https://example.com/a", USER_ID))
                .isInstanceOf(BusinessException.class)
                .extracting("code")
                .isEqualTo(ErrorCode.URL_FORMAT_ERROR.getCode());
        assertThatThrownBy(() -> service.openInputStreamFromUrl(
                "https://demo-bucket.cos.ap-guangzhou.myqcloud.com/user-8/resume/a.pdf", null))
                .isInstanceOf(BusinessException.class)
                .extracting("code")
                .isEqualTo(ErrorCode.USER_NOT_LOGIN.getCode());
    }

    @Test
    void openInputStream_withoutClient_shouldFailFast() {
        FileUploadService service = service("cos", "id", "key", BUCKET);
        set(service, "region", REGION);
        assertThatThrownBy(() -> service.openInputStreamFromUrl("https://example.com/a", USER_ID))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("COS 客户端未初始化");
    }

    @Test
    void extractKeyFromUrl_shouldNormalizeOrReturnEmpty() {
        FileUploadService service = ready();
        assertThat(service.extractKeyFromUrl(" ")).isEmpty();
        assertThat(service.extractKeyFromUrl(
                "https://demo-bucket.cos.ap-guangzhou.myqcloud.com/user-8/resume/%E7%AE%80%E5%8E%86.pdf"))
                .isEqualTo("user-8/resume/简历.pdf");
        assertThat(service.extractKeyFromUrl("https://[")).isEmpty();
        assertThat(service.extractKeyFromUrl("user-8/../secret")).isEmpty();
    }

    private static FileUploadService ready() {
        FileUploadService service = service("cos", "id", "key", BUCKET);
        set(service, "region", REGION);
        set(service, "cosClient", mock(COSClient.class));
        return service;
    }

    private static FileUploadService service(String mode, String secretId, String secretKey, String bucket) {
        FileUploadService service = new FileUploadService();
        set(service, "storageMode", mode);
        set(service, "secretId", secretId);
        set(service, "secretKey", secretKey);
        set(service, "bucketName", bucket);
        set(service, "region", REGION);
        return service;
    }

    private static COSClient clientOf(FileUploadService service) throws Exception {
        Field field = FileUploadService.class.getDeclaredField("cosClient");
        field.setAccessible(true);
        return (COSClient) field.get(service);
    }

    private static void set(Object target, String name, Object value) {
        try {
            Field field = target.getClass().getDeclaredField(name);
            field.setAccessible(true);
            field.set(target, value);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
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
}
