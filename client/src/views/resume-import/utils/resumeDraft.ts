import type { ResumeApi } from '@/service/api/resume';

export type DraftEducation = Omit<ResumeApi.EducationDTO, 'id'> & {
  id?: number;
  _key: string;
};

export type DraftCareer = Omit<ResumeApi.CareerDTO, 'id'> & {
  id?: number;
  _key: string;
};

export type DraftProject = Omit<ResumeApi.ProjectDTO, 'id'> & {
  id?: number;
  _key: string;
};

export type DraftAward = Omit<ResumeApi.AwardDTO, 'id'> & {
  id?: number;
  _key: string;
};

export interface ResumeContentDraft {
  id: number;
  personalInfo: ResumeApi.ResumePersonalInfo;
  skill: ResumeApi.SkillDTO;
  educations: DraftEducation[];
  careers: DraftCareer[];
  projects: DraftProject[];
  awards: DraftAward[];
}

function defaultPersonalInfo(source?: ResumeApi.ResumePersonalInfo): ResumeApi.ResumePersonalInfo {
  return {
    name: source?.name ?? '',
    phone: source?.phone ?? '',
    homepageUrl: source?.homepageUrl ?? '',
    preferredWorkCity: source?.preferredWorkCity ?? ''
  };
}

function newKey(prefix: string) {
  return `${prefix}-${Date.now()}-${Math.random().toString(36).slice(2, 8)}`;
}

function defaultSkill(): ResumeApi.SkillDTO {
  return {
    id: 0,
    content: [{ type: '专业技能', content: ['待补充'] }]
  };
}

type ImportAwardLike = ResumeApi.ResumeImportAwardItem & Record<string, unknown>;

function inferAwardTypeFromName(name: string): number {
  if (/奖学金|助学金/.test(name)) return 1;
  if (/竞赛|比赛|大赛|杯|挑战赛|Contest/i.test(name)) return 2;
  return 3;
}

function resolveImportAwardType(rawType: unknown, name: string): number {
  const fromName = inferAwardTypeFromName(name);
  if (fromName === 1 || fromName === 2) return fromName;
  if (typeof rawType === 'number' && rawType >= 1 && rawType <= 3) return rawType;
  return 3;
}

function normalizeImportAwardItem(raw: ImportAwardLike) {
  const name = trimOrEmpty(
    raw.name ?? (raw.awardName as string | undefined) ?? (raw.title as string | undefined) ?? (raw.honor as string | undefined)
  );
  const awardDate = trimOrEmpty(
    raw.awardDate ?? (raw.date as string | undefined) ?? (raw.award_date as string | undefined)
  );
  const rawType = raw.awardType ?? raw.award_type ?? raw.type;
  if (!name || !awardDate) {
    return null;
  }
  return {
    awardType: resolveImportAwardType(rawType, name),
    name,
    awardDate,
    description: trimOrEmpty(raw.description as string | undefined) || undefined
  };
}

/** 用 PDF 识别结果构建全量覆盖草稿（不含旧经历 id，保存时会删除未提交项） */
export function buildDraftFromImportResult(
  base: ResumeApi.ResumeContentDTO,
  parsed: ResumeApi.ResumeImportParseResult
): ResumeContentDraft {
  const educations: DraftEducation[] = [];
  for (const edu of parsed.educations ?? []) {
    if (!edu.school || !edu.startDate) continue;
    educations.push({
      _key: newKey('import-edu'),
      school: edu.school,
      major: edu.major || '',
      degree: edu.degree ?? 6,
      startDate: edu.startDate,
      endDate: edu.endDate,
      visible: edu.visible ?? true,
      gpa: edu.gpa
    });
  }

  const careers: DraftCareer[] = [];
  for (const career of parsed.careers ?? []) {
    if (!career.company || !career.position || !career.startDate) continue;
    careers.push({
      _key: newKey('import-career'),
      company: career.company,
      position: career.position,
      startDate: career.startDate,
      endDate: career.endDate,
      visible: career.visible ?? true,
      details: career.details
    });
  }

  const projects: DraftProject[] = [];
  for (const project of parsed.projects ?? []) {
    if (!project.name || !project.role || !project.startDate) continue;
    projects.push({
      _key: newKey('import-project'),
      name: project.name,
      role: project.role,
      startDate: project.startDate,
      endDate: project.endDate,
      description: project.description || '',
      contribution: project.contribution || '',
      techStack: project.techStack ?? [],
      highlights: typeof project.highlights === 'string' ? project.highlights : undefined,
      url: project.url,
      visible: project.visible ?? true
    });
  }

  const awards: DraftAward[] = [];
  const parsedAwards = parsed.awards ?? [];
  for (const award of parsedAwards) {
    const normalized = normalizeImportAwardItem(award as ImportAwardLike);
    if (!normalized) continue;
    awards.push({
      _key: newKey('import-award'),
      awardType: normalized.awardType,
      name: normalized.name,
      awardDate: normalized.awardDate,
      description: normalized.description
    });
  }

  const parsedSkillContent = parsed.skill?.content?.length
    ? parsed.skill.content.map(item => ({
        type: item.type?.trim() || '专业技能',
        content: [...(item.content ?? [])]
      }))
    : [{ type: '专业技能', content: ['待补充'] }];

  const draft: ResumeContentDraft = {
    id: base.id,
    personalInfo: defaultPersonalInfo({
      name: parsed.personalInfo?.name ?? base.personalInfo?.name,
      phone: parsed.personalInfo?.phone ?? base.personalInfo?.phone,
      homepageUrl: parsed.personalInfo?.homepageUrl ?? base.personalInfo?.homepageUrl,
      preferredWorkCity: parsed.personalInfo?.preferredWorkCity ?? base.personalInfo?.preferredWorkCity
    }),
    skill: {
      id: base.skill?.id ?? 0,
      content: parsedSkillContent
    },
    educations,
    careers,
    projects,
    awards
  };

  return draft;
}

export function cloneResumeForEdit(source: ResumeApi.ResumeContentDTO): ResumeContentDraft {
  return {
    id: source.id,
    personalInfo: defaultPersonalInfo(source.personalInfo),
    skill: source.skill
      ? {
          id: source.skill.id,
          content: source.skill.content?.map(item => ({
            type: item.type,
            content: [...(item.content ?? [])]
          })) ?? [{ type: '专业技能', content: ['待补充'] }]
        }
      : defaultSkill(),
    educations: (source.educations ?? []).map(edu => ({
      ...edu,
      _key: String(edu.id)
    })),
    careers: (source.careers ?? []).map(career => ({
      ...career,
      _key: String(career.id)
    })),
    projects: (source.projects ?? []).map(project => ({
      ...project,
      techStack: project.techStack ?? [],
      _key: String(project.id)
    })),
    awards: (source.awards ?? []).map(award => ({
      ...award,
      _key: String(award.id)
    }))
  };
}

export function createEmptyEducation(): DraftEducation {
  return {
    _key: newKey('edu'),
    school: '',
    major: '',
    degree: 3,
    startDate: '',
    endDate: '',
    visible: true,
    gpa: ''
  };
}

export function createEmptyCareer(): DraftCareer {
  return {
    _key: newKey('career'),
    company: '',
    position: '',
    startDate: '',
    endDate: '',
    visible: true,
    details: ''
  };
}

export function createEmptyProject(): DraftProject {
  return {
    _key: newKey('project'),
    name: '',
    role: '',
    startDate: '',
    endDate: '',
    description: '',
    contribution: '',
    techStack: [],
    visible: true,
    url: ''
  };
}

export function createEmptyAward(): DraftAward {
  return {
    _key: newKey('award'),
    awardType: 3,
    name: '',
    awardDate: '',
    description: ''
  };
}

function trimOrEmpty(value: string | undefined | null): string {
  return value?.trim() ?? '';
}

function stripEmptyOptionalDates<T extends { startDate?: string; endDate?: string }>(item: T): T {
  let next = { ...item } as T;
  if (!trimOrEmpty(next.endDate)) {
    const { endDate: _end, ...rest } = next;
    next = rest as T;
  }
  if (!trimOrEmpty(next.startDate)) {
    const { startDate: _start, ...rest } = next;
    next = rest as T;
  }
  return next;
}

function isBlankDraftEducation(edu: DraftEducation): boolean {
  return !edu.id
    && !trimOrEmpty(edu.school)
    && !trimOrEmpty(edu.major)
    && !trimOrEmpty(edu.startDate)
    && !trimOrEmpty(edu.endDate)
    && !trimOrEmpty(edu.gpa);
}

function isCompleteEducation(edu: DraftEducation): boolean {
  return Boolean(trimOrEmpty(edu.school) && trimOrEmpty(edu.major) && edu.degree && trimOrEmpty(edu.startDate));
}

function isBlankDraftCareer(career: DraftCareer): boolean {
  return !career.id
    && !trimOrEmpty(career.company)
    && !trimOrEmpty(career.position)
    && !trimOrEmpty(career.startDate)
    && !trimOrEmpty(career.endDate)
    && !trimOrEmpty(career.details);
}

function isCompleteCareer(career: DraftCareer): boolean {
  return Boolean(
    trimOrEmpty(career.company) && trimOrEmpty(career.position) && trimOrEmpty(career.startDate)
  );
}

function isBlankDraftProject(project: DraftProject): boolean {
  return !project.id
    && !trimOrEmpty(project.name)
    && !trimOrEmpty(project.role)
    && !trimOrEmpty(project.startDate)
    && !trimOrEmpty(project.endDate)
    && !trimOrEmpty(project.description)
    && !trimOrEmpty(project.contribution)
    && !trimOrEmpty(project.url)
    && !(project.techStack?.length);
}

function isCompleteProject(project: DraftProject): boolean {
  return Boolean(
    trimOrEmpty(project.name)
    && trimOrEmpty(project.role)
    && trimOrEmpty(project.startDate)
  );
}

function isBlankDraftAward(award: DraftAward): boolean {
  return !award.id
    && !trimOrEmpty(award.name)
    && !trimOrEmpty(award.awardDate)
    && !trimOrEmpty(award.description);
}

function isCompleteAward(award: DraftAward): boolean {
  return Boolean(award.awardType && trimOrEmpty(award.name) && trimOrEmpty(award.awardDate));
}

function mapEducation(edu: DraftEducation): ResumeApi.EducationCreate | ResumeApi.EducationUpdate {
  if (!isCompleteEducation(edu)) {
    throw new Error('educationIncomplete');
  }
  return stripEmptyOptionalDates({
    ...(edu.id ? { id: edu.id } : {}),
    school: trimOrEmpty(edu.school),
    major: trimOrEmpty(edu.major),
    degree: edu.degree,
    startDate: trimOrEmpty(edu.startDate),
    ...(trimOrEmpty(edu.endDate) ? { endDate: trimOrEmpty(edu.endDate) } : {}),
    ...(trimOrEmpty(edu.gpa) ? { gpa: trimOrEmpty(edu.gpa) } : {})
  });
}

function mapCareer(career: DraftCareer): ResumeApi.CareerCreate | ResumeApi.CareerUpdate {
  if (!isCompleteCareer(career)) {
    throw new Error('careerIncomplete');
  }
  return stripEmptyOptionalDates({
    ...(career.id ? { id: career.id } : {}),
    company: trimOrEmpty(career.company),
    position: trimOrEmpty(career.position),
    startDate: trimOrEmpty(career.startDate),
    ...(trimOrEmpty(career.endDate) ? { endDate: trimOrEmpty(career.endDate) } : {}),
    ...(trimOrEmpty(career.details) ? { details: trimOrEmpty(career.details) } : {})
  });
}

function mapProject(project: DraftProject): ResumeApi.ProjectCreate | ResumeApi.ProjectUpdate {
  if (!isCompleteProject(project)) {
    throw new Error('projectIncomplete');
  }
  return stripEmptyOptionalDates({
    ...(project.id ? { id: project.id } : {}),
    name: trimOrEmpty(project.name),
    role: trimOrEmpty(project.role),
    startDate: trimOrEmpty(project.startDate),
    description: trimOrEmpty(project.description),
    contribution: trimOrEmpty(project.contribution),
    ...(trimOrEmpty(project.endDate) ? { endDate: trimOrEmpty(project.endDate) } : {}),
    ...(project.techStack?.length ? { techStack: project.techStack } : {}),
    ...(trimOrEmpty(project.url) ? { url: trimOrEmpty(project.url) } : {}),
    ...(project.highlights ? { highlights: project.highlights } : {})
  });
}

function mapAward(award: DraftAward): ResumeApi.AwardCreate | ResumeApi.AwardUpdate {
  if (!isCompleteAward(award)) {
    throw new Error('awardIncomplete');
  }
  return {
    ...(award.id ? { id: award.id } : {}),
    awardType: award.awardType,
    name: trimOrEmpty(award.name),
    awardDate: trimOrEmpty(award.awardDate),
    ...(trimOrEmpty(award.description) ? { description: trimOrEmpty(award.description) } : {})
  };
}

export function buildSaveRequest(draft: ResumeContentDraft): ResumeApi.ResumeContentSaveRequest {
  if (!draft.skill?.content?.length) {
    throw new Error('skillRequired');
  }

  const skillContent = draft.skill.content
    .map(item => ({
      type: item.type?.trim() || '专业技能',
      content: (item.content ?? []).map(s => s.trim()).filter(Boolean)
    }))
    .filter(item => item.content.length > 0);

  if (!skillContent.length) {
    throw new Error('skillRequired');
  }

  return {
    skill: {
      ...(draft.skill.id ? { id: draft.skill.id } : {}),
      content: skillContent
    },
    personalInfo: {
      name: trimOrEmpty(draft.personalInfo.name),
      phone: trimOrEmpty(draft.personalInfo.phone),
      homepageUrl: trimOrEmpty(draft.personalInfo.homepageUrl),
      preferredWorkCity: trimOrEmpty(draft.personalInfo.preferredWorkCity)
    },
    educations: draft.educations
      .filter(edu => !isBlankDraftEducation(edu))
      .map(mapEducation),
    careers: draft.careers
      .filter(career => !isBlankDraftCareer(career))
      .map(mapCareer),
    projects: draft.projects
      .filter(project => !isBlankDraftProject(project))
      .map(mapProject),
    awards: draft.awards
      .filter(award => !isBlankDraftAward(award))
      .map(mapAward)
  };
}
