package com.zdmj.contract;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

import com.zdmj.common.exception.BusinessException;
import com.zdmj.common.exception.ErrorCode;
import com.zdmj.common.model.PageRequests;
import com.zdmj.jobService.dto.JobPageQueryDTO;

class PaginationContractTest {

    @Test
    void 分页参数_缺省零和超上限_规范为默认页并截断limit() {
        assertThat(PageRequests.normalize(null, null))
                .isEqualTo(new PageRequests.Normalized(PageRequests.DEFAULT_PAGE, PageRequests.DEFAULT_LIMIT));
        assertThat(PageRequests.normalize(0, 0).page()).isEqualTo(1);
        assertThat(PageRequests.normalize(-3, -1).limit()).isEqualTo(PageRequests.DEFAULT_LIMIT);
        assertThat(PageRequests.normalize(2, PageRequests.MAX_LIMIT).limit()).isEqualTo(PageRequests.MAX_LIMIT);
        assertThat(PageRequests.normalize(2, 500).limit()).isEqualTo(PageRequests.MAX_LIMIT);

        JobPageQueryDTO query = new JobPageQueryDTO();
        query.setPage("0");
        query.setLimit("500");
        PageRequests.Normalized normalized = PageRequests.normalize(query.getPage(), query.getLimit());
        assertThat(normalized.page()).isEqualTo(1);
        assertThat(normalized.limit()).isEqualTo(100);

        query.setPage("");
        query.setLimit("");
        assertThat(query.getPage()).isNull();
        assertThat(query.getLimit()).isNull();
    }

    @Test
    void 分页参数_非整数_拒绝并保留校验错误码() {
        JobPageQueryDTO query = new JobPageQueryDTO();
        assertThatThrownBy(() -> query.setPage("abc"))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getCode())
                .isEqualTo(ErrorCode.VALIDATION_ERROR.getCode());
        assertThatThrownBy(() -> query.setLimit("1.5"))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getCode())
                .isEqualTo(ErrorCode.VALIDATION_ERROR.getCode());
    }
}
