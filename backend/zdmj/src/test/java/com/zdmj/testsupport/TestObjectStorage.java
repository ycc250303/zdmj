package com.zdmj.testsupport;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

import com.qcloud.cos.COSClient;
import com.qcloud.cos.exception.CosServiceException;
import com.qcloud.cos.model.*;
import com.zdmj.common.storage.FileUploadService;
import java.io.ByteArrayInputStream;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.CopyOnWriteArrayList;
import org.apache.http.client.methods.HttpGet;

/** 复用生产文件规则，仅通过内存模拟 COS 客户端。 */
public class TestObjectStorage extends FileUploadService {
    public static final String URL_PREFIX = "https://test-bucket.cos.test-region.myqcloud.com/";
    private final List<String> calls = new CopyOnWriteArrayList<>();
    private final ConcurrentMap<String, byte[]> objects = new ConcurrentHashMap<>();

    public TestObjectStorage() {
        this(mock(COSClient.class));
    }

    private TestObjectStorage(COSClient client) {
        super(client, "test-bucket", "test-region");
        when(client.putObject(any(PutObjectRequest.class))).thenAnswer(invocation -> {
            PutObjectRequest request = invocation.getArgument(0);
            objects.put(request.getKey(), request.getInputStream().readAllBytes());
            calls.add("upload:" + request.getKey());
            return new PutObjectResult();
        });
        doAnswer(invocation -> {
            String key = invocation.getArgument(1);
            objects.remove(key);
            calls.add("delete:" + key);
            return null;
        }).when(client).deleteObject(anyString(), anyString());
        when(client.getObjectMetadata(any(GetObjectMetadataRequest.class))).thenAnswer(invocation -> {
            GetObjectMetadataRequest request = invocation.getArgument(0);
            requireObject(request.getKey());
            calls.add("exists:" + request.getKey());
            return new ObjectMetadata();
        });
        when(client.getObject(any(GetObjectRequest.class))).thenAnswer(invocation -> {
            GetObjectRequest request = invocation.getArgument(0);
            COSObject object = new COSObject();
            object.setObjectContent(new COSObjectInputStream(
                    new ByteArrayInputStream(requireObject(request.getKey())), new HttpGet()));
            calls.add("open:" + request.getKey());
            return object;
        });
        when(client.listObjects(any(ListObjectsRequest.class))).thenAnswer(invocation -> {
            ListObjectsRequest request = invocation.getArgument(0);
            List<String> keys = objects.keySet().stream().filter(key -> key.startsWith(request.getPrefix()))
                    .filter(key -> request.getMarker() == null || key.compareTo(request.getMarker()) > 0)
                    .sorted().toList();
            ObjectListing listing = new ObjectListing();
            int count = Math.min(keys.size(), request.getMaxKeys());
            for (String key : keys.subList(0, count)) {
                COSObjectSummary summary = new COSObjectSummary();
                summary.setKey(key);
                listing.getObjectSummaries().add(summary);
            }
            listing.setTruncated(count < keys.size());
            if (listing.isTruncated()) {
                listing.setNextMarker(keys.get(count - 1));
            }
            calls.add("list:" + request.getPrefix());
            return listing;
        });
    }

    public List<String> calls() {
        return List.copyOf(calls);
    }

    public void clear() {
        calls.clear();
        objects.clear();
    }

    private byte[] requireObject(String key) {
        byte[] bytes = objects.get(key);
        if (bytes == null) {
            CosServiceException exception = new CosServiceException("对象不存在");
            exception.setStatusCode(404);
            throw exception;
        }
        return bytes;
    }
}
