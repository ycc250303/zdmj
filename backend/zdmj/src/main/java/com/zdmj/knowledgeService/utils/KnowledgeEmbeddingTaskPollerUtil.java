package com.zdmj.knowledgeService.utils;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zdmj.common.client.KnowledgeVectorApiClient;
import com.zdmj.common.config.PythonApiConfig;
import com.zdmj.knowledgeService.dto.KnowledgeEmbeddingTaskDTO;
import com.zdmj.knowledgeService.entity.KnowledgeBases;
import com.zdmj.knowledgeService.mapper.KnowledgeBasesMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import jakarta.annotation.PreDestroy;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicBoolean;

@Slf4j
@Component
@RequiredArgsConstructor
public class KnowledgeEmbeddingTaskPollerUtil {

    // 需要被轮询的“进行中”状态；终态任务不会再次查询 Python。
    private static final Set<String> ACTIVE_STATUSES = Set.of("PENDING", "RUNNING");
    // 单次扫描上限，避免一次轮询拉取过多记录导致 DB/网络抖动。
    private static final int BATCH_LIMIT = 100;

    private final KnowledgeBasesMapper knowledgeBasesMapper;
    private final KnowledgeVectorApiClient knowledgeVectorApiClient;
    private final PythonApiConfig pythonApiConfig;

    // 进程内互斥开关：确保同一时刻最多只有一个轮询循环在跑。
    private final AtomicBoolean polling = new AtomicBoolean(false);
    // 专用单线程执行器，避免占用公共线程池并保证轮询串行。
    private final ExecutorService pollingExecutor = Executors.newSingleThreadExecutor(new PollerThreadFactory());

    /**
     * 按需触发轮询入口。
     * 由 create/update/delete 在成功提交任务后调用；
     * 如果已有轮询在跑，本次触发直接忽略。
     */
    public void triggerPolling(String reason) {
        if (!polling.compareAndSet(false, true)) {
            log.debug("向量任务轮询已在运行，本次触发忽略。reason={}", reason);
            return;
        }
        pollingExecutor.execute(() -> runPollingLoop(reason));
    }

    /**
     * 轮询主循环：
     * 1) 每轮扫描进行中任务并同步状态
     * 2) 若无活跃任务则停止
     * 3) 否则按配置间隔 sleep 后继续下一轮
     */
    private void runPollingLoop(String reason) {
        log.info("启动按需向量任务轮询。reason={}", reason);
        try {
            while (true) {
                int activeCount = pollOnce();
                if (activeCount == 0) {
                    log.info("当前无待处理向量任务，停止轮询。");
                    break;
                }

                try {
                    Thread.sleep(Math.max(1000, pythonApiConfig.getPollIntervalMs()));
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    log.warn("向量任务轮询线程被中断，提前结束。");
                    break;
                }
            }
        } catch (Exception e) {
            log.error("按需向量任务轮询异常中止: {}", e.getMessage(), e);
        } finally {
            polling.set(false);
            // 防止“停止瞬间又有新任务写入”导致漏轮询，做一次补偿重启检查。
            if (hasActiveTasks() && polling.compareAndSet(false, true)) {
                log.info("检测到新待处理任务，补偿重启向量轮询。");
                pollingExecutor.execute(() -> runPollingLoop("recheck-restart"));
            }
        }
    }

    /**
     * 执行单轮轮询：
     * - 查询本地 knowledge_bases 中处于 PENDING/RUNNING 的任务
     * - 调 Python 查询任务状态
     * - 将终态或进度状态回写本地表
     *
     * @return 当前仍处于活跃状态的任务数（用于主循环判断是否继续）
     */
    private int pollOnce() {
        List<KnowledgeBases> pendingTasks = knowledgeBasesMapper.selectPendingVectorTasks(BATCH_LIMIT);

        if (pendingTasks == null || pendingTasks.isEmpty()) {
            return 0;
        }

        for (KnowledgeBases kb : pendingTasks) {
            String taskId = kb.getVectorTaskId();
            if (taskId == null || taskId.isBlank()) {
                continue;
            }

            try {
                KnowledgeEmbeddingTaskDTO task = knowledgeVectorApiClient.getTaskStatus(taskId);
                String status = task.getStatus();
                if (status == null || status.isBlank()) {
                    continue;
                }

                // dirty=true 才落库，避免每轮无变化也 update。
                boolean dirty = false;
                if ("RUNNING".equals(status) || "PENDING".equals(status)) {
                    if (!status.equals(kb.getVectorTaskStatus())) {
                        kb.setVectorTaskStatus(status);
                        dirty = true;
                    }
                } else if ("SUCCESS".equals(status)) {
                    if (!"SUCCESS".equals(kb.getVectorTaskStatus())) {
                        kb.setVectorTaskStatus("SUCCESS");
                        dirty = true;
                    }
                    List<Long> newVectorIds = task.getVectorIds() == null ? new ArrayList<>() : task.getVectorIds();
                    if (kb.getVectorIds() == null || !kb.getVectorIds().equals(newVectorIds)) {
                        kb.setVectorIds(newVectorIds);
                        dirty = true;
                    }
                } else if ("FAILED".equals(status) || "CANCELLED".equals(status)) {
                    if (!status.equals(kb.getVectorTaskStatus())) {
                        kb.setVectorTaskStatus(status);
                        dirty = true;
                    }
                } else {
                    log.warn("未知任务状态，跳过更新。knowledgeId={}, taskId={}, status={}", kb.getId(), taskId, status);
                    continue;
                }

                if (dirty) {
                    int rows = knowledgeBasesMapper.updateById(kb);
                    if (rows <= 0) {
                        log.warn("轮询状态更新失败。knowledgeId={}, taskId={}", kb.getId(), taskId);
                    }
                }
            } catch (Exception e) {
                log.error("查询向量任务状态失败。knowledgeId={}, taskId={}, error={}",
                        kb.getId(), taskId, e.getMessage());
            }
        }

        return countActiveTasks();
    }

    /**
     * 是否存在活跃任务（进行中任务）。
     */
    private boolean hasActiveTasks() {
        return countActiveTasks() > 0;
    }

    /**
     * 查询当前活跃任务总数。
     */
    private int countActiveTasks() {
        Long count = knowledgeBasesMapper.selectCount(
                new LambdaQueryWrapper<KnowledgeBases>()
                        .isNotNull(KnowledgeBases::getVectorTaskId)
                        .in(KnowledgeBases::getVectorTaskStatus, ACTIVE_STATUSES));
        return count == null ? 0 : count.intValue();
    }

    /**
     * Spring 关闭容器时释放轮询线程，避免残留后台线程。
     */
    @PreDestroy
    public void shutdown() {
        pollingExecutor.shutdownNow();
    }

    /**
     * 轮询线程工厂：统一线程名，便于日志和监控定位。
     */
    private static class PollerThreadFactory implements ThreadFactory {
        @Override
        public Thread newThread(Runnable r) {
            Thread thread = new Thread(r, "knowledge-vector-poller");
            thread.setDaemon(true);
            return thread;
        }
    }
}
