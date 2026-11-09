-- 奖项不再单独保存类型，名称本身区分奖学金、竞赛和其他荣誉
ALTER TABLE awards DROP CONSTRAINT IF EXISTS chk_awards_type;
ALTER TABLE awards DROP COLUMN IF EXISTS award_type;
