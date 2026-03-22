# zdmj
"职"点迷津

## 文件夹结构

```
zdmj/
├── backend/         # 后端服务
│   ├── zdmj/        # Java Spring Boot 主服务
│   └── zdmj-python/ # Python FastAPI AI服务
├── client/          # 客户端
├── docs/            # 项目文档
├── deploy/          # 部署配置（Docker、Nginx等）
└── sql/             # 数据库脚本
```

## 技术栈

| 分类          | 技术                                                       |
| ------------- | ---------------------------------------------------------- |
| Java 主服务   | Spring Boot、MyBatis-Plus、Spring Security、Spring WebFlux |
| Python AI服务 | FastAPI、asyncpg                                           |
| 数据库        | pgSQL、Redis                                               |
| 第三方工具    | JWT、腾讯云COS SDK                                         |
| DevOps        | Docker、Nginx、Github Actions                              |
