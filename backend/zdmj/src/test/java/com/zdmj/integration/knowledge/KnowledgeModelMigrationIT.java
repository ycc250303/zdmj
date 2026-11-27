package com.zdmj.integration.knowledge;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/** 使用独立数据库验证知识文档结构脚本及数据库约束。 */
class KnowledgeModelMigrationIT {
    private static final PostgreSQLContainer<?> DATABASE = new PostgreSQLContainer<>(
            DockerImageName.parse("pgvector/pgvector:pg15").asCompatibleSubstituteFor("postgres"));
    private static String migration;

    @BeforeAll
    static void start() throws Exception {
        migration = Files.readString(Path.of(System.getProperty("zdmj.repo.root"))
                .resolve("sql/migrations/20261126_remove_knowledge_bases.sql"));
        DATABASE.start();
    }

    @AfterAll
    static void stop() { DATABASE.stop(); }

    @BeforeEach
    void initialize() throws Exception {
        try (Connection connection = connect()) {
            execute(connection, """
                    DROP SCHEMA public CASCADE;
                    CREATE SCHEMA public;
                    CREATE EXTENSION vector;
                    CREATE TABLE knowledge_bases(id BIGSERIAL PRIMARY KEY,user_id BIGINT,scope SMALLINT);
                    CREATE TABLE knowledge_documents(
                        id BIGSERIAL PRIMARY KEY,knowledge_id BIGINT NOT NULL,user_id BIGINT NOT NULL DEFAULT 0,
                        type SMALLINT NOT NULL,content TEXT NOT NULL,file_status SMALLINT DEFAULT 1,
                        embedding_status SMALLINT DEFAULT 1,created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP);
                    CREATE INDEX idx_knowledge_documents_user_id ON knowledge_documents(user_id);
                    CREATE INDEX idx_knowledge_documents_type ON knowledge_documents(type);
                    CREATE UNIQUE INDEX uk_knowledge_documents_kid_content ON knowledge_documents(knowledge_id,content);
                    CREATE TABLE knowledge_vectors(
                        id BIGSERIAL PRIMARY KEY,knowledge_id BIGINT NOT NULL,user_id BIGINT NOT NULL DEFAULT 0,
                        document_id BIGINT,chunk_index INTEGER,embedding VECTOR(1024) NOT NULL);
                    CREATE INDEX idx_knowledge_vectors_document_id ON knowledge_vectors(document_id);
                    CREATE INDEX idx_knowledge_vectors_embedding ON knowledge_vectors USING HNSW(embedding vector_cosine_ops);
                    CREATE UNIQUE INDEX uk_knowledge_vectors_kid_did_chunk ON knowledge_vectors(knowledge_id,document_id,chunk_index);
                    """);
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void 空表结构变更_兼容历史索引名称并建立范围约束(boolean legacyIndexes) throws Exception {
        try (Connection connection = connect()) {
            if (legacyIndexes) {
                execute(connection, """
                        ALTER INDEX idx_knowledge_documents_type RENAME TO idx_knowledge_documents_source_type;
                        ALTER INDEX uk_knowledge_documents_kid_content RENAME TO uk_knowledge_documents_kid_uri;
                        CREATE INDEX idx_knowledge_documents_knowledge_id ON knowledge_documents(knowledge_id);
                        CREATE INDEX idx_knowledge_vectors_knowledge_id ON knowledge_vectors(knowledge_id);
                        CREATE INDEX idx_knowledge_vectors_user_id ON knowledge_vectors(user_id);
                        """);
            }
            execute(connection, migration);
            assertThat(rows(connection, "SELECT to_regclass('public.knowledge_bases') IS NULL")).containsExactly("t");
            assertThat(rows(connection, "SELECT count(*) FROM information_schema.columns WHERE table_schema='public' AND ((table_name='knowledge_documents' AND column_name='knowledge_id') OR (table_name='knowledge_vectors' AND column_name IN('knowledge_id','user_id')))"))
                    .containsExactly("0");
            assertThat(rows(connection, "SELECT tablename||':'||count(*) FROM pg_indexes WHERE schemaname='public' AND tablename IN('knowledge_documents','knowledge_vectors') GROUP BY tablename ORDER BY tablename"))
                    .containsExactly("knowledge_documents:5", "knowledge_vectors:3");
            for (String values : List.of("(1,NULL)", "(1,0)", "(2,101)", "(3,101)", "(4,NULL)")) {
                assertThatThrownBy(() -> execute(connection, "INSERT INTO knowledge_documents(scope,user_id,type,content) SELECT scope,owner::bigint,1,'invalid' FROM (VALUES " + values + ") v(scope,owner)"))
                        .isInstanceOf(SQLException.class).hasMessageContaining("ck_knowledge_documents_scope_owner");
            }
            execute(connection, "INSERT INTO knowledge_documents(scope,user_id,type,content) VALUES(1,101,1,'same'),(1,102,1,'same'),(2,NULL,1,'same'),(3,NULL,1,'same')");
            assertThatThrownBy(() -> execute(connection, "INSERT INTO knowledge_documents(scope,user_id,type,content) VALUES(1,101,1,'same')"))
                    .isInstanceOf(SQLException.class).hasMessageContaining("uk_knowledge_documents_user_content");
            assertThatThrownBy(() -> execute(connection, "INSERT INTO knowledge_documents(scope,type,content) VALUES(2,1,'same')"))
                    .isInstanceOf(SQLException.class).hasMessageContaining("uk_knowledge_documents_system_content");
            execute(connection, "INSERT INTO knowledge_vectors(document_id,chunk_index,embedding) VALUES(1,0,'[1," + "0,".repeat(1022) + "0]')");
            assertThatThrownBy(() -> execute(connection, "UPDATE knowledge_vectors SET chunk_index=-1"))
                    .isInstanceOf(SQLException.class).hasMessageContaining("ck_knowledge_vectors_chunk_index");
            assertThatThrownBy(() -> execute(connection, "UPDATE knowledge_vectors SET document_id=NULL"))
                    .isInstanceOf(SQLException.class).hasMessageContaining("not-null");
            assertThatThrownBy(() -> execute(connection, "INSERT INTO knowledge_vectors(document_id,chunk_index,embedding) SELECT document_id,chunk_index,embedding FROM knowledge_vectors"))
                    .isInstanceOf(SQLException.class).hasMessageContaining("uk_knowledge_vectors_document_chunk");
        }
    }

    @Test
    void 已有文档缺少范围_结构变更失败并完整回滚() throws Exception {
        try (Connection connection = connect()) {
            execute(connection, "INSERT INTO knowledge_bases(id,user_id,scope) VALUES(1,101,1); INSERT INTO knowledge_documents(knowledge_id,user_id,type,content) VALUES(1,101,1,'existing')");
            assertThatThrownBy(() -> execute(connection, migration)).isInstanceOf(SQLException.class).hasMessageContaining("null values");
            execute(connection, "ROLLBACK");
            assertThat(rows(connection, "SELECT content FROM knowledge_documents")).containsExactly("existing");
            assertThat(rows(connection, "SELECT count(*) FROM knowledge_bases")).containsExactly("1");
            assertThat(rows(connection, "SELECT count(*) FROM information_schema.columns WHERE table_name='knowledge_documents' AND column_name='scope'"))
                    .containsExactly("0");
        }
    }

    @Test
    void 已有数据满足目标范围_结构变更保留文档与向量内容() throws Exception {
        try (Connection connection = connect()) {
            execute(connection, "ALTER TABLE knowledge_documents ADD COLUMN scope SMALLINT; INSERT INTO knowledge_bases(id,user_id,scope) VALUES(1,101,1); INSERT INTO knowledge_documents(id,knowledge_id,user_id,type,content,scope) VALUES(1,1,101,1,'existing',1)");
            execute(connection, "INSERT INTO knowledge_vectors(knowledge_id,user_id,document_id,chunk_index,embedding) VALUES(1,101,1,0,'[1," + "0,".repeat(1022) + "0]')");
            List<String> before = rows(connection, "SELECT id||':'||document_id||':'||chunk_index||':'||embedding::text FROM knowledge_vectors");
            execute(connection, migration);
            assertThat(rows(connection, "SELECT id||':'||user_id||':'||scope||':'||content FROM knowledge_documents"))
                    .containsExactly("1:101:1:existing");
            assertThat(rows(connection, "SELECT id||':'||document_id||':'||chunk_index||':'||embedding::text FROM knowledge_vectors"))
                    .isEqualTo(before);
        }
    }

    @ParameterizedTest
    @org.junit.jupiter.params.provider.MethodSource("historicalScripts")
    void 历史脚本仅变更目标结构(String file, String fixture, String verification, String expected) throws Exception {
        try (Connection connection = connect()) {
            execute(connection, fixture);
            String script = Files.readString(Path.of(System.getProperty("zdmj.repo.root"), "sql/migrations", file));
            execute(connection, "BEGIN;\n" + script + "\nCOMMIT;");
            assertThat(rows(connection, verification)).containsExactly(expected);
        }
    }

    private static java.util.stream.Stream<org.junit.jupiter.params.provider.Arguments> historicalScripts() {
        return java.util.stream.Stream.of(
                org.junit.jupiter.params.provider.Arguments.of("20260612_resume_single_user_drop_visible.sql",
                        "CREATE TABLE resumes(id BIGINT,user_id BIGINT); CREATE TABLE educations(visible BOOLEAN); CREATE TABLE careers(visible BOOLEAN); CREATE TABLE project_experiences(visible BOOLEAN); INSERT INTO resumes VALUES(1,101)",
                        "SELECT count(*) FROM resumes", "1"),
                org.junit.jupiter.params.provider.Arguments.of("20260905_job_profile_user_isolation.sql",
                        "CREATE TABLE job_capability_profiles(id BIGINT,job_id BIGINT)",
                        "SELECT is_nullable FROM information_schema.columns WHERE table_name='job_capability_profiles' AND column_name='user_id'", "NO"),
                org.junit.jupiter.params.provider.Arguments.of("20261106_career_report_single_row.sql",
                        "CREATE TABLE career_development_reports(id BIGINT,user_id BIGINT,job_id BIGINT,version INTEGER,is_latest BOOLEAN); INSERT INTO career_development_reports VALUES(1,101,1,1,true)",
                        "SELECT indexname FROM pg_indexes WHERE tablename='career_development_reports'", "uk_career_reports_user_job"),
                org.junit.jupiter.params.provider.Arguments.of("20261109_drop_resumes.sql",
                        "CREATE TABLE resumes(id BIGINT,user_id BIGINT,skill_id BIGINT); CREATE TABLE resume_matches(id BIGINT); CREATE TABLE skills(id BIGINT,user_id BIGINT); INSERT INTO resumes VALUES(1,101,1); INSERT INTO skills VALUES(1,101),(2,101)",
                        "SELECT (to_regclass('resumes') IS NULL AND to_regclass('resume_matches') IS NULL)::text||':'||count(*) FROM skills", "true:2"),
                org.junit.jupiter.params.provider.Arguments.of("20261110_skills_name_description.sql",
                        "CREATE TABLE skills(id BIGSERIAL PRIMARY KEY,user_id BIGINT NOT NULL,content JSONB)",
                        "SELECT string_agg(column_name,',' ORDER BY ordinal_position) FROM information_schema.columns WHERE table_name='skills'", "id,user_id,name,description"),
                org.junit.jupiter.params.provider.Arguments.of("20261111_merge_job_description.sql",
                        "CREATE TABLE jobs(id BIGINT,description TEXT,content JSONB,requirements JSONB,content_embedding VECTOR(1024),requirements_embedding VECTOR(1024)); INSERT INTO jobs(id,description,content,requirements) VALUES(1,'现有描述','[\"职责\"]','[\"要求\"]')",
                        "SELECT description FROM jobs", "现有描述"),
                org.junit.jupiter.params.provider.Arguments.of("20261112_drop_knowledge_vector_tasks.sql",
                        "ALTER TABLE knowledge_documents ADD COLUMN metadata JSONB DEFAULT '{}'::jsonb; CREATE TABLE knowledge_vector_tasks(id BIGINT)",
                        "SELECT (to_regclass('knowledge_vector_tasks') IS NULL)::text||':'||is_nullable FROM information_schema.columns WHERE table_name='knowledge_documents' AND column_name='metadata'", "true:NO"));
    }

    @Test
    void 结构脚本目录只包含结构操作() throws Exception {
        Path directory = Path.of(System.getProperty("zdmj.repo.root"), "sql/migrations");
        try (var paths = Files.walk(directory)) {
            for (Path path : paths.filter(file -> file.toString().endsWith(".sql")).toList()) {
                String sql = Files.readString(path).replaceAll("(?m)--[^\\r\\n]*", "");
                assertThat(sql).as("结构脚本 %s", path.getFileName())
                        .doesNotContainPattern("(?i)\\b(UPDATE|INSERT|DELETE|MERGE|COPY|TRUNCATE)\\b");
            }
        }
    }

    private static Connection connect() throws SQLException {
        return DriverManager.getConnection(DATABASE.getJdbcUrl(), DATABASE.getUsername(), DATABASE.getPassword());
    }

    private static void execute(Connection connection, String sql) throws SQLException {
        try (var statement = connection.createStatement()) { statement.execute(sql); }
    }

    private static List<String> rows(Connection connection, String sql) throws SQLException {
        List<String> rows = new ArrayList<>();
        try (var statement = connection.createStatement(); var result = statement.executeQuery(sql)) {
            while (result.next()) { rows.add(result.getString(1)); }
        }
        return rows;
    }
}
