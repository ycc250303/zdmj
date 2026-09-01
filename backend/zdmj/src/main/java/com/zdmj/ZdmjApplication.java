package com.zdmj;

import org.mybatis.spring.annotation.MapperScan;
import org.apache.ibatis.annotations.Mapper;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication(scanBasePackages = "com.zdmj")
@MapperScan(value = {
        "com.zdmj.userAuthService.mapper",
        "com.zdmj.resumeService.mapper",
        "com.zdmj.knowledgeService.mapper",
        "com.zdmj.jobService.mapper",
        "com.zdmj.conversationService.mapper",
        "com.zdmj.matchService.mapper",
        "com.zdmj.careerReportService.mapper"
}, annotationClass = Mapper.class)
public class ZdmjApplication {
    public static void main(String[] args) {
        // #region agent log
        boolean port8080InUse = probeTcp("127.0.0.1", 8080);
        boolean envExists = java.nio.file.Files.exists(
                java.nio.file.Path.of("/Users/yinchengcheng/Documents/GitHub/ycc/zdmj/.env"));
        boolean pgOpen = probeTcp("111.229.81.45", 5432);
        boolean redisOpen = probeTcp("111.229.81.45", 6379);
        agentLog("A", "pre-start-checks",
                "{\"port8080InUse\":" + port8080InUse
                        + ",\"envExists\":" + envExists
                        + ",\"pgOpen\":" + pgOpen
                        + ",\"redisOpen\":" + redisOpen + "}");
        try {
            SpringApplication.run(ZdmjApplication.class, args);
            agentLog("A", "start-success", "{\"ok\":true}");
        } catch (Throwable t) {
            Throwable root = t;
            while (root.getCause() != null && root.getCause() != root) {
                root = root.getCause();
            }
            agentLog("A", "start-failed",
                    "{\"ex\":\"" + t.getClass().getName()
                            + "\",\"msg\":\"" + jsonEscape(t.getMessage())
                            + "\",\"rootEx\":\"" + root.getClass().getName()
                            + "\",\"rootMsg\":\"" + jsonEscape(root.getMessage()) + "\"}");
            throw t;
        }
        // #endregion
    }

    // #region agent log
    private static boolean probeTcp(String host, int port) {
        try (java.net.Socket socket = new java.net.Socket()) {
            socket.connect(new java.net.InetSocketAddress(host, port), 400);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private static String jsonEscape(String raw) {
        if (raw == null) {
            return "";
        }
        return raw.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", " ").replace("\r", " ");
    }

    private static void agentLog(String hypothesisId, String message, String dataJson) {
        try {
            String line = "{\"sessionId\":\"a14696\",\"runId\":\"post-fix\",\"hypothesisId\":\""
                    + hypothesisId + "\",\"location\":\"ZdmjApplication.java:main\",\"message\":\""
                    + message + "\",\"data\":" + dataJson + ",\"timestamp\":" + System.currentTimeMillis() + "}\n";
            try (java.io.FileWriter fw = new java.io.FileWriter(
                    "/Users/yinchengcheng/Documents/GitHub/ycc/zdmj/.cursor/debug-a14696.log", true)) {
                fw.write(line);
            }
        } catch (Exception ignored) {
        }
    }
    // #endregion
}
