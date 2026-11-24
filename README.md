# 校园社团活动管理系统

面向高校社团活动的全流程管理系统，覆盖账号与权限、社团入驻、活动发布与报名、审核、签到、归档、统计、消息与审计。

## 技术栈

- 后端：Spring Boot 2.7、MyBatis-Plus、JWT、H2 / MySQL 8
- 前端：Vue 3、Vite、Element Plus、Axios
- 测试：JUnit 5、Spring Boot Test、H2 内存库

## 目录结构

```
campus-activity-system/
├── backend/                 后端服务（端口 8080）
│   ├── src/main/java/com/cqu/campus/
│   │   ├── config/          配置、演示数据初始化
│   │   ├── controller/      REST 接口
│   │   ├── service/         业务逻辑
│   │   ├── domain/          实体、DTO、VO
│   │   ├── mapper/          MyBatis-Plus Mapper
│   │   └── security/        JWT 与登录上下文
│   ├── src/main/resources/  application.yml、schema.sql
│   └── src/test/            启动与登录冒烟测试
├── frontend/                前端工程（端口 5173，/api 代理到 8080）
├── sql/mysql_schema.sql     MySQL 建表脚本
└── README.md
```

## 快速开始

### 方式一：H2 演示模式（零依赖，推荐答辩演示）

```powershell
cd backend
$env:SPRING_PROFILES_ACTIVE="demo"
mvn spring-boot:run
```

另开一个终端：

```powershell
cd frontend
npm install
npm run dev
```

浏览器访问 http://localhost:5173 。首次启动会自动建表并写入演示数据。

### 方式二：MySQL

1. 创建数据库并导入表结构：

```sql
CREATE DATABASE campus DEFAULT CHARACTER SET utf8mb4;
```

```powershell
mysql -uroot -p campus < sql/mysql_schema.sql
```

2. 配置数据库密码后启动：

```powershell
cd backend
$env:DB_PASSWORD="你的MySQL密码"
mvn spring-boot:run
```

数据库地址、账号可在 `backend/src/main/resources/application.yml` 修改。首次启动时若用户表为空，会自动写入角色与演示账号。

## 演示账号

首次启动（H2 演示模式或 MySQL 用户表为空）会自动创建以下账号，初始密码均为 `123456`：

| 账号 | 角色 | 密码 | 说明 |
| --- | --- | --- | --- |
| admin | 平台管理员 | 123456 | 入驻审核、平台统计、操作审计、冻结/解冻社团 |
| clubadmin | 社团管理员 | 123456 | 篮球社负责人，活动管理、报名审核、签到、归档 |
| student | 学生 | 123456 | 演示学生一，含待审核报名与已签到记录 |
| student2 | 学生 | 123456 | 演示学生二，含一条待审核的社团入驻申请 |

## 功能清单

- 账号：注册、登录、个人资料修改、角色菜单
- 社团：学生入驻申请、平台审核、社团资料维护、冻结与解冻
- 活动：草稿/发布/满员/关闭/归档状态流转、标签、平台违规下架、删除草稿
- 报名：学生报名与撤销、社团审核、待审名额不超剩余名额、活动开始后禁止撤销、活动关闭或下架时自动驳回待审报名并通知
- 签到：学生自助签到、负责人名单代登记、学生补签申请与负责人审核、签到记录与 CSV 导出
- 归档：多文件上传、公开/社团可见/私有三级可见范围、受控下载
- 统计：平台与社团维度概览、报名与签到统计
- 消息：报名与审核结果通知、未读数、失败状态记录
- 审计：关键操作留痕，平台按操作类型/资源查询

## 测试与构建

```powershell
cd backend
mvn test               # 运行冒烟测试（H2 内存库）
mvn -DskipTests package

cd frontend
npm run build          # 产物在 frontend/dist
```

## 主要配置

| 配置项 | 说明 | 默认值 |
| --- | --- | --- |
| `DB_PASSWORD` | MySQL 密码 | 空 |
| `JWT_SECRET` | JWT 签名密钥（生产必须覆盖，长度 ≥ 32） | 演示密钥 |
| `app.file.root` | 上传文件目录 | `./uploads` |
| `app.file.max-size-mb` | 单文件大小上限 | 20 |
| `app.cors.allowed-origins` | 允许的前端来源 | `http://localhost:5173` |

## 接口约定

- 统一响应：`{ code, message, data, traceId }`，成功时 `code = 0`
- 认证：请求头 `Authorization: Bearer <token>`
- 匿名接口：注册、登录、活动列表 `GET /api/activities`
- 审计查询：`GET /api/audit-logs`（仅平台管理员）
