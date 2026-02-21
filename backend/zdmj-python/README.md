# ZDMJ Python 服务

FastAPI 后端服务，提供 RESTful API 接口。

## 本地开发

### 1. 环境要求

- Python 3.11+
- pip

### 2. 创建虚拟环境

**Windows:**
```bash
python -m venv zdmj
zdmj\Scripts\activate
```

**Linux/Mac:**
```bash
python3 -m venv zdmj
source zdmj/bin/activate
```

### 3. 安装依赖

```bash
pip install -r requirements.txt
```

### 4. 配置环境变量

复制 `.env.example` 为 `.env` 并修改配置：

```bash
# Windows
copy .env.example .env

# Linux/Mac
cp .env.example .env
```

编辑 `.env` 文件，配置数据库连接信息：

```env
# PostgreSQL 配置
POSTGRES_HOST=111.229.81.45
POSTGRES_PORT=5432
POSTGRES_USER=zdmj
POSTGRES_PASSWORD=zdmj
POSTGRES_DB=zdmj

# Redis 配置
REDIS_HOST=111.229.81.45
REDIS_PORT=6379
REDIS_PASSWORD=zdmj
REDIS_DB=0
```

### 5. 测试数据库连接

运行测试脚本检查数据库连接：

```bash
# Windows
python scripts\test_db_connection.py

# Linux/Mac
python scripts/test_db_connection.py
```

或者使用虚拟环境中的 Python：

```bash
# 激活虚拟环境后
python scripts/test_db_connection.py
```

### 6. 运行开发服务器

**方式一：使用脚本（推荐）**

```bash
# Linux/Mac
bash scripts/run_dev.sh

# Windows (使用 Git Bash 或 WSL)
bash scripts/run_dev.sh
```

**方式二：手动运行**

```bash
# 激活虚拟环境
source zdmj/bin/activate  # Linux/Mac
# 或
zdmj\Scripts\activate  # Windows

# 运行服务器
uvicorn app.main:app --host 0.0.0.0 --port 8000 --reload
```

### 7. 访问服务

- API 根路径: http://localhost:8000
- API 文档: http://localhost:8000/docs
- 健康检查: http://localhost:8000/api/health

## 测试数据库连接

### 使用测试脚本

```bash
python scripts/test_db_connection.py
```

### 使用健康检查接口

启动服务后，访问：

```bash
curl http://localhost:8000/api/health
```

或使用浏览器访问：http://localhost:8000/api/health

返回示例：

```json
{
  "status": "healthy",
  "postgres": {
    "status": "connected"
  },
  "redis": {
    "status": "connected"
  }
}
```

## 项目结构

```
zdmj-python/
├── app/
│   ├── __init__.py
│   ├── main.py              # FastAPI 应用入口
│   ├── config.py            # 配置管理
│   ├── database.py          # 数据库连接
│   ├── api/
│   │   ├── __init__.py
│   │   └── health.py        # 健康检查接口
│   └── models/              # 数据模型
├── scripts/
│   ├── run_dev.sh           # 本地开发运行脚本
│   ├── test_db_connection.py # 数据库连接测试脚本
│   └── setup_server.sh      # 服务器环境配置脚本
├── requirements.txt         # Python 依赖
├── .env.example           # 环境变量示例
├── Dockerfile              # Docker 镜像构建
└── README.md               # 项目说明
```

## 常见问题

### 1. 数据库连接失败

- 检查 `.env` 文件中的数据库配置是否正确
- 确认数据库服务是否运行
- 检查网络连接和防火墙设置
- 运行 `python scripts/test_db_connection.py` 查看详细错误信息

### 2. 端口被占用

如果 8000 端口被占用，可以指定其他端口：

```bash
uvicorn app.main:app --host 0.0.0.0 --port 8001 --reload
```

### 3. 虚拟环境激活失败

确保使用正确的激活命令：

- Windows: `zdmj\Scripts\activate`
- Linux/Mac: `source zdmj/bin/activate`

## 部署

参考 `zdmj/deploy/服务器环境配置.md` 了解服务器部署步骤。
