# GitHub Actions 连接服务器

发布和例行清理用仓库密钥 `SSH_PRIVATE_KEY`、`SERVER_HOST` 登录 `root@服务器:22`。私钥不要提交到仓库。

## 1. 在 Mac 上生成密钥

```bash
ssh-keygen -t ed25519 -C "github-actions-zdmj" -f ~/.ssh/zdmj_github_actions -N ""
```

私钥：`~/.ssh/zdmj_github_actions`。公钥：`~/.ssh/zdmj_github_actions.pub`。口令必须为空，发布脚本不会输入口令。

## 2. 把公钥写入服务器

用你现在能登录的方式进入服务器，执行：

```bash
install -d -m 700 /root/.ssh
touch /root/.ssh/authorized_keys
chmod 600 /root/.ssh/authorized_keys
```

把 `~/.ssh/zdmj_github_actions.pub` 的整行追加到 `/root/.ssh/authorized_keys`。已能用密码登录时，在 Mac 上执行：

```bash
ssh-copy-id -i ~/.ssh/zdmj_github_actions.pub root@服务器地址
```

## 3. 在 Mac 上验证

把 `服务器地址` 换成实际 IP 或域名：

```bash
ssh -i ~/.ssh/zdmj_github_actions -o BatchMode=yes -p 22 root@服务器地址 'echo connected'
```

输出 `connected` 后继续。出现 `Permission denied (publickey)` 时，检查公钥是否写在 root 的 `authorized_keys`，以及目录权限是否为 `700`、文件权限是否为 `600`。

## 4. 更新 GitHub 密钥

打开仓库 Settings → Secrets and variables → Actions，更新这两个密钥：

| Name                | Value                                                                     |
| ------------------- | ------------------------------------------------------------------------- |
| `SSH_PRIVATE_KEY` | 私钥全文，含 `BEGIN OPENSSH PRIVATE KEY` 与 `END OPENSSH PRIVATE KEY` |
| `SERVER_HOST`     | 服务器 IP 或域名，不含 `root@`、端口和协议                              |

复制私钥：

```bash
pbcopy < ~/.ssh/zdmj_github_actions
```

粘贴后确认首尾两行都在，中间没有额外空格或引号。

## 5. 重新发布

Actions → CD - Deploy → Run workflow，勾选部署后端。`Deploy on server` 应进入远端命令，不再出现 `Permission denied (publickey)`。
