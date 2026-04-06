# zdmj
"职"点迷津

## 文件夹结构

```
zdmj/
├── backend/          # 后端服务
│   ├── zdmj/        # Java Spring Boot 主服务
│   └── zdmj-python/ # Python FastAPI AI服务
├── client/          # 客户端
├── docs/            # 项目文档
├── deploy/          # 部署配置（Docker、Nginx等）
└── sql/             # 数据库脚本
```

## 技术栈

| 分类 | 技术 |
|------|------|
| Java 主服务 | Spring Boot、Java、MyBatis-Plus、Spring Security、Spring WebFlux |
| Python AI服务 | FastAPI、asyncpg、Pydantic |
| 数据库 | PostgreSQL、Redis、MongoDB |
| 第三方工具 | JWT、腾讯云COS SDK |
| DevOps | Docker、Nginx |

---

### 启动命令

#### 1️⃣ 启动 Python 后端服务 (FastAPI)

```bash
# 进入 Python 后端目录
cd backend/zdmj-python

# 创建 conda 环境（首次运行）
conda create -n zdmj python=3.11 -y

# 激活 conda 环境
conda activate zdmj

# 安装依赖（首次运行）
pip install -r requirements.txt

# 启动服务
uvicorn app.main:app --reload --host 0.0.0.0 --port 8000
```

**访问地址**: http://localhost:8000
**API文档**: http://localhost:8000/docs

---

#### 2️⃣ 启动 Java 后端服务 (Spring Boot)

```bash
# 进入 Java 后端目录
cd backend/zdmj

# 构建项目（首次运行或代码更新后）
./mvnw clean package -DskipTests

# 启动服务
./mvnw spring-boot:run

# 或直接运行 jar 包
java -jar target/zdmj-0.0.1-SNAPSHOT.jar
```

**访问地址**: http://localhost:8080
**API前缀**: `/api/zdmj`

---

#### 3️⃣ 启动前端服务 (Vue3 + Vite)

```bash
# 进入前端目录
cd client

# 安装依赖（首次运行）
pnpm install

# 启动开发服务器
pnpm dev
```

**访问地址**: http://localhost:9527

---

### 📋 完整启动流程（三个终端）

**终端 1 - Python 后端**:
```bash
cd backend/zdmj-python
conda activate zdmj
uvicorn app.main:app --reload --host 0.0.0.0 --port 8000
```

**终端 2 - Java 后端**:
```bash
cd backend/zdmj
./mvnw spring-boot:run
```

**终端 3 - 前端**:
```bash
cd client
pnpm dev
```

---

### 🔗 服务架构

```
浏览器 (http://localhost:9527)
    ↓
前端 Vue3 应用
    ↓ API请求
Java后端 (http://localhost:8080/api/zdmj)
    ↓ AI相关请求
Python后端 (http://localhost:8000)
    ↓ 数据存储
远程数据库 (PostgreSQL + Redis)
```

---

### ⚙️ 配置文件

- **前端配置**: `client/.env`
- **Java配置**: `backend/zdmj/src/main/resources/application.yml`
- **Python配置**: `backend/zdmj-python/app/config.py`

---

### 📚 更多文档

- [部署文档](deploy/)
- [后端开发文档](docs/backend/)
- [数据库脚本](sql/)
