package com.zdmj.python.constant;

/**
 * Python服务API接口路径常量
 * 统一管理Java端调用Python端的API接口路径
 */
public class PythonAPI {

    /**
     * 知识库相关API
     */
    public static class Knowledge {
        /** 创建知识库向量化任务 */
        public static final String EMBEDDING = "/api/knowledge/embedding";

        /** 删除知识库向量 */
        public static final String DELETE_VECTORS = "/api/knowledge/vectors/delete";

        /** 查询向量化任务状态（需要拼接taskId） */
        public static final String EMBEDDING_TASK_STATUS = "/api/knowledge/embedding/tasks/";
    }

    /**
     * 项目分析相关API
     */
    public static class Project {
        /** 创建项目分析任务 */
        public static final String ANALYZE = "/api/project/analyze";

        /** 查询项目分析任务状态（需要拼接taskId） */
        public static final String ANALYZE_TASK_STATUS = "/api/project/analyze/tasks/";
    }
}
