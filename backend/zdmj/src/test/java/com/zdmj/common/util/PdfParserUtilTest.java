package com.zdmj.common.util;

import com.qcloud.cos.COSClient;
import com.qcloud.cos.ClientConfig;
import com.qcloud.cos.auth.BasicCOSCredentials;
import com.qcloud.cos.auth.COSCredentials;
import com.qcloud.cos.model.COSObjectInputStream;
import com.qcloud.cos.region.Region;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.apache.http.client.methods.HttpGet;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.lang.reflect.Field;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mockStatic;

class PdfParserUtilTest {
    private static final String LOCAL_RESUME_PDF = "D:\\GitHub\\ycc\\Resume_Template\\ycc.pdf";

    @Test
    void extractTextFromCosKey_shouldParsePdfText() throws Exception {
        byte[] pdfBytes = createPdfBytes("Hello PDF Parser");
        COSObjectInputStream cosStream = new COSObjectInputStream(
                new ByteArrayInputStream(pdfBytes),
                new HttpGet("http://localhost/mock.pdf"));

        try (MockedStatic<CosUtil> cosUtil = mockStatic(CosUtil.class)) {
            cosUtil.when(() -> CosUtil.getObjectInputStream("user-1/knowledge/demo.pdf")).thenReturn(cosStream);

            String parsedText = PdfParserUtil.extractTextFromCosKey("user-1/knowledge/demo.pdf");

            assertTrue(parsedText.contains("Hello PDF Parser"));
        }
    }

    @Test
    void extractTextFromCosKey_shouldWrapException() {
        try (MockedStatic<CosUtil> cosUtil = mockStatic(CosUtil.class)) {
            cosUtil.when(() -> CosUtil.getObjectInputStream("bad-key"))
                    .thenThrow(new RuntimeException("not found"));

            RuntimeException ex = assertThrows(RuntimeException.class,
                    () -> PdfParserUtil.extractTextFromCosKey("bad-key"));

            assertTrue(ex.getMessage().startsWith("PDF解析失败："));
        }
    }

    @Test
    void extractTextFromLocalPdf_shouldParseText() {
        Path pdfPath = Path.of(LOCAL_RESUME_PDF);
        Assumptions.assumeTrue(Files.exists(pdfPath), "本地PDF不存在，跳过该测试: " + LOCAL_RESUME_PDF);

        String text = PdfParserUtil.extractTextFromLocalPath(LOCAL_RESUME_PDF);
        System.out.println("[LOCAL PDF] extracted text:\n" + preview(text));
        assertTrue(text != null && !text.isBlank(), "本地PDF解析结果不应为空");
    }

    @Test
    void extractTextFromCosKey_realKey_shouldParseText() {
        String cosKey = System.getProperty("test.cos.key");
        Assumptions.assumeTrue(cosKey != null && !cosKey.isBlank(),
                "未提供 -Dtest.cos.key，跳过真实COS解析测试");
        initRealCosClient();

        String text = PdfParserUtil.extractTextFromCosKey(cosKey);
        System.out.println("[COS PDF] key=" + cosKey + "\n" + preview(text));
        assertTrue(text != null && !text.isBlank(), "COS PDF解析结果不应为空");
    }

    private byte[] createPdfBytes(String text) throws Exception {
        try (PDDocument doc = new PDDocument();
                ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            PDPage page = new PDPage();
            doc.addPage(page);
            try (PDPageContentStream contentStream = new PDPageContentStream(doc, page)) {
                contentStream.beginText();
                contentStream.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 12);
                contentStream.newLineAtOffset(100, 700);
                contentStream.showText(text);
                contentStream.endText();
            }
            doc.save(out);
            return out.toByteArray();
        }
    }

    private String preview(String text) {
        if (text == null) {
            return "(null)";
        }
        int maxLen = 1000;
        if (text.length() <= maxLen) {
            return text;
        }
        return text.substring(0, maxLen) + "\n... [truncated, total=" + text.length() + "]";
    }

    /**
     * 初始化真实COS客户端（仅真实联调测试用）
     * 优先读取 JVM 参数，其次读取环境变量
     */
    private void initRealCosClient() {
        String secretId = readArgOrEnv("test.cos.secretId", "COS_SECRET_ID");
        String secretKey = readArgOrEnv("test.cos.secretKey", "COS_SECRET_KEY");
        String region = readArgOrEnv("test.cos.region", "COS_REGION");
        String bucketName = readArgOrEnv("test.cos.bucket", "COS_BUCKET_NAME");

        Assumptions.assumeTrue(notBlank(secretId), "未提供 COS_SECRET_ID 或 -Dtest.cos.secretId，跳过");
        Assumptions.assumeTrue(notBlank(secretKey), "未提供 COS_SECRET_KEY 或 -Dtest.cos.secretKey，跳过");
        Assumptions.assumeTrue(notBlank(region), "未提供 COS_REGION 或 -Dtest.cos.region，跳过");
        Assumptions.assumeTrue(notBlank(bucketName), "未提供 COS_BUCKET_NAME 或 -Dtest.cos.bucket，跳过");

        COSCredentials cred = new BasicCOSCredentials(secretId, secretKey);
        COSClient client = new COSClient(cred, new ClientConfig(new Region(region)));
        setStaticField(CosUtil.class, "cosClient", client);
        setStaticField(CosUtil.class, "staticBucketName", bucketName);
        setStaticField(CosUtil.class, "staticRegion", region);
    }

    private String readArgOrEnv(String jvmKey, String envKey) {
        String fromJvm = System.getProperty(jvmKey);
        if (notBlank(fromJvm)) {
            return fromJvm;
        }
        return System.getenv(envKey);
    }

    private void setStaticField(Class<?> clazz, String fieldName, Object value) {
        try {
            Field field = clazz.getDeclaredField(fieldName);
            field.setAccessible(true);
            field.set(null, value);
        } catch (Exception e) {
            throw new RuntimeException("设置静态字段失败: " + fieldName, e);
        }
    }

    private boolean notBlank(String value) {
        return value != null && !value.isBlank();
    }
}
