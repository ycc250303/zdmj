import { fetchGetAsyncTask, AsyncTaskApi } from '@/service/api/async-task';

const DEFAULT_INTERVAL_MS = 2000;
const DEFAULT_TIMEOUT_MS = 10 * 60 * 1000;

export class AsyncTaskFailedError extends Error {
  readonly task: AsyncTaskApi.AsyncTask;

  constructor(task: AsyncTaskApi.AsyncTask) {
    super(task.errorMessage || '任务执行失败');
    this.name = 'AsyncTaskFailedError';
    this.task = task;
  }
}

function sleep(ms: number) {
  return new Promise(resolve => setTimeout(resolve, ms));
}

/**
 * 仅在 PENDING/RUNNING 时每 2s 静默轮询任务；终态返回。不弹每次请求的 toast。
 */
export async function waitForAsyncTask(
  taskId: number,
  options?: { intervalMs?: number; timeoutMs?: number }
): Promise<AsyncTaskApi.AsyncTask> {
  const intervalMs = options?.intervalMs ?? DEFAULT_INTERVAL_MS;
  const timeoutMs = options?.timeoutMs ?? DEFAULT_TIMEOUT_MS;
  const started = Date.now();

  while (Date.now() - started < timeoutMs) {
    const { data, error } = await fetchGetAsyncTask(taskId);
    if (error || !data) {
      throw error instanceof Error ? error : new Error('查询任务失败');
    }
    if (data.status === AsyncTaskApi.SUCCESS) {
      return data;
    }
    if (data.status === AsyncTaskApi.FAILED) {
      throw new AsyncTaskFailedError(data);
    }
    await sleep(intervalMs);
  }
  throw new Error('任务等待超时，请稍后刷新查看结果');
}

export function isAsyncTaskFailedError(err: unknown): err is AsyncTaskFailedError {
  return err instanceof AsyncTaskFailedError;
}
