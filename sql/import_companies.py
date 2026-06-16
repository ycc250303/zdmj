import os
import re
import json
import math
from typing import Dict, List, Optional, Tuple

import pandas as pd
import psycopg2


PG_CONFIG = {
    "host": "111.229.81.45",
    "port": 5432,
    "dbname": "zdmj",
    "user": "zdmj",
    "password": "zdmj",
}


def parse_industries(value) -> List[str]:
    """将『所属行业』列解析成字符串列表，用于写入 JSONB 数组。

    支持常见分隔符：逗号/顿号/斜杠/分号等；解析后对条目去重，保留首次出现顺序。
    """
    if pd.isna(value):
        return []

    text = str(value).strip()
    if not text:
        return []

    # 统一替换为英文逗号，再按逗号分割
    for sep in ["、", "，", ";", "；", "/", "|"]:
        text = text.replace(sep, ",")

    parts = [p.strip() for p in text.split(",") if p.strip()]
    # 去重且保持原有顺序（完全相同的字符串视为重复）
    return list(dict.fromkeys(parts))


def parse_string_list(value) -> List[str]:
    """解析 JSON 数组字符串、Excel 中的 list 类型，或按行拆成字符串列表。"""
    if pd.isna(value):
        return []
    if isinstance(value, (list, tuple)):
        return [str(x).strip() for x in value if str(x).strip()]
    text = str(value).strip()
    if not text:
        return []
    if text.startswith("["):
        try:
            data = json.loads(text)
            if isinstance(data, list):
                return [str(x).strip() for x in data if str(x).strip()]
        except json.JSONDecodeError:
            pass
    lines = [ln.strip() for ln in text.replace("<br>", "\n").split("\n") if ln.strip()]
    if lines:
        return lines
    return [text]


def to_int_salary(value) -> Optional[int]:
    """将薪资列转为非负整数；无法解析返回 None。"""
    if pd.isna(value):
        return None
    if isinstance(value, (int, float)):
        if isinstance(value, float) and (math.isnan(value) or math.isinf(value)):
            return None
        return int(round(float(value)))
    s = str(value).strip().replace(",", "")
    if not s:
        return None
    try:
        return int(round(float(s)))
    except ValueError:
        return None


def parse_salary_range_fallback(text: str) -> Tuple[Optional[int], Optional[int]]:
    """从『薪资范围』文本中提取数字作为最低/最高薪资兜底。"""
    if not text or not str(text).strip():
        return None, None
    nums = re.findall(r"\d+(?:\.\d+)?", str(text).replace(",", ""))
    if not nums:
        return None, None
    ints = [int(round(float(n))) for n in nums]
    if len(ints) == 1:
        return ints[0], ints[0]
    a, b = ints[0], ints[1]
    return (min(a, b), max(a, b))


def map_salary_type(value) -> int:
    """薪资单位 -> jobs.salary_type：1=日薪/实习，2=月薪/全职，3=年薪。

    Excel「薪资单位(月/日)」常见取值：day、month；亦兼容中文 日/月/年。
    """
    if pd.isna(value):
        return 2
    s = str(value).strip().lower()
    if s in ("day",) or "日" in s:
        return 1
    if s in ("year",) or "年" in s:
        return 3
    if s in ("month",) or "月" in s:
        return 2
    return 2


def is_job_web_info_lost(raw) -> bool:
    """『网页职位信息走失』为真时整行岗位不入库。"""
    if pd.isna(raw):
        return False
    if raw is True:
        return True
    if isinstance(raw, (int, float)) and not pd.isna(raw):
        try:
            if int(raw) == 1:
                return True
        except (TypeError, ValueError):
            pass
        return False
    s = str(raw).strip().lower()
    if s in ("", "0", "否", "false", "no", "n", "无"):
        return False
    if "走失" in s:
        return True
    if s in ("1", "true", "是", "yes", "y", "对"):
        return True
    return False


def build_description(duties: List[str], reqs: List[str], fallback: str) -> str:
    """岗位职责 + 岗位要求拼接为 description；无结构化内容时用岗位详情兜底。"""
    parts = []
    if duties:
        parts.append("岗位职责：\n" + "\n".join(duties))
    if reqs:
        parts.append("岗位要求：\n" + "\n".join(reqs))
    text = "\n\n".join(parts).strip()
    if text:
        return text
    fb = (fallback or "").replace("<br>", "\n").strip()
    return fb


SIZE_MAP = {
    # 1=20人以下/2=20-99人/3=100-299人/4=300-499人/5=500-999人/6=1000-9999人/7=10000人以上
    "20人以下": 1,
    "20-99人": 2,
    "100-299人": 3,
    "300-499人": 4,
    "500-999人": 5,
    "1000-9999人": 6,
    "10000人以上": 7,
}


TYPE_MAP = {
    # 1=A轮/2=B轮/3=C轮/4=D轮及以上/5=不需要融资/6=天使轮/7=已上市/8=未融资
    "A轮": 1,
    "B轮": 2,
    "C轮": 3,
    "D轮及以上": 4,
    "不需要融资": 5,
    "天使轮": 6,
    "已上市": 7,
    "未融资": 8,
}


def map_size(value):
    """按规格将 Excel『公司规模』文本映射到 size smallint 代码。"""
    if pd.isna(value):
        return None
    text = str(value).strip()
    if not text:
        return None
    return SIZE_MAP.get(text)


def map_type(value):
    """按规格将 Excel『公司类型』文本映射到 type smallint 代码。"""
    if pd.isna(value):
        return None
    text = str(value).strip()
    if not text:
        return None
    return TYPE_MAP.get(text)


def load_existing_company_names(conn) -> Dict[str, int]:
    """从数据库中读取已存在的公司名称及其主键 id，避免重复插入并维护哈希表。"""
    name_to_id: Dict[str, int] = {}
    with conn.cursor() as cur:
        cur.execute("SELECT id, name FROM companies")
        for _id, name in cur.fetchall():
            if name:
                name_to_id[name] = _id
    return name_to_id


def main():
    base_dir = os.path.dirname(os.path.abspath(__file__))
    excel_path = os.path.join(base_dir, os.environ.get("JOB_EXCEL", "new_job_data.xlsx"))

    col_company_name = "公司名称"
    col_industry = "所属行业"
    col_size = "公司规模"
    col_type = "公司类型"
    col_intro = "公司详情"
    col_job_name = "岗位名称"
    col_location = "地址"
    col_salary = "薪资范围"
    col_job_detail = "岗位详情"
    col_job_link = "岗位来源地址"
    col_salary_min = "最低薪资(元)"
    col_salary_max = "最高薪资(元)"
    col_salary_unit = "薪资单位(月/日)"
    col_duties = "岗位职责"
    col_req = "岗位要求"
    col_keywords = "关键词"
    col_web_lost = "网页职位信息走失"

    usecols = [
        col_company_name,
        col_industry,
        col_size,
        col_type,
        col_intro,
        col_job_name,
        col_location,
        col_salary,
        col_job_detail,
        col_job_link,
        col_salary_min,
        col_salary_max,
        col_salary_unit,
        col_duties,
        col_req,
        col_keywords,
        col_web_lost,
    ]

    df = pd.read_excel(excel_path, usecols=usecols)

    conn = psycopg2.connect(**PG_CONFIG)
    try:
        conn.autocommit = False

        name_to_id: Dict[str, int] = load_existing_company_names(conn)

        insert_sql_returning_company = """
            INSERT INTO companies (name, industries, size, type, introduction)
            VALUES (%s, %s::jsonb, %s, %s, %s)
            RETURNING id
        """

        insert_sql_job = """
            INSERT INTO jobs (
                job_name, company_id, company_name, description, location,
                salary_min, salary_max, salary_type,
                content, requirements, keywords, link
            )
            VALUES (
                %s, %s, %s, %s, %s,
                %s, %s, %s,
                %s::jsonb, %s::jsonb, %s::jsonb, %s
            )
        """

        inserted_company_count = 0
        inserted_job_count = 0

        for _, row in df.iterrows():
            name_raw = row.get(col_company_name)
            industries_raw = row.get(col_industry)
            size_raw = row.get(col_size)
            type_raw = row.get(col_type)
            intro_raw = row.get(col_intro)

            if pd.isna(name_raw) or str(name_raw).strip() == "":
                continue

            industries_list = parse_industries(industries_raw)
            if not industries_list:
                continue

            size_val = map_size(size_raw)
            if size_val is None:
                continue

            name = str(name_raw).strip()

            if name in name_to_id:
                continue

            type_val = map_type(type_raw)
            introduction = None
            if not pd.isna(intro_raw):
                intro_text = str(intro_raw).strip()
                introduction = intro_text if intro_text else None

            industries_json = json.dumps(industries_list, ensure_ascii=False)

            with conn.cursor() as cur:
                cur.execute(
                    insert_sql_returning_company,
                    (name, industries_json, size_val, type_val, introduction),
                )
                new_id = cur.fetchone()[0]
                name_to_id[name] = new_id
                inserted_company_count += 1

                if inserted_company_count % 100 == 0:
                    print(f"已插入公司记录 {inserted_company_count} 条...")

        for _, row in df.iterrows():
            if is_job_web_info_lost(row.get(col_web_lost)):
                continue

            name_raw = row.get(col_company_name)
            job_name_raw = row.get(col_job_name)
            location_raw = row.get(col_location)
            salary_raw = row.get(col_salary)
            job_detail_raw = row.get(col_job_detail)
            job_link_raw = row.get(col_job_link)

            if (
                pd.isna(name_raw)
                or str(name_raw).strip() == ""
                or pd.isna(job_name_raw)
                or str(job_name_raw).strip() == ""
                or pd.isna(location_raw)
                or str(location_raw).strip() == ""
            ):
                continue

            name = str(name_raw).strip()
            job_name = str(job_name_raw).strip()
            location = str(location_raw).strip()

            duties = parse_string_list(row.get(col_duties))
            reqs = parse_string_list(row.get(col_req))
            job_detail = ""
            if not pd.isna(job_detail_raw):
                job_detail = str(job_detail_raw).replace("<br>", "").strip()

            description = build_description(duties, reqs, job_detail)
            if len(description) < 20:
                continue

            salary_min = to_int_salary(row.get(col_salary_min))
            salary_max = to_int_salary(row.get(col_salary_max))
            if salary_min is None or salary_max is None:
                fb_min, fb_max = parse_salary_range_fallback(
                    "" if pd.isna(salary_raw) else str(salary_raw)
                )
                if salary_min is None:
                    salary_min = fb_min
                if salary_max is None:
                    salary_max = fb_max

            if salary_min is None or salary_max is None:
                continue
            if salary_min > salary_max:
                salary_min, salary_max = salary_max, salary_min

            salary_type = map_salary_type(row.get(col_salary_unit))

            salary_text = "" if pd.isna(salary_raw) else str(salary_raw).strip()
            # 与旧逻辑一致：薪资范围或数值字段中至少应出现可解析的薪资依据
            has_salary_signal = (
                any(ch.isdigit() for ch in salary_text)
                or salary_min > 0
                or salary_max > 0
            )
            if not has_salary_signal:
                continue

            if pd.isna(job_link_raw):
                job_link = ""
            else:
                job_link = str(job_link_raw).strip()
            if len(job_link) > 500:
                job_link = job_link[:500]

            keywords_list = parse_string_list(row.get(col_keywords))
            content_json = json.dumps(duties, ensure_ascii=False)
            requirements_json = json.dumps(reqs, ensure_ascii=False)
            keywords_json = json.dumps(keywords_list, ensure_ascii=False)

            company_id = name_to_id.get(name)
            if company_id is None:
                continue

            with conn.cursor() as cur:
                cur.execute(
                    insert_sql_job,
                    (
                        job_name,
                        company_id,
                        name,
                        description,
                        location,
                        salary_min,
                        salary_max,
                        salary_type,
                        content_json,
                        requirements_json,
                        keywords_json,
                        job_link,
                    ),
                )
                inserted_job_count += 1

                if inserted_job_count % 100 == 0:
                    print(f"已插入岗位记录 {inserted_job_count} 条...")

        conn.commit()
        print(f"成功插入公司记录 {inserted_company_count} 条，岗位记录 {inserted_job_count} 条。")

    except Exception as e:
        conn.rollback()
        print(f"导入过程中发生错误，已回滚事务：{e}")
        raise
    finally:
        conn.close()


if __name__ == "__main__":
    main()
