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

export type DraftSkill = Omit<ResumeApi.SkillDTO, 'id'> & {
  id?: number;
  _key: string;
};

export interface ResumeContentDraft {
  personalInfo: ResumeApi.ResumePersonalInfo;
  skills: DraftSkill[];
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

function normalizeImportAwardItem(raw: ResumeApi.ResumeImportAwardItem) {
  const name = trimOrEmpty(raw.name);
  const awardDate = trimOrEmpty(raw.awardDate);
  if (!name || !awardDate) {
    return null;
  }
  return {
    name,
    awardDate,
    description: trimOrEmpty(raw.description) || undefined
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
      highlights: project.highlights ?? [],
      url: project.url,
      visible: project.visible ?? true
    });
  }

  const awards: DraftAward[] = [];
  const parsedAwards = parsed.awards ?? [];
  for (const award of parsedAwards) {
    const normalized = normalizeImportAwardItem(award);
    if (!normalized) continue;
    awards.push({
      _key: newKey('import-award'),
      name: normalized.name,
      awardDate: normalized.awardDate,
      description: normalized.description
    });
  }

  const skills: DraftSkill[] = (parsed.skills ?? []).flatMap(skill => {
    const name = trimOrEmpty(skill.name);
    if (!name) {
      return [];
    }
    return [
      {
        _key: newKey('import-skill'),
        name,
        description: trimOrEmpty(skill.description)
      }
    ];
  });

  const draft: ResumeContentDraft = {
    personalInfo: defaultPersonalInfo({
      name: parsed.personalInfo?.name ?? base.personalInfo?.name,
      phone: parsed.personalInfo?.phone ?? base.personalInfo?.phone,
      homepageUrl: parsed.personalInfo?.homepageUrl ?? base.personalInfo?.homepageUrl,
      preferredWorkCity: parsed.personalInfo?.preferredWorkCity ?? base.personalInfo?.preferredWorkCity
    }),
    skills,
    educations,
    careers,
    projects,
    awards
  };

  return draft;
}

function previewId(id: number | undefined, index: number) {
  return id ?? -(index + 1);
}

/** 把编辑草稿转成模板预览使用的简历内容。未保存条目使用负数编号，避免列表键重复。 */
export function draftToPreview(draft: ResumeContentDraft): ResumeApi.ResumeContentDTO {
  return {
    personalInfo: { ...draft.personalInfo },
    skills: draft.skills.map((item, index) => ({
      id: previewId(item.id, index),
      name: item.name,
      description: item.description
    })),
    educations: draft.educations.map((item, index) => ({
      id: previewId(item.id, index),
      school: item.school,
      major: item.major,
      degree: item.degree,
      startDate: item.startDate,
      endDate: item.endDate,
      visible: item.visible,
      gpa: item.gpa
    })),
    careers: draft.careers.map((item, index) => ({
      id: previewId(item.id, index),
      company: item.company,
      position: item.position,
      startDate: item.startDate,
      endDate: item.endDate,
      visible: item.visible,
      details: item.details
    })),
    projects: draft.projects.map((item, index) => ({
      id: previewId(item.id, index),
      name: item.name,
      startDate: item.startDate,
      endDate: item.endDate,
      role: item.role,
      description: item.description,
      contribution: item.contribution,
      techStack: item.techStack,
      highlights: item.highlights,
      url: item.url,
      visible: item.visible
    })),
    awards: draft.awards.map((item, index) => ({
      id: previewId(item.id, index),
      name: item.name,
      awardDate: item.awardDate,
      description: item.description
    }))
  };
}

export function cloneResumeForEdit(source: ResumeApi.ResumeContentDTO): ResumeContentDraft {
  return {
    personalInfo: defaultPersonalInfo(source.personalInfo),
    skills: (source.skills ?? []).map(skill => ({
      id: skill.id,
      name: skill.name,
      description: skill.description ?? '',
      _key: String(skill.id)
    })),
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
      highlights: project.highlights ?? [],
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
    highlights: [],
    visible: true,
    url: ''
  };
}

export function createEmptyAward(): DraftAward {
  return {
    _key: newKey('award'),
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
    && !(project.techStack?.length)
    && !(project.highlights?.length);
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
  return Boolean(trimOrEmpty(award.name) && trimOrEmpty(award.awardDate));
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
    ...(project.highlights?.length ? { highlights: project.highlights } : {}),
    ...(trimOrEmpty(project.url) ? { url: trimOrEmpty(project.url) } : {})
  });
}

function mapAward(award: DraftAward): ResumeApi.AwardCreate | ResumeApi.AwardUpdate {
  if (!isCompleteAward(award)) {
    throw new Error('awardIncomplete');
  }
  return {
    ...(award.id ? { id: award.id } : {}),
    name: trimOrEmpty(award.name),
    awardDate: trimOrEmpty(award.awardDate),
    ...(trimOrEmpty(award.description) ? { description: trimOrEmpty(award.description) } : {})
  };
}

export function createEmptySkill(): DraftSkill {
  return {
    _key: newKey('skill'),
    name: '',
    description: ''
  };
}

export function buildSaveRequest(draft: ResumeContentDraft): ResumeApi.ResumeContentSaveRequest {
  const skills = draft.skills
    .map(item => ({
      ...(item.id ? { id: item.id } : {}),
      name: trimOrEmpty(item.name),
      ...(trimOrEmpty(item.description) ? { description: trimOrEmpty(item.description) } : {})
    }))
    .filter(item => item.name);

  if (!skills.length) {
    throw new Error('skillRequired');
  }

  return {
    skills,
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
