# 1 创建密钥

* 生成 SSH 密钥对 ：`ssh-keygen-trsa-b4096-C"github-actions-zdmj"`
* 文件夹位置默认在：`C:\Users\你的用户名\.ssh`
  * 公钥：`id_rsa.pub`
  * 私钥：`id_rsa`

# 2 GitHub 中配置 Secret

* 进入仓库：Settings → Secrets and variables → Actions
* 点击 "New repository secret"
* Name: SSH_PRIVATE_KEY
* Value: 粘贴完整的私钥内容（包括 -----BEGIN 和 -----END 行）

# 3 公钥添加到服务器

```
cd ~/.ssh/

nano authorized_keys
# 添加你的公钥

# 测试能否免密连接
ssh root@your-server-port
```
