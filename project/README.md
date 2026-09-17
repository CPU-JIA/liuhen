# 留痕 项目代码

Sprint 1 代码。后端 Spring Boot 3.5 / Java 25 / MySQL 8.4，前端 Vue 3 / Vite / TypeScript。设计依据见 `../docs/Exp03-系统架构与数据库设计/04-最终版/系统架构设计说明.md`，任务依据见 `../docs/Exp04-Sprint1任务拆分与工时估算/04-最终版/Sprint1任务清单与工时估算表.md`。

## 本机运行

后端（需要一个空的 MySQL 8.4 库，表结构由 Flyway 自动建）：

```bash
cd backend
LIUHEN_DB_URL="jdbc:mysql://127.0.0.1:3306/liuhen?characterEncoding=UTF-8&serverTimezone=Asia/Shanghai" \
LIUHEN_DB_USER=root LIUHEN_DB_PASSWORD=你的口令 \
mvn spring-boot:run -Dspring-boot.run.profiles=dev
```

dev 配置会在空库里种三个账号：`A0001` 管理员、`T0001` 教师、`J0001` 教务，密码取环境变量 `LIUHEN_SEED_PASSWORD`，默认 `Liuhen@2026`。学生账号由教师导入名单生成，初始密码为学号后 6 位，首次登录必须改密。

前端：

```bash
cd frontend
pnpm install
pnpm dev          # http://localhost:5173，/api 代理到 8080
pnpm build        # 类型检查加打包
pnpm scan:words   # 文案禁用词扫描
```

## 测试

```bash
cd backend && mvn test            # 178 条单元测试，覆盖率报告在 target/site/jacoco/index.html
cd frontend && pnpm test          # 29 条 vitest，jsdom 环境
BASE=http://127.0.0.1:8080 bash scripts/smoke-sprint1.sh   # 43 条冒烟断言，需要 dev 配置与空库
```

集成测试（Flyway 建表、实体映射、触发器、跨事务的锁定与改密）要一个真 MySQL，设了环境变量才跑，默认跳过：

```bash
LIUHEN_IT_DB_URL="jdbc:mysql://127.0.0.1:3306/liuhen_it?characterEncoding=UTF-8&serverTimezone=Asia/Shanghai" LIUHEN_IT_DB_USER=root LIUHEN_IT_DB_PASSWORD=你的口令 mvn test      # 多跑 5 条
```

集成测试的数据带时间戳，同一个库可以反复跑。冒烟脚本每条断言带实验 2 的验收编号，失败时打印响应体。测试用例与验收编号的对照见 `../docs/Exp06-Sprint1单元测试与代码审查/04-最终版/单元测试用例.md`。

## 容器

```bash
LIUHEN_DB_PASSWORD=改一个 LIUHEN_JWT_SECRET=至少32字节的随机串 docker compose up --build
```

三个服务：mysql、backend（8080）、frontend（80，nginx 反代 /api）。

## 目录

| 目录                                    | 内容                                                                                               |
| --------------------------------------- | -------------------------------------------------------------------------------------------------- |
| backend/src/main/java/cn/liuhen         | 按史诗分包：account、policy、work、registration、declaration、timeline、security、evidence、common |
| backend/src/main/resources/db/migration | Flyway 脚本，来自实验 3 的 schema-sprint1.sql                                                      |
| backend/src/test                        | 按包对应的单元测试；integration/ 下是需要真库的集成测试                                            |
| frontend/src/views                      | 登录、改密、课程、作业、编辑器、时间线、声明；同目录 *.test.ts 是页面测试                          |
| scripts                                 | 冒烟脚本                                                                                           |

## 约定

- 没有删除接口。快照、登记、声明只增；登记的修改是新增一条并作废旧条。
- 时间戳全部服务端生成。计数单位统一为字符（Unicode 码点）。
- 教师端、复核端、教务端文案不出现禁用词，后端单测与前端脚本各扫一遍。
- 验收数字集中在 `application.yml` 的 `liuhen.*`，实验 4 说要复核的数字改那里。
- 名单导入时已存在的账号只加入课程，不覆盖姓名与密码。
