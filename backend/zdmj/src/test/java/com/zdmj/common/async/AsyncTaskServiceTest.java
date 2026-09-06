package com.zdmj.common.async;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zdmj.common.async.mapper.AsyncLlmTaskMapper;
import com.zdmj.common.context.UserContext;
import com.zdmj.common.context.UserHolder;
import com.zdmj.common.exception.BusinessException;
import com.zdmj.common.exception.ErrorCode;

@ExtendWith(MockitoExtension.class)
class AsyncTaskServiceTest {

    @Mock
    private AsyncLlmTaskMapper mapper;
    @Mock
    private LlmStreamProducer producer;

    private AsyncTaskService service;

    @BeforeEach
    void setUp() {
        service = new AsyncTaskService(mapper, producer);
    }

    @AfterEach
    void tearDown() {
        UserHolder.clear();
    }

    @Test
    void enqueue_firstTime_shouldInsertAndXadd() {
        when(mapper.insert(any(AsyncLlmTask.class))).thenAnswer(inv -> {
            inv.getArgument(0, AsyncLlmTask.class).setId(42L);
            return 1;
        });
        when(producer.send(eq(42L))).thenReturn(true);
        when(mapper.selectById(42L)).thenReturn(pending(42L, 8L));

        AsyncTaskDTO dto = service.enqueue(AsyncTaskType.STUDENT_PROFILE, 8L, "user:8", null);

        assertEquals(42L, dto.getTaskId());
        assertEquals(AsyncTaskStatus.PENDING.getCode(), dto.getStatus());
        verify(producer).send(42L);
    }

    @Test
    void enqueue_uniqueConflict_shouldReturnExisting() {
        when(mapper.insert(any(AsyncLlmTask.class))).thenThrow(new DataIntegrityViolationException("uk"));
        when(mapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(pending(11L, 8L));

        AsyncTaskDTO dto = service.enqueue(AsyncTaskType.STUDENT_PROFILE, 8L, "user:8", "{}");

        assertEquals(11L, dto.getTaskId());
        verify(producer, never()).send(anyLong());
    }

    @Test
    void enqueue_xaddFailed_shouldReturnFailedRow() {
        when(mapper.insert(any(AsyncLlmTask.class))).thenAnswer(inv -> {
            inv.getArgument(0, AsyncLlmTask.class).setId(5L);
            return 1;
        });
        when(producer.send(anyLong())).thenReturn(false);
        AsyncLlmTask failed = pending(5L, 8L);
        failed.setStatus(AsyncTaskStatus.FAILED.getCode());
        failed.setErrorMessage("任务入队失败");
        when(mapper.selectById(5L)).thenReturn(failed);

        AsyncTaskDTO dto = service.enqueue(AsyncTaskType.STUDENT_PROFILE, 8L, "user:8", null);

        assertEquals(AsyncTaskStatus.FAILED.getCode(), dto.getStatus());
        assertEquals("任务入队失败", dto.getErrorMessage());
    }

    @Test
    void enqueue_embedType_shouldReject() {
        assertThrows(IllegalArgumentException.class,
                () -> service.enqueue(AsyncTaskType.KB_EMBED, 1L, "doc:1", null));
    }

    @Test
    void getById_owner_shouldReturn() {
        UserHolder.set(UserContext.of(8L, "u"));
        when(mapper.selectById(42L)).thenReturn(pending(42L, 8L));

        assertEquals(42L, service.getById(42L).getTaskId());
    }

    @Test
    void getById_otherUser_shouldBeNotFound() {
        UserHolder.set(UserContext.of(9L, "other"));
        when(mapper.selectById(42L)).thenReturn(pending(42L, 8L));

        BusinessException ex = assertThrows(BusinessException.class, () -> service.getById(42L));
        assertEquals(ErrorCode.ASYNC_TASK_NOT_FOUND, ex.getErrorCode());
    }

    @Test
    void getById_missing_shouldBeNotFound() {
        UserHolder.set(UserContext.of(8L, "u"));
        when(mapper.selectById(1L)).thenReturn(null);

        BusinessException ex = assertThrows(BusinessException.class, () -> service.getById(1L));
        assertEquals(ErrorCode.ASYNC_TASK_NOT_FOUND, ex.getErrorCode());
    }

    @Test
    void getById_notLoggedIn_shouldThrow1002() {
        BusinessException ex = assertThrows(BusinessException.class, () -> service.getById(1L));
        assertEquals(ErrorCode.USER_NOT_LOGIN, ex.getErrorCode());
    }

    private static AsyncLlmTask pending(long id, long userId) {
        AsyncLlmTask task = new AsyncLlmTask();
        task.setId(id);
        task.setUserId(userId);
        task.setTaskType(AsyncTaskType.STUDENT_PROFILE.getCode());
        task.setBizKey("user:" + userId);
        task.setStatus(AsyncTaskStatus.PENDING.getCode());
        return task;
    }
}
