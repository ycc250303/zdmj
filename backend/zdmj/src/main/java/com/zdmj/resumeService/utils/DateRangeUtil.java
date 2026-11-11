package com.zdmj.resumeService.utils;

import com.zdmj.common.exception.BusinessException;
import com.zdmj.common.exception.ErrorCode;

import java.time.LocalDate;

/**
 * 经历类起止日期校验。结束日期早于开始日期时，按调用方给出的错误码拒绝写入。
 */
public final class DateRangeUtil {

    private DateRangeUtil() {
    }

    /**
     * 结束日期早于开始日期时拒绝写入。任一端为空时跳过，在职、在读或进行中的记录可以没有结束日期。
     *
     * @param startDate 开始日期
     * @param endDate 结束日期
     * @param errorCode 结束日期早于开始日期时抛出的业务错误码
     * @throws BusinessException 结束日期早于开始日期
     */
    public static void rejectIfEndBeforeStart(LocalDate startDate, LocalDate endDate, ErrorCode errorCode) {
        if (startDate != null && endDate != null && endDate.isBefore(startDate)) {
            throw new BusinessException(errorCode);
        }
    }
}
