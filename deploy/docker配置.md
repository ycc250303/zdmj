# 1.初始配置

更新系统

```
sudo apt update
sudo apt upgrade -y
```

安装 Docker

```bash
sudo apt install -y ca-certificates curl gnupg

# 添加 Docker 官方 GPG key
sudo install -m 0755 -d /etc/apt/keyrings
curl -fsSL https://download.docker.com/linux/ubuntu/gpg | \
sudo gpg --dearmor -o /etc/apt/keyrings/docker.gpg
sudo chmod a+r /etc/apt/keyrings/docker.gpg

# 添加 Docker 源
echo \
  "deb [arch=$(dpkg --print-architecture) signed-by=/etc/apt/keyrings/docker.gpg] \
  https://download.docker.com/linux/ubuntu \
  $(. /etc/os-release && echo "$VERSION_CODENAME") stable" | \
  sudo tee /etc/apt/sources.list.d/docker.list > /dev/null

# 安装 Docker + Compose 插件
sudo apt update
sudo apt install -y docker-ce docker-ce-cli containerd.io docker-buildx-plugin docker-compose-plugin

# 允许当前用户直接用 docker
sudo usermod -aG docker $USER
```

# 2.创建目录

```bash
sudo mkdir -p /opt/zdmj
sudo chown $USER:$USER /opt/zdmj
cd /opt/zdmj

mkdir -p data/{pgsql,redis}
```

# 3.配置镜像源

```bash
sudo nano /etc/docker/daemon.json

# 复制下面内容
{
  "registry-mirrors": [
    "https://docker.m.daocloud.io",
    "https://dockerproxy.com",
    "https://ccr.ccs.tencentyun.com"
  ]
}

# 重启docker
sudo systemctl daemon-reload
sudo systemctl restart docker
```

# 4.拉取镜像

```bash
cd /opt/zdmj

nano docker-compose.yml

# 粘贴对应文件内容

docker compose up -d

docker compose ps

# 统一管理密码

nano .env
```

## DashScope API Key 与 401 InvalidApiKey

- `application.yml` 中 **不再写死** `api-key`，运行时只认环境变量：`DASHSCOPE_API_KEY` 或 `SPRING_AI_DASHSCOPE_API_KEY`（`docker-compose` 已从 `.env` 注入）。
- 修改 `.env` 或 `application.yml` 后必须 **重新构建并启动后端镜像**，否则容器内仍是旧 JAR 里的配置：

```bash
cd /opt/zdmj   # 与 docker-compose.yml、.env 同目录
docker compose build --no-cache backend && docker compose up -d backend
```

- 在服务器上确认变量已进入容器（不打印密钥内容）：

```bash
docker exec zdmj-backend sh -c 'if [ -n "$DASHSCOPE_API_KEY" ]; then echo DASHSCOPE_API_KEY=ok; else echo DASHSCOPE_API_KEY=empty; fi; if [ -n "$SPRING_AI_DASHSCOPE_API_KEY" ]; then echo SPRING_AI_DASHSCOPE_API_KEY=ok; else echo SPRING_AI_DASHSCOPE_API_KEY=empty; fi'
```

若长度为 0，说明 `.env` 未加载或变量名不一致；请在 **`docker-compose.yml` 所在目录** 执行 `docker compose`，并检查该目录下的 `.env`。

# 5.管理

```
# 启动
docker compose up -d

# 查看状态
docker compose ps

# 查看日志
docker compose logs -f postgres

# 停止
docker compose down

# 查看指定容器日志
docker logs --tail=200 zdmj-backend

```

# 6.手动部署

```
cd ..
cd /opt/zdmj/zdmj
git pull --ff-only origin main
./deploy/deploy.sh
```