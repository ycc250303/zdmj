package com.zdmj.common.ai;

/**
 * 按岗位方向拆分的提示词场景。路径约定 {@code {directory}/{slug}}，缺文件回退 {@code {directory}/default}。
 */
public enum PromptScenario {

    RESUME_ANALYSIS("resume-analysis"),
    JOB_REQUIREMENT("job-requirement"),
    JOB_CAREER_GRAPH("job-career-graph"),
    JOB_STUDENT_MATCH("job-student-match");

    private final String directory;

    PromptScenario(String directory) {
        this.directory = directory;
    }

    public String directory() {
        return directory;
    }

    public String path(JobRole role) {
        JobRole resolved = role == null ? JobRole.UNKNOWN : role;
        return directory + "/" + resolved.slug();
    }

    public String defaultPath() {
        return directory + "/default";
    }
}
