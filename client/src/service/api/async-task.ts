import { request } from '../request';

/**
 * 异步 LLM 任务（对齐后端 AsyncTaskDTO / GET /async-tasks/{id}）
 */
export namespace AsyncTaskApi {
  /** 1 排队 / 2 执行中 / 3 成功 / 4 失败 */
  export type TaskStatus = 1 | 2 | 3 | 4;

  export const PENDING = 1 as const;
  export const RUNNING = 2 as const;
  export const SUCCESS = 3 as const;
  export const FAILED = 4 as const;

  export interface AsyncTask {
    taskId: number;
    taskType?: number;
    status: TaskStatus | number;
    errorMessage?: string | null;
    /** 简历识别等无独立业务表时的 JSON 字符串 */
    result?: string | null;
    startedAt?: string | null;
    completedAt?: string | null;
  }
}

/** 查询本人任务。FAILED 仍 HTTP 200 / code=0。 */
export function fetchGetAsyncTask(taskId: number | string) {
  return request<AsyncTaskApi.AsyncTask>({
    url: `/async-tasks/${taskId}`,
    method: 'get'
  });
}
