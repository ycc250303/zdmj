/** 日薪（salary_type=1）视为实习岗，月薪/年薪视为全职 */
export function isInternJob(salaryType: number | null | undefined): boolean {
  return salaryType === 1;
}

/** 去掉地址中 "-None" 等无效后缀段，如「郑州-None」→「郑州」 */
export function formatJobLocation(location: string | null | undefined): string {
  if (!location?.trim()) {
    return '';
  }
  const parts = location
    .split('-')
    .map(part => part.trim())
    .filter(part => part && part.toLowerCase() !== 'none');
  return parts.length > 0 ? parts.join('-') : location.trim();
}
