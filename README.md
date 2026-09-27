# litemall-next

Spring Cloud 微服务商城（教学/参考实现）。后端 12 个 Maven 模块 + 两个 Vue3 前端。

- 后端：JDK 25 / Spring Boot 4.0.8 / Spring Cloud 2025.1.2 / Spring Cloud Alibaba 2025.1.0.0 / MyBatis-Plus 3.5.17
- 前端：Vue 3.5 + Vite 8（`admin-web` 用 Element Plus，`mobile-web` 用 Vant 4）
- 中间件：MySQL 8、Redis、Nacos（注册 + 配置）、Seata、RocketMQ（仅 marketing）

---

## 1. 架构

```
                      ┌─────────────────┐   ┌─────────────────┐
                      │ admin-web :5174 │   │ mobile-web :5173│   (Vite dev server，
                      └────────────────┘   └────────┬────────┘    /admin、/app 代理到 8000)
                               └──────────┬──────────┘
                                          ▼
                              ┌───────────────────────┐
                              │  litemall-gateway :8000│  WebFlux
                              │  · JWT 签名/过期/角色/受众 │
                              │  · 路径前缀 RBAC        │
                              │  · 剥离 user-info /      │
                              │    X-Internal-Token      │
                              │  · 白名单（精确路径）     │
                              └-----------┬────────────┘
                                          │ lb://（Nacos 服务发现）
   ┌──────────┬──────────┬──────────┬─────┴────┬────────────────────┬──────────
   ▼          ▼          ▼          ▼          ▼          ▼          ▼          ▼
 user:8001 goods:8002 mkt:8003  order:8004  pay:8005  msg:8006  storage:8007 admin:8008
   │          │          │          │          │          │          │          │
   └────────────────────┴─────┬────┴──────────┴──────────┴──────────┘          │
                               │  跨服务仅经 litemall-api 的 Feign 契约（/inner/**）▼
                               │  + X-Internal-Token 共享密钥头
                    ┌──────────┴──────────────────────────────────────┐
                    │ MySQL litemall 库 │ Redis │ Nacos │ Seata │ RocketMQ │
                    └────────────────────────────────────────────────────┘

 共享库：litemall-common（M16 后为纯 POM 聚合层：core/web/mybatis/cache/notify）、
          litemall-common-jwt、litemall-api
```

分层约定：`Controller → IService / ServiceImpl(MyBatis-Plus) → Mapper`；**跨服务只允许走 Feign 契约**，
禁止直连他域表。对外 HTTP 统一 `{code, msg, data}`（`com.litemall.domain.Result`），**成功码 = 200**。

## 2. 端口表

| 进程 | 端口 | 说明 | 对外 |
|---|---|---|---|
| `litemall-gateway` | **8000** | 唯一业务入口 | 是 |
| `litemall-user` | 8001 | 登录/注册/短信/微信登录/地址/资料 | 否 |
| `litemall-goods` | 8002 | 商品/分类/品牌/评论/搜索/库存 | 否 |
| `litemall-marketing` | 8003 | 优惠券/广告/专题/公告/团购 | 否 |
| `litemall-order` | 8004 | 购物车/订单(Seata)/售后/收藏/足迹 | 否 |
| `litemall-pay` | 8005 | 支付下单/回调/退款 | 否 |
| `litemall-message` | 8006 | 站内信/用户反馈 | 否 |
| `litemall-storage` | 8007 | 文件上传 + `/storage/**` 静态访问 | 否 |
| `litemall-admin` | 8008 | 管理端（自带二次鉴权过滤链） | 否 |
| `admin-web`（Vite） | 5174 | 管理后台 dev server | 开发机 |
| `mobile-web`（Vite） | 5173 | C 端 H5 dev server | 开发机 |
| Nacos | 8848 | 注册 + 配置中心 | 基础设施 |
| Seata Server | 8091 | 分布式事务（`default_tx_group`） | 基础设施 |
| RocketMQ nameserver | 9876 | 仅 `litemall-marketing` 使用 | 基础设施 |
| Sentinel dashboard | 8090 | 配置残留，**当前所有服务 `sentinel.enabled=false`** | 可选 |

> 8001–8008 默认绑定 `127.0.0.1`（`HM_SERVER_BIND`），容器内跨主机部署时需显式改为 `0.0.0.0` 并置于内网。

## 3. 路由与鉴权（网关）

| 路径前缀 | 目标服务 | 要求角色 / 受众 |
|---|---|---|
| `/app/auth/**` `/app/user/**` `/app/address/**` | litemall-user | `user` / `litemall-app` |
| `/app/goods/**` `/app/search/**` | litemall-goods | 同上 |
| `/app/cart/**` `/app/order/**` `/app/aftersale/**` `/app/collect/**` `/app/footprint/**` | litemall-order | 同上 |
| `/app/market/**` | litemall-marketing | 同上 |
| `/app/pay/**` | litemall-pay | 同上 |
| `/app/msg/**` `/app/feedback/**` | litemall-message | 同上 |
| `/app/storage/**` `/storage/**` | litemall-storage | 仅需有效令牌（未列入 roleRules） |
| `/admin/**` | litemall-admin | `admin` / `litemall-admin` |

免登录白名单（精确路径，不通配）：`/app/auth/{login,register,wx-login,captcha}`、
`/app/goods/{list,detail,category,brand/list,comment/list}`、
`/app/market/{ad/list,coupon/list,groupon/list,topic/list,notice/list}`、
`/app/pay/notify/wechat`（免登录 ≠ 免校验，必须在 pay 服务内通过微信 v3 验签）、`/admin/auth/login`。

`/inner/**` 不经网关（网关会剥离外部 `X-Internal-Token`），由各服务的 `InnerApiInterceptor` 校验共享密钥头；
Feign 出站由 `litemall-api` 的 `FeignInnerConfig` 自动附加。

## 4. 环境变量

| 变量 | 必需性 | 默认值 | 说明 |
|---|---|---|---|
| `HM_ACTIVE_PROFILE` | 建议显式 | `dev` | **生产必须 `prod`**；dev 才有 mock 微信登录与宽松校验 |
| `HM_JKS_PASSWORD` | **必需**(gateway/user/admin) | 空 → 启动失败 | JWT 私钥库口令（H4） |
| `HM_JKS_LOCATION` / `HM_JKS_ALIAS` | 可选 | `classpath:litemall.jks` / `hmall` | 密钥库位置与别名 |
| `HM_INTERNAL_TOKEN` | **非 dev 必需**(全部服务一致) | 空 | `/inner/**` 共享密钥；非 dev 为空则启动失败（H3） |
| `HM_SERVER_BIND` | 按需 | `127.0.0.1` | 容器/对外部署设 `0.0.0.0` |
| `HM_NACOS_ADDR` | 按需 | `192.168.96.129:8848` | Nacos 地址（配置 + 注册）。**默认值是本项目 dev 环境实测可达的中间件，不是回环地址**；换环境请整体覆盖 |
| `HM_SEATA_ADDR` | 按需 | `192.168.96.129:8848` | Seata 的 `registry.type=nacos`，故这里填 **Nacos** 地址（不是 Seata 自身的 8091） |
| `HM_ROCKETMQ_ADDR` | 按需(marketing) | `192.168.96.129:9876` | RocketMQ nameserver |
| `HM_DB_HOST` / `HM_DB_PORT` / `HM_DB_NAME` | 按需 | `127.0.0.1` / `3306` / `litemall` | 数据库定位 |
| `HM_DB_USER` | 按需 | `root` | **生产必须换受限账号** |
| `HM_DB_PW` | **必需**(prod) | dev 取 `application-dev.yaml` | 数据库口令（M12） |
| `HM_REDIS_HOST` / `HM_REDIS_PORT` / `HM_REDIS_PW` | 按需 | `127.0.0.1` / `6379` / 空 | Redis |
| `HM_LOG_LEVEL_APP` | 可选 | dev `DEBUG` / prod `INFO` | 应用日志级别（`com.litemall`） |
| `HM_STORAGE_DIR` | 可选 | `./storage` | 上传文件落盘目录 |
| `HM_STORAGE_MAX_SIZE` | 可选 | `10485760`（10MB） | L7 单文件上限；实现内另以 `Integer.MAX_VALUE` 兜底（DB 列为 `int`），超限直接拒绝 |
| `HM_STORAGE_ALLOWED_EXT` | 可选 | `jpg,jpeg,png,gif,webp,bmp,pdf` | L7 扩展名白名单，另有 MIME 纵深校验 |
| `HM_WX_PAY_ENABLED` / `HM_WX_PAY_CERT` / `HM_WX_PAY_APIV3_KEY` | 生产支付**必需** | `false` / 空 / 空 | 微信 v3 回调验签（H2）；enabled=true 而缺证书/密钥则启动失败 |
| `HM_WX_MOCK_ENABLED` | 仅 dev | dev `true` | 打开 `MockWechatMiniService`（H8） |

## 5. 启动顺序

前置：JDK 25、Maven 3.9+、MySQL（`litemall` 库，36 张业务表 + `undo_log`）、Redis、Nacos。

```powershell
# 1) 构建（仓库根执行）
cd backend\litemall-next
mvn -B clean install -DskipTests

# 2) 注入本地开发所需变量（dev profile 下 HM_INTERNAL_TOKEN 可为空，仅告警）
$env:HM_JKS_PASSWORD = '<本地密钥库口令>'

# 3) 先起业务服务，再起网关（网关依赖 Nacos 发现业务实例）
foreach ($m in 'user','goods','marketing','order','pay','message','storage','admin') {
  Start-Process -ArgumentList "spring-boot:run -pl litemall-$m" -FilePath mvn -NoNewWindow
}
mvn -B spring-boot:run -pl litemall-gateway

# 4) 前端（另开终端）
cd frontend\mobile-web ; npm ci ; npm run dev    # :5173
cd frontend\admin-web  ; npm ci ; npm run dev    # :5174
```

**Nacos 是运行必需**：配置导入写成 `optional:nacos:` 只让"启动"能在无 Nacos 时降级，
但网关路由是 `lb://服务名`，Nacos 不可用时所有网关转发都会 503。本地无 Nacos 时请直连业务端口验证。

容器编排见第 7 节；CI 见第 8 节。

## 6. 模块清单

| 模块 | packaging | 职责 |
|---|---|---|
| `litemall-next` | pom | 聚合父 POM + 版本收口 |
| `litemall-common` | **pom** | M16 后为纯聚合层（无代码）：5 个拆分层 + 服务端中间件（Nacos 注册/配置、Seata、RocketMQ、Feign OkHttp） |
| `litemall-common-core` | jar | `Result`/`TokenPrincipal`、异常体系、通用工具、`InnerApiProperties`。**不含** Servlet/数据访问/Redis |
| `litemall-common-web` | jar | 全局异常处理、身份与内部接口拦截器、`MvcConfig`/`JsonConfig`/`SentinelWebConfig`、`WebUtils`、`CookieBuilder` |
| `litemall-common-mybatis` | jar | `MyBatisConfig`（分页插件）、`PageVO`/`PageQuery`、MySQL 驱动 |
| `litemall-common-cache` | jar | `CacheService`（Redis + Caffeine 二级缓存）与自动配置 |
| `litemall-common-notify` | jar | `NotifyService` 与短信发送实现（阿里云/腾讯/Noop） |
| `litemall-common-jwt` | jar | JWT 单一实现：`JwtTool` / `JwtProperties` / 自动装配 |
| `litemall-api` | jar | Feign 契约：`GoodsClient` / `OrderClient` / `UserClient` / `MarketingClient` + `domain.dto` |
| `litemall-gateway` | jar | :8000 WebFlux 网关 |
| `litemall-{user,goods,marketing,order,pay,message,storage,admin}` | jar | 8 个业务服务 |

## 7. 容器化

```powershell
# 全栈：中间件 + 9 个后端服务，见 docker/docker-compose.yml
cd docker
cp .env.example .env          # 填 HM_DB_PW / HM_INTERNAL_TOKEN / HM_JKS_PASSWORD
docker compose up -d mysql redis nacos seata rocketmq-namesrv rocketmq-broker
docker compose up -d --build gateway user goods marketing order pay message storage admin

# 单个服务镜像：上下文根 = backend/litemall-next，Dockerfile 在仓库根的 docker/ 下
cd backend\litemall-next
mvn -B -DskipTests package
docker build -f ..\..\docker\Dockerfile --build-arg MODULE=litemall-gateway -t litemall-gateway:local .
```

- 镜像基于 `eclipse-temurin:25-jre`，JAR 路径约定 `backend/litemall-next/<module>/target/<module>.jar`。
  **P2/L8 起 9 个可运行模块全部声明了 `spring-boot-maven-plugin`**（此前只有网关有），
  实测产物为可执行 fat jar（`Main-Class: org.springframework.boot.loader.launch.JarLauncher`、含 `BOOT-INF/`）。
  `litemall-common`（纯 POM）/`litemall-common-{core,web,mybatis,cache,notify}`/`litemall-common-jwt`/`litemall-api`
  是库模块，**不得**加该插件，也不能作为镜像目标。
- ⚠️ **本机未安装 Docker，compose 与镜像 tag 未经实跑**，只做了 YAML 语法校验。
  首次使用请以 `docker compose config` 与逐服务启动日志为准。
- ⚠️ 仓库内**没有任何建库/种子 SQL**（只有 `db-migration-M10-bigint.sql` 这个迁移差量），
  所以 MySQL 不会自动初始化：请把导出脚本放进 `docker/mysql/init/*.sql`。
- ⚠️ 服务默认用 `HM_ACTIVE_PROFILE=dev`。**prod profile 会强制从 Nacos 导入
  `${spring.application.name}.yaml`（M12 引入，非 optional）**，Nacos 里没有该配置时会启动失败——
  这是刻意的：配置缺失要尽早暴露，而不是静默用错配置。
- `.dockerignore` 位于 `backend/litemall-next/`（Docker 只读上下文根），已排除 `**/*.jks`，
  密钥库不进镜像层（H4）。
- compose 把 `HM_SERVER_BIND` 覆盖为 `0.0.0.0`，并**把 9 个服务的端口都映射到宿主机**（8000–8008），
  这是为本地联调方便。**生产部署只应暴露网关 8000**，其余服务改用 `expose` 或不映射。
- `docker/.env.example` 是变量模板，必须复制为 `docker/.env`；真实口令不要入库。

## 8. CI

- `.github/workflows/frontend-ci.yml`（P1/M15）：matrix 跑 `mobile-web`、`admin-web`，
  执行 `npm ci` → `npm run lint` → `npm run build`，node 24。
- `.github/workflows/backend-ci.yml`（P2/L8）：JDK 25 + Maven cache，跑 `mvn -B verify`。
  刻意不与前端那份合并，避免改动已验收的 `frontend-ci.yml`。
- **backend-ci 只覆盖"基础设施无关"子集**，两点必须知道：
  1. `.gitignore` 忽略 `*.jks`（H4），干净 runner 上没有密钥库，上下文测试会以
     `Cannot load keys from store: class path resource [litemall.jks] does not exist` 失败。
     因此 workflow **现场用 `keytool` 生成一次性自签密钥库**（别名 `hmall`、口令 `hmall123`，与测试期望一致），
     仅用于跑测试，不是生产密钥。
  2. `StockDeductBatchTest` 连**真实 MySQL** 且依赖既有数据，而仓库没有建库 SQL，
     无法在 runner 上造库，故用 `-Dtest='!StockDeductBatchTest'` 排除，并以 `-Dseata.enabled=false`
     跳开 `192.168.96.129` 的 Seata 注册中心。
- 本地已实测等价的 CI 命令通过（118 个测试，0 失败；较完整套件少掉的正是被排除的 2 个）：

本地等价命令（仓库根）：

```powershell
cd backend\litemall-next ; $env:HM_JKS_PASSWORD='hmall123'; mvn -B verify   # 完整套件，120 个测试，需 dev 中间件
cd frontend/mobile-web   ; npm ci ; npm run lint ; npm run build
cd frontend/admin-web    ; npm ci ; npm run lint ; npm run build
```

## 9. 测试

下表是实测分布（`mvn -B verify` 全量 = **120 个用例，0 失败**；backend-ci 子集 = 118）。

| 模块 | 测试类（用例数） | 小计 |
|---|---|---|
| `litemall-common` | 纯 POM 聚合层（M16），无测试 | 0 |
| `litemall-common-core` | 无测试（`Result`/异常/工具） | 0 |
| `litemall-common-web` | `InnerApiInterceptorTest`(4：内部密钥头校验/401) | 4 |
| `litemall-common-mybatis` | 无测试 | 0 |
| `litemall-common-cache` | `CacheServiceTest`(4：M7 解码失败抛异常并删损坏 key、空值缓存、H7 SCAN+批量删+广播、M8 单 key 广播) | 4 |
| `litemall-common-notify` | 无测试 | 0 |
| `litemall-common-jwt` | `JwtToolTest`(6：往返、过期拒绝、空令牌、篡改拒绝、缺 role/aud 旧令牌拒绝、异密钥拒绝) | 6 |
| `litemall-gateway` | `AuthGlobalFilterTest`(6：角色/受众/白名单/头剥离) + 上下文(1) | 7 |
| `litemall-goods` | `StockDeductBatchTest`(2，**连真实 MySQL**：整批回滚/充足生效) + 上下文(1) | 3 |
| `litemall-marketing` | `CouponServiceImplTest`(9：MQ 失败回滚限领、预减台账回补、TTL 四例) + 上下文(1) | 10 |
| `litemall-message` | `MessageServiceImplTest`(7) + 上下文(1) | 8 |
| `litemall-order` | `OrderAdminStateMachineTest`(9)、`OrderAdminInnerApiTest`(5)、`OrderIdempotentTest`(3：M4 状态机)、`InnerApiSecurityTest`(2：`/inner/**` 401)、`CartServiceImplTest`(16：M14 归属完整性/金额钳制/数量上限) + 上下文(1) | 36 |
| `litemall-pay` | `PayServiceImplTest`(16：验签失败/幂等/金额不符/RPC 失败释放) + 上下文(1) | 17 |
| `litemall-storage` | `StorageServiceImplTest`(7：空文件、正常落盘、L7 白名单/无扩展名/目录穿越/超限/Content-Type) + 上下文(1) | 8 |
| `litemall-user` | 上下文(1) | 1 |
| `litemall-admin` | `AdminOrderFeignTest`(5，P1)、`AdminDtoValidationTest`(11：M13 各 DTO 约束 + `/admin/auth/login` 的 400 行为 + admin 上下文，该上下文测试以 `seata.enabled=false` 保持与外部中间件无关) | 16 |
| `litemall-api` | 无测试 | 0 |
| **合计** | | **120**（CI 子集 118，排除 `StockDeductBatchTest` 的 2 个） |

上下文测试需要 `HM_JKS_PASSWORD`（dev 值 `hmall123`），CI 与本节均以该值注入。

> `litemall-admin` 长期没有 `src/test`（P1 只补了 `AdminOrderFeignTest`，仍是纯 Mockito，**不启动上下文**），
> 其直接后果是：admin 的 `application.yaml` 引用了**无默认值**的 `${hm.db.host}` / `${hm.db.pw}` 却没有
> `application-dev.yaml`，**admin 在 dev 下无法启动**，而这一断裂躲过了 P0/P1 的全部验收（该模块 0 个上下文测试）。
> P2/M12 修复了配置；P2/M13 补上了 admin 的**首个上下文测试**（`AdminDtoValidationTest`），
> 并顺带以 `seata.enabled=false` 使其不依赖外部 Seata 注册中心。

## 10. 已知限制

1. **Docker 与 backend-ci 都未实跑**：本机没有安装 Docker，compose 与镜像 tag 未经执行验证（仅做了 YAML 语法校验）；
   backend-ci 只覆盖基础设施无关子集（见第 8 节），完整套件必须在有 dev 中间件的机器上跑。
2. **仓库内没有建库/种子 SQL**：只有 `db-migration-M10-bigint.sql` 这个迁移差量，
   新环境无法一键起库——compose 的 MySQL 需要你自备导出脚本放进 `docker/mysql/init/*.sql`。
3. **M10 漏了 storage**：全仓 PO 的 `id` 只剩 `Storage.java` 仍是 `Integer`（其余均已统一为 `Long`），
   跨服务调用存在同型截断隐患；修正它需要 `litemall_storage` 表的 DDL，故以【需人工确认】形式保留，**未执行**。
4. 微信支付的真实商户号端到端联调未做（无资质），当前为"骨架 + dev 明文回调 + prod fail-fast"。
5. 密钥库 `litemall.jks` 仍在本地磁盘与 git 历史中，**未轮换、未清史**，口令应视为已泄露。
6. `mobile-web` 的 `GoodsDetail.vue` 有一处 `v-html` 渲染后端 HTML（ESLint `vue/no-v-html` 告警刻意保留），
   是真实 XSS 关注点，正解是接入 sanitize（如 DOMPurify）。
7. **M16 只瘦身了网关**：其余 11 个模块仍依赖 `litemall-common` 聚合层（依赖树与拆分前**逐条一致**）。
   按模块进一步裁剪依赖（例如纯计算型服务去掉文档 UI / Redis）属后续优化，本次刻意未做，
   以把拆分风险限制在"可验证的等价重构"范围内。
8. `litemall-common/src/main/resources/META-INF/spring-configuration-metadata.json` 的内容仍是
   hmall 时代的错误元数据（声明 `com.hmall.config.*`、`hm.mq.*` RabbitMQ 属性、`hm.db.pw` 默认 `"123"`）；
   common 现为 pom 打包，该文件**不会**被打进任何 jar，属惰性残留，**未修改**（超出报告范围）。
9. `PageQuery`（`common-mybatis`）在全仓**无任何引用**，疑似死代码；因不在报告清单内，**未删除**。
