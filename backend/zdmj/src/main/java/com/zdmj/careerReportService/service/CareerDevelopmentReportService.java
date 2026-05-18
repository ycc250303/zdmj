package com.zdmj.careerReportService.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.zdmj.careerReportService.dto.CareerReportCheckDTO;
import com.zdmj.careerReportService.dto.CareerReportDTO;
import com.zdmj.careerReportService.dto.CareerReportGenerateReqDTO;
import com.zdmj.careerReportService.dto.CareerReportPolishReqDTO;
import com.zdmj.careerReportService.dto.CareerReportUpdateReqDTO;
import com.zdmj.careerReportService.entity.CareerDevelopmentReport;

/**
 * 职业发展报告服务接口
 */
public interface CareerDevelopmentReportService extends IService<CareerDevelopmentReport> {

    /**
     * 查询当前用户针对某岗位的最新报告（不触发生成）。
     *
     * @param jobId 岗位ID
     * @return 最新报告；不存在时返回 null
     */
    CareerReportDTO getLatestOrNull(Long jobId);

    /**
     * 同步生成职业发展报告并落库为新版本。
     *
     * @param jobId 岗位ID
     * @param req   生成参数（可选）
     * @return 新生成的报告
     */
    CareerReportDTO generate(Long jobId, CareerReportGenerateReqDTO req);

    /**
     * 对已有报告进行智能润色，写入新版本。
     *
     * @param reportId 报告ID
     * @param req      润色参数（可选）
     * @return 润色后的新版本报告
     */
    CareerReportDTO polish(Long reportId, CareerReportPolishReqDTO req);

    /**
     * 对报告做完整性检查（本地 + LLM），并更新当前记录的质量标记。
     *
     * @param reportId 报告ID
     * @return 检查结果
     */
    CareerReportCheckDTO checkIntegrity(Long reportId);

    /**
     * 保存用户手动编辑后的报告正文，写入新版本。
     *
     * @param reportId 报告ID
     * @param req      编辑内容
     * @return 保存后的新版本报告
     */
    CareerReportDTO saveManualEdit(Long reportId, CareerReportUpdateReqDTO req);
}
