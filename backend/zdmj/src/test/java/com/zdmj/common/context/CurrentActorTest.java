package com.zdmj.common.context;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class CurrentActorTest {

    @Test
    void 构造身份_合法用户_保存用户编号() {
        CurrentActor actor = new CurrentActor(8L);
        assertThat(actor.userId()).isEqualTo(8L);
    }

    @Test
    void 构造身份_用户编号非法_立即拒绝() {
        for (Long userId : new Long[] {null, 0L, -1L}) {
            assertThatThrownBy(() -> new CurrentActor(userId))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }
}
