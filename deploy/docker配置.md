# 1.初始配置

更新系统

```bash
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

# 5.前端部署

```bash
# 1) 系统更新
sudo apt update
sudo apt upgrade -y

# 2) 基础工具
sudo apt install -y curl git rsync nginx

# 3) 安装 nvm（用于安装 Node）
curl -fsSL https://raw.githubusercontent.com/nvm-sh/nvm/v0.40.3/install.sh | bash
source ~/.nvm/nvm.sh

# 4) 安装 Node 20（满足 client 要求 >=20.19）
nvm install 20
nvm use 20
nvm alias default 20

# 5) 启用 corepack（管理 pnpm）
corepack enable
corepack prepare pnpm@latest --activate

# 6) 验证版本
node -v
pnpm -v
nginx -v
rsync --version
git --version
```

```bash
cd /opt/zdmj/zdmj
git fetch origin
git checkout main
git pull --ff-only origin main

cd client
pnpm install --frozen-lockfile
pnpm build

sudo rsync -av --delete dist/ /usr/share/nginx/html/
sudo nginx -t
sudo systemctl reload nginx

# 确认状态
sudo systemctl status nginx --no-pager
```
# 6.管理

```bash
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

# 7.手动部署

```bash
cd ..
cd /opt/zdmj/zdmj
git pull --ff-only origin main
./deploy/deploy.sh
```

# 附：系统盘满了

```bash
docker system df

docker buildx du --verbose

docker builder prune --filter "until=240h"
# 240h = 10天，可改 168h(7天) / 720h(30天)

docker system df -v

docker rmi id
```