import os
import json
from typing import Dict, List, Set

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

    支持常见分隔符：逗号/顿号/斜杠/分号等。
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
    return parts


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
    # 1. 定位 Excel 文件
    base_dir = os.path.dirname(os.path.abspath(__file__))
    excel_path = os.path.join(base_dir, "jobs_data.xls")

    # 2. 读取 Excel（只读需要的列：公司 + 岗位）
    col_company_name = "公司名称"
    col_industry = "所属行业"
    col_size = "公司规模"
    col_type = "公司类型"
    col_intro = "公司详情"
    col_job_name = "岗位名称"
    col_location = "地址"
    col_salary = "薪资范围"
    col_job_detail = "岗位详情"

    df = pd.read_excel(
        excel_path,
        usecols=[
            col_company_name,
            col_industry,
            col_size,
            col_type,
            col_intro,
            col_job_name,
            col_location,
            col_salary,
            col_job_detail,
        ],
    )

    # 3. 连接 PostgreSQL
    conn = psycopg2.connect(**PG_CONFIG)
    try:
        conn.autocommit = False

        # 3.1 读出已存在的公司名称及主键 id，用于去重和哈希表
        name_to_id: Dict[str, int] = load_existing_company_names(conn)

        insert_sql_returning_company = """
            INSERT INTO companies (name, industries, size, type, introduction)
            VALUES (%s, %s::jsonb, %s, %s, %s)
            RETURNING id
        """

        insert_sql_job = """
            INSERT INTO jobs (job_name, company_id, description, location, salary, link)
            VALUES (%s, %s, %s, %s, %s, %s)
        """

        inserted_company_count = 0
        inserted_job_count = 0

        # 4. 先逐行处理，插入 / 去重公司
        for _, row in df.iterrows():
            name_raw = row.get(col_company_name)
            industries_raw = row.get(col_industry)
            size_raw = row.get(col_size)
            type_raw = row.get(col_type)
            intro_raw = row.get(col_intro)

            # 公司必填字段检查：「公司名称」「所属行业」「公司规模」任一为空则跳过
            if pd.isna(name_raw) or str(name_raw).strip() == "":
                continue

            industries_list = parse_industries(industries_raw)
            if not industries_list:
                continue

            size_val = map_size(size_raw)
            if size_val is None:
                continue

            name = str(name_raw).strip()

            # 若该公司名称已经插入过（或 DB 已有），则跳过
            if name in name_to_id:
                continue

            type_val = map_type(type_raw)
            introduction = None
            if not pd.isna(intro_raw):
                intro_text = str(intro_raw).strip()
                introduction = intro_text if intro_text else None

            industries_json = json.dumps(industries_list, ensure_ascii=False)

            # 插入公司，并拿回主键 id，更新哈希表
            with conn.cursor() as cur:
                cur.execute(
                    insert_sql_returning_company,
                    (name, industries_json, size_val, type_val, introduction),
                )
                new_id = cur.fetchone()[0]
                name_to_id[name] = new_id
                inserted_company_count += 1

                # 每插入 100 条公司数据，打印一次日志
                if inserted_company_count % 100 == 0:
                    print(f"已插入公司记录 {inserted_company_count} 条...")

        # 4.2 再逐行处理，插入岗位（此时 name_to_id 已包含所有公司）
        for _, row in df.iterrows():
            name_raw = row.get(col_company_name)
            job_name_raw = row.get(col_job_name)
            location_raw = row.get(col_location)
            salary_raw = row.get(col_salary)
            job_detail_raw = row.get(col_job_detail)

            # 岗位必填字段检查
            if (
                pd.isna(name_raw)
                or str(name_raw).strip() == ""
                or pd.isna(job_name_raw)
                or str(job_name_raw).strip() == ""
                or pd.isna(location_raw)
                or str(location_raw).strip() == ""
                or pd.isna(salary_raw)
                or str(salary_raw).strip() == ""
                or pd.isna(job_detail_raw)
            ):
                continue

            name = str(name_raw).strip()
            job_name = str(job_name_raw).strip()
            location = str(location_raw).strip()
            salary = str(salary_raw).strip()
            # 去除岗位详情中的 "<br>"
            job_detail = str(job_detail_raw).replace("<br>", "").strip()

            # 薪资不包含任何数字，则不插入
            if not any(ch.isdigit() for ch in salary):
                continue

            # 岗位详情少于 20 个字符，则不插入
            if len(job_detail) < 20:
                continue

            # 根据哈希表获取 company_id，若不存在则不插入该岗位
            company_id = name_to_id.get(name)
            if company_id is None:
                continue

            # 插入 jobs 表
            with conn.cursor() as cur:
                cur.execute(
                    insert_sql_job,
                    (
                        job_name,
                        company_id,
                        job_detail,  # description
                        location,
                        salary,
                        "",  # link 暂无来源链接信息，先写空字符串
                    ),
                )
                inserted_job_count += 1

                # 每插入 100 条岗位数据，打印一次日志
                if inserted_job_count % 100 == 0:
                    print(f"已插入岗位记录 {inserted_job_count} 条...")

        # 5. 提交事务
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

