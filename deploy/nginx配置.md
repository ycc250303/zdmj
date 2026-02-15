# 1 安装 Nginx

```
# 更新软件包列表
sudo apt update

# 安装 nginx
sudo apt install nginx -y

# 检查 nginx 是否安装成功
nginx -v
```

# 2 配置 Nginx

```
sudo nano /tmp/nginx.conf
# 然后复制配置文件内容

# 备份默认配置（可选）
sudo cp /etc/nginx/sites-available/default /etc/nginx/sites-available/default.backup

# 将配置文件复制到 nginx 配置目录
sudo cp /tmp/nginx.conf /etc/nginx/sites-available/zdmj

# 创建软链接启用配置
sudo ln -sf /etc/nginx/sites-available/zdmj /etc/nginx/sites-enabled/zdmj

# 删除默认配置的软链接（如果不需要）
sudo rm -f /etc/nginx/sites-enabled/default
```

# 3 测试配置

```
# 测试 nginx 配置文件语法
sudo nginx -t

# 如果显示 "syntax is ok" 和 "test is successful"，说明配置正确
```

# 4 启动和重启 nginx

```
# 启动 nginx
sudo systemctl start nginx

# 设置开机自启
sudo systemctl enable nginx

# 重启 nginx（配置修改后）
sudo systemctl restart nginx

# 或者重新加载配置（不中断服务）
sudo systemctl reload nginx

# 查看 nginx 状态
sudo systemctl status nginx
```

# 5 管理 Nginx

```
# 停止 nginx
sudo systemctl stop nginx

# 重启 nginx
sudo systemctl restart nginx

# 重新加载配置（不中断服务）
sudo systemctl reload nginx

# 查看错误日志
sudo tail -f /var/log/nginx/zdmj_error.log

# 查看访问日志
sudo tail -f /var/log/nginx/zdmj_access.log

# 查看所有 nginx 相关日志
sudo tail -f /var/log/nginx/*.log
```
