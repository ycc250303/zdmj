package com.zdmj.common.async;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class AsyncTaskEnumsTest {

    @Test
    void status_inFlightAndTerminal_shouldMatchLifecycle() {
        assertThat(AsyncTaskStatus.PENDING.getLabel()).isEqualTo("排队中");
        assertThat(AsyncTaskStatus.PENDING.isInFlight()).isTrue();
        assertThat(AsyncTaskStatus.RUNNING.isInFlight()).isTrue();
        assertThat(AsyncTaskStatus.SUCCESS.isInFlight()).isFalse();
        assertThat(AsyncTaskStatus.FAILED.isInFlight()).isFalse();
        assertThat(AsyncTaskStatus.SUCCESS.isTerminal()).isTrue();
        assertThat(AsyncTaskStatus.FAILED.isTerminal()).isTrue();
        assertThat(AsyncTaskStatus.PENDING.isTerminal()).isFalse();
        assertThat(AsyncTaskStatus.RUNNING.isTerminal()).isFalse();
    }

    @Test
    void type_fromCodeAndStream_shouldResolveKnownCodes() {
        assertThat(AsyncTaskType.JOB_MATCH.getStreamKind()).isEqualTo(AsyncTaskType.StreamKind.LLM);
        assertThat(AsyncTaskType.KB_EMBED.getStreamKind()).isEqualTo(AsyncTaskType.StreamKind.EMBED);
        assertThat(AsyncTaskType.fromCode(4)).isEqualTo(AsyncTaskType.JOB_MATCH);
        assertThat(AsyncTaskType.fromCode(null)).isNull();
        assertThat(AsyncTaskType.fromCode(0)).isNull();
    }
}
