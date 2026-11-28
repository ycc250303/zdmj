package com.zdmj.contract;

import static org.assertj.core.api.Assertions.assertThat;
import java.util.Arrays;
import com.zdmj.common.exception.ErrorCode;
import org.junit.jupiter.api.Test;

class ErrorCodeContractTest {
    @Test
    void 全部错误码_编号为正且全局唯一() {
        assertThat(Arrays.stream(ErrorCode.values()).map(ErrorCode::getCode).toList())
                .allSatisfy(code -> assertThat(code).isPositive())
                .doesNotHaveDuplicates();
    }
}
