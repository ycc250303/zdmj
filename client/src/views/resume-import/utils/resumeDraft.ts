import type { ResumeApi } from '@/service/api/resume';

export type DraftEducation = Omit<ResumeApi.EducationDTO, 'id'> & { id?: number; _key: string };
export type DraftCareer = Omit<ResumeApi.CareerDTO, 'id'> & { id?: number; _key: string };
export type DraftProject = Omit<ResumeApi.ProjectDTO, 'id'> & { id?: number; _key: string };
export type DraftAward = Omit<ResumeApi.AwardDTO, 'id'> & { id?: number; _key: string };

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

function newKey(prefix: string) { return `${prefix}-${Date.now()}-${Math.random().toString(36).slice(2, 8)}`; }

function defaultSkill(): ResumeApi.SkillDTO {
  return { id: 0, content: [{ type: '专业技能', content: ['待补充'] }] };
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
  const name = trimOrEmpty(raw.name ?? (raw.awardName as string) ?? (raw.title as string) ?? (raw.honor as string));
  const awardDate = trimOrEmpty(raw.awardDate ?? (raw.date as string) ?? (raw.award_date as string));
  if (!name || !awardDate) return null;
  return {
    awardType: resolveImportAwardType(raw.awardType ?? raw.award_type ?? raw.type, name),
    name,
    awardDate,
    description: trimOrEmpty(raw.description as string) || undefined
  };
}

export function buildDraftFromImportResult(
  base: ResumeApi.ResumeContentDTO,
  parsed: ResumeApi.ResumeImportParseResult
): ResumeContentDraft {
  const educations: DraftEducation[] = (parsed.educations ?? [])
    .filter(e => e.school && e.startDate)
    .map(e => ({ _key: newKey('import-edu'), school: e.school!, major: e.major || '', degree: e.degree ?? 6, startDate: e.startDate!, endDate: e.endDate, visible: true, gpa: e.gpa }));

  const careers: DraftCareer[] = (parsed.careers ?? [])
    .filter(c => c.company && c.position && c.startDate)
    .map(c => ({ _key: newKey('import-career'), company: c.company!, position: c.position!, startDate: c.startDate!, endDate: c.endDate, visible: true, details: c.details }));

  const projects: DraftProject[] = (parsed.projects ?? [])
    .filter(p => p.name && p.role && p.startDate)
    .map(p => ({ _key: newKey('import-project'), name: p.name!, role: p.role!, startDate: p.startDate!, endDate: p.endDate, description: p.description || '', contribution: p.contribution || '', techStack: p.techStack ?? [], highlights: typeof p.highlights === 'string' ? p.highlights : undefined, url: p.url, visible: true }));

  const awards: DraftAward[] = (parsed.awards ?? [])
    .map(a => normalizeImportAwardItem(a as ImportAwardLike))
    .filter(Boolean)
    .map((a: any) => ({ _key: newKey('import-award'), awardType: a.awardType, name: a.name, awardDate: a.awardDate, description: a.description }));

  const parsedSkillContent = parsed.skill?.content?.length
    ? parsed.skill.content.map(item => ({ type: item.type?.trim() || '专业技能', content: [...(item.content ?? [])] }))
    : [{ type: '专业技能', content: ['待补充'] }];

  return {
    id: base.id,
    personalInfo: defaultPersonalInfo({
      name: parsed.personalInfo?.name ?? base.personalInfo?.name,
      phone: parsed.personalInfo?.phone ?? base.personalInfo?.phone,
      homepageUrl: parsed.personalInfo?.homepageUrl ?? base.personalInfo?.homepageUrl,
      preferredWorkCity: parsed.personalInfo?.preferredWorkCity ?? base.personalInfo?.preferredWorkCity
    }),
    skill: { id: base.skill?.id ?? 0, content: parsedSkillContent },
    educations, careers, projects, awards
  };
}

export function cloneResumeForEdit(source: ResumeApi.ResumeContentDTO): ResumeContentDraft {
  return {
    id: source.id,
    personalInfo: defaultPersonalInfo(source.personalInfo),
    skill: source.skill ? { id: source.skill.id, content: source.skill.content?.map(item => ({ type: item.type, content: [...(item.content ?? [])] })) ?? [{ type: '专业技能', content: ['待补充'] }] } : defaultSkill(),
    educations: (source.educations ?? []).map(edu => ({ ...edu, _key: String(edu.id) })),
    careers: (source.careers ?? []).map(c => ({ ...c, _key: String(c.id) })),
    projects: (source.projects ?? []).map(p => ({ ...p, techStack: p.techStack ?? [], _key: String(p.id) })),
    awards: (source.awards ?? []).map(a => ({ ...a, _key: String(a.id) }))
  };
}

export function createEmptyEducation(): DraftEducation { return { _key: newKey('edu'), school: '', major: '', degree: 3, startDate: '', endDate: '', visible: true, gpa: '' }; }
export function createEmptyCareer(): DraftCareer { return { _key: newKey('career'), company: '', position: '', startDate: '', endDate: '', visible: true, details: '' }; }
export function createEmptyProject(): DraftProject { return { _key: newKey('project'), name: '', role: '', startDate: '', endDate: '', description: '', contribution: '', techStack: [], visible: true, url: '' }; }
export function createEmptyAward(): DraftAward { return { _key: newKey('award'), awardType: 3, name: '', awardDate: '', description: '' }; }

function trimOrEmpty(value: string | undefined | null): string { return value?.trim() ?? ''; }

function stripEmptyOptionalDates<T extends { startDate?: string; endDate?: string }>(item: T): T {
  let next = { ...item } as T;
  if (!trimOrEmpty(next.endDate)) { const { endDate: _, ...rest } = next; next = rest as T; }
  if (!trimOrEmpty(next.startDate)) { const { startDate: _, ...rest } = next; next = rest as T; }
  return next;
}

function isCompleteEducation(edu: DraftEducation) { return Boolean(trimOrEmpty(edu.school) && trimOrEmpty(edu.major) && edu.degree && trimOrEmpty(edu.startDate)); }
function isCompleteCareer(c: DraftCareer) { return Boolean(trimOrEmpty(c.company) && trimOrEmpty(c.position) && trimOrEmpty(c.startDate)); }
function isCompleteProject(p: DraftProject) { return Boolean(trimOrEmpty(p.name) && trimOrEmpty(p.role) && trimOrEmpty(p.startDate)); }
function isCompleteAward(a: DraftAward) { return Boolean(a.awardType && trimOrEmpty(a.name) && trimOrEmpty(a.awardDate)); }
function isBlankEducation(e: DraftEducation) { return !e.id && !trimOrEmpty(e.school) && !trimOrEmpty(e.major) && !trimOrEmpty(e.startDate) && !trimOrEmpty(e.endDate) && !trimOrEmpty(e.gpa); }
function isBlankCareer(c: DraftCareer) { return !c.id && !trimOrEmpty(c.company) && !trimOrEmpty(c.position) && !trimOrEmpty(c.startDate) && !trimOrEmpty(c.endDate) && !trimOrEmpty(c.details); }
function isBlankProject(p: DraftProject) { return !p.id && !trimOrEmpty(p.name) && !trimOrEmpty(p.role) && !trimOrEmpty(p.startDate) && !trimOrEmpty(p.endDate) && !trimOrEmpty(p.description) && !trimOrEmpty(p.contribution) && !trimOrEmpty(p.url) && !(p.techStack?.length); }
function isBlankAward(a: DraftAward) { return !a.id && !trimOrEmpty(a.name) && !trimOrEmpty(a.awardDate) && !trimOrEmpty(a.description); }

function mapEducation(edu: DraftEducation) { if (!isCompleteEducation(edu)) throw new Error('educationIncomplete'); return stripEmptyOptionalDates({ ...(edu.id ? { id: edu.id } : {}), school: trimOrEmpty(edu.school), major: trimOrEmpty(edu.major), degree: edu.degree, startDate: trimOrEmpty(edu.startDate), ...(trimOrEmpty(edu.endDate) ? { endDate: trimOrEmpty(edu.endDate) } : {}), ...(trimOrEmpty(edu.gpa) ? { gpa: trimOrEmpty(edu.gpa) } : {}) }); }
function mapCareer(c: DraftCareer) { if (!isCompleteCareer(c)) throw new Error('careerIncomplete'); return stripEmptyOptionalDates({ ...(c.id ? { id: c.id } : {}), company: trimOrEmpty(c.company), position: trimOrEmpty(c.position), startDate: trimOrEmpty(c.startDate), ...(trimOrEmpty(c.endDate) ? { endDate: trimOrEmpty(c.endDate) } : {}), ...(trimOrEmpty(c.details) ? { details: trimOrEmpty(c.details) } : {}) }); }
function mapProject(p: DraftProject) { if (!isCompleteProject(p)) throw new Error('projectIncomplete'); return stripEmptyOptionalDates({ ...(p.id ? { id: p.id } : {}), name: trimOrEmpty(p.name), role: trimOrEmpty(p.role), startDate: trimOrEmpty(p.startDate), description: trimOrEmpty(p.description), contribution: trimOrEmpty(p.contribution), ...(trimOrEmpty(p.endDate) ? { endDate: trimOrEmpty(p.endDate) } : {}), ...(p.techStack?.length ? { techStack: p.techStack } : {}), ...(trimOrEmpty(p.url) ? { url: trimOrEmpty(p.url) } : {}), ...(p.highlights ? { highlights: p.highlights } : {}) }); }
function mapAward(a: DraftAward) { if (!isCompleteAward(a)) throw new Error('awardIncomplete'); return { ...(a.id ? { id: a.id } : {}), awardType: a.awardType, name: trimOrEmpty(a.name), awardDate: trimOrEmpty(a.awardDate), ...(trimOrEmpty(a.description) ? { description: trimOrEmpty(a.description) } : {}) }; }

export function buildSaveRequest(draft: ResumeContentDraft): ResumeApi.ResumeContentSaveRequest {
  if (!draft.skill?.content?.length) throw new Error('skillRequired');
  const skillContent = draft.skill.content.map(item => ({ type: item.type?.trim() || '专业技能', content: (item.content ?? []).map(s => s.trim()).filter(Boolean) })).filter(item => item.content.length > 0);
  if (!skillContent.length) throw new Error('skillRequired');
  return {
    skill: { ...(draft.skill.id ? { id: draft.skill.id } : {}), content: skillContent },
    personalInfo: { name: trimOrEmpty(draft.personalInfo.name), phone: trimOrEmpty(draft.personalInfo.phone), homepageUrl: trimOrEmpty(draft.personalInfo.homepageUrl), preferredWorkCity: trimOrEmpty(draft.personalInfo.preferredWorkCity) },
    educations: draft.educations.filter(e => !isBlankEducation(e)).map(mapEducation),
    careers: draft.careers.filter(c => !isBlankCareer(c)).map(mapCareer),
    projects: draft.projects.filter(p => !isBlankProject(p)).map(mapProject),
    awards: draft.awards.filter(a => !isBlankAward(a)).map(mapAward)
  };
}
