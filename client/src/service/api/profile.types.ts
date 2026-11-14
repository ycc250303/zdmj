/** 用户画像生成请求参数 */
export interface CapabilityProfileGenerateReq {
  /** 简历纯文本。上传文件时改用 multipart，不使用这个字段。 */
  rawText?: string;
}

/** 岗位专项评估分项 */
export interface ScoreDetail {
  /** 项目经验（0-40） */
  projectExperienceScore?: number;
  /** 技能匹配（0-20） */
  skillMatchScore?: number;
  /** 内容完整性（0-15） */
  contentCompletenessScore?: number;
  /** 结构清晰度（0-15） */
  structureClarityScore?: number;
  /** 表达专业性（0-10） */
  expressionProfessionalismScore?: number;
}

/** 改进建议（含技能缺失、证据不足等，见 Suggestion.category） */
export interface Suggestion {
  /** 技能缺失 | 证据不足 | 项目 | 表达 | 结构 | 职业素养 */
  category?: string;
  priority?: string;
  /** 问题描述 */
  issue?: string;
  /** 可执行改进动作 */
  recommendation?: string;
}

/** 学生就业能力画像 DTO */
export interface StudentCapabilityProfile {
  /** 专业技能：2～4 句中文，须结合简历中的课程/项目/技术栈写具体证据，避免只堆砌关键词 */
  professionalSkills?: string;
  /** 获奖经历：在校荣誉、竞赛获奖、奖学金等 */
  honorsAndAwards?: string;
  /** 创新能力：结合竞赛/课题/项目中的创新点、个人角色与可验证结果（勿空洞套话） */
  innovationAbility?: string;
  /** 学习能力：从自学内容、技术栈扩展、问题解决过程等提取可追问证据 */
  learningAbility?: string;
  /** 抗压能力：时间紧/任务重/多线程协作等场景中的职责与交付，尽量量化或写清边界 */
  pressureResistance?: string;
  /** 沟通能力：协作、评审、跨角色对接等经历；无证据则写「简历未体现」 */
  communicationAbility?: string;
  /** 实习/实践能力：项目背景—个人职责—技术实现—结果；突出个人贡献与可量化产出 */
  practicalAbility?: string;
  /** 综合竞争力（0～100） */
  competitivenessScore?: number;
  /** 岗位专项评估分项 */
  scoreDetail?: ScoreDetail;
  /** 优势亮点 */
  strengths?: string[];
  /** 改进建议 */
  suggestions?: Suggestion[];
  /** 一句话总结 */
  summary?: string;
}
