# 使用 Python 3.11 作为基础镜像
FROM python:3.11-slim

# 设置工作目录
WORKDIR /app

# 设置环境变量
# GitPython 会调用系统 git；必须安装 git 包，并显式指定路径避免 slim 镜像里偶发找不到
ENV PYTHONUNBUFFERED=1 \
    PYTHONDONTWRITEBYTECODE=1 \
    GIT_PYTHON_GIT_EXECUTABLE=/usr/bin/git \
    GIT_PYTHON_REFRESH=quiet

# 安装系统依赖
RUN apt-get update && \
    apt-get install -y --no-install-recommends \
    git \
    openssh-client \
    ca-certificates \
    gcc \
    postgresql-client \
    && rm -rf /var/lib/apt/lists/* \
    && git --version

# 复制依赖文件
COPY requirements.txt .

# 安装 Python 依赖
RUN pip install --no-cache-dir --upgrade pip && \
    pip install --no-cache-dir -r requirements.txt

# 复制应用代码
COPY . .

# 暴露端口
EXPOSE 8000

# 运行应用
CMD ["uvicorn", "app.main:app", "--host", "0.0.0.0", "--port", "8000"]
