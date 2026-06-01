# CoolShark 电商平台 — Android 端开发文档

> 适用场景：Android (Java) 客户端开发，调用 CoolShark 微服务后端 API  
> 更新日期：2026-05-28

---

## 目录

- [一、Android 项目搭建步骤](#一android-项目搭建步骤)
- [二、后端接口对接配置](#二后端接口对接配置)
- [三、功能清单](#三功能清单)

---

## 一、Android 项目搭建步骤

### 1.1 创建新项目

在 IntelliJ IDEA 中操作：

1. **File → New → New Project**
2. 左侧选择 **New Project**（非 New Module）
3. 填写：
   - Name: `CoolSharkAndroid`
   - Location: `D:\java\csmall-android`
   - Language: **Java**
   - Build system: **Gradle**
   - JDK: 选择 JDK 17 或更高
4. 点击 **Create**

### 1.2 项目目录结构

```
csmall-android/
├── app/
│   ├── src/
│   │   ├── main/
│   │   │   ├── java/com/cooxiao/android/
│   │   │   │   ├── api/              # Retrofit 接口定义
│   │   │   │   │   ├── ApiConstants.java      # 服务器地址常量
│   │   │   │   │   ├── AuthApi.java           # 登录/注册 API
│   │   │   │   │   ├── ProductApi.java        # 商品 API
│   │   │   │   │   ├── CartApi.java           # 购物车 API
│   │   │   │   │   ├── OrderApi.java          # 订单 API
│   │   │   │   │   ├── SeckillApi.java        # 秒杀 API
│   │   │   │   │   ├── SearchApi.java         # 搜索 API
│   │   │   │   │   ├── AddressApi.java        # 地址管理 API
│   │   │   │   │   └── UploadApi.java         # 文件上传 API
│   │   │   │   ├── model/            # 数据模型（与后端 DTO/VO 对应）
│   │   │   │   │   ├── JsonResult.java        # 统一响应封装
│   │   │   │   │   ├── JsonPage.java          # 分页响应封装
│   │   │   │   │   ├── TokenVO.java           # 登录返回 Token
│   │   │   │   │   ├── UserLoginDTO.java      # 登录请求体
│   │   │   │   │   ├── UserRegistryDTO.java   # 注册请求体
│   │   │   │   │   ├── SpuListItemVO.java     # SPU 列表项
│   │   │   │   │   ├── SpuStandardVO.java     # SPU 详情
│   │   │   │   │   ├── SkuStandardVO.java     # SKU 数据
│   │   │   │   │   ├── CategoryVO.java        # 分类数据
│   │   │   │   │   ├── CartStandardVO.java    # 购物车项
│   │   │   │   │   ├── OrderAddDTO.java       # 创建订单请求
│   │   │   │   │   ├── OrderAddVO.java        # 创建订单响应
│   │   │   │   │   ├── OrderDetailVO.java     # 订单详情
│   │   │   │   │   ├── ImageFileVO.java       # 上传图片响应
│   │   │   │   │   └── DeliveryAddressVO.java # 地址数据
│   │   │   │   ├── network/          # 网络层
│   │   │   │   │   ├── RetrofitClient.java    # Retrofit 单例
│   │   │   │   │   ├── AuthInterceptor.java   # 自动添加 Token 拦截器
│   │   │   │   │   └── ResponseHandler.java   # 统一响应处理
│   │   │   │   ├── ui/               # 页面
│   │   │   │   │   ├── login/        # 登录/注册
│   │   │   │   │   ├── product/      # 商品列表/详情
│   │   │   │   │   ├── cart/         # 购物车
│   │   │   │   │   ├── order/        # 订单
│   │   │   │   │   ├── seckill/      # 秒杀
│   │   │   │   │   ├── search/       # 搜索
│   │   │   │   │   ├── profile/      # 个人中心/地址管理
│   │   │   │   │   └── camera/       # CameraX 拍照上传
│   │   │   │   └── utils/            # 工具类
│   │   │   │       └── TokenManager.java     # Token 本地存储
│   │   │   ├── res/                  # 资源文件
│   │   │   └── AndroidManifest.xml
│   │   └── test/                     # 单元测试
│   ├── build.gradle.kts
│   └── proguard-rules.pro
├── settings.gradle.kts
├── build.gradle.kts                  # 根构建文件
├── gradle.properties
└── local.properties
```

### 1.3 Gradle 依赖配置

**根 `build.gradle.kts`：**

```kotlin
plugins {
    id("com.android.application") version "8.2.0" apply false
}
```

**`settings.gradle.kts`：**

```kotlin
pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}
rootProject.name = "CoolSharkAndroid"
include(":app")
```

**`app/build.gradle.kts`（核心依赖部分）：**

```kotlin
android {
    namespace = "com.cooxiao.android"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.cooxiao.android"
        minSdk = 26
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    // AndroidX
    implementation("androidx.appcompat:appcompat:1.6.1")
    implementation("androidx.constraintlayout:constraintlayout:2.1.4")
    implementation("androidx.lifecycle:lifecycle-runtime:2.7.0")

    // CameraX（拍照核心）
    implementation("androidx.camera:camera-core:1.3.1")
    implementation("androidx.camera:camera-camera2:1.3.1")
    implementation("androidx.camera:camera-lifecycle:1.3.1")
    implementation("androidx.camera:camera-view:1.3.1")

    // Retrofit + OkHttp（网络请求）
    implementation("com.squareup.retrofit2:retrofit:2.9.0")
    implementation("com.squareup.retrofit2:converter-gson:2.9.0")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("com.squareup.okhttp3:logging-interceptor:4.12.0")

    // Gson（JSON 解析）
    implementation("com.google.code.gson:gson:2.10.1")

    // Glide（图片加载）
    implementation("com.github.bumptech.glide:glide:4.16.0")
}
```

### 1.4 AndroidManifest 权限

```xml
<uses-permission android:name="android.permission.INTERNET" />
<uses-permission android:name="android.permission.CAMERA" />
<uses-permission android:name="android.permission.WRITE_EXTERNAL_STORAGE"
    android:maxSdkVersion="28" />

<application
    android:usesCleartextTraffic="true"
    ...>
    <!-- 生产环境域名，DNS 解析通过 -->
</application>
```

> `android:usesCleartextTraffic="true"` 是因为生产环境使用 HTTP（尚未配置 HTTPS）。上线 HTTPS 后可移除。

### 1.5 关键网络层代码

**`ApiConstants.java`：**

```java
public class ApiConstants {
    // 生产环境：通过阿里云 Nginx（端口 80）
    public static final String BASE_URL = "http://8.156.85.160/";

    // 本机测试（模拟器 → 宿主机）：如果后端在本地运行
    // public static final String BASE_URL = "http://10.0.2.2:10087/";

    // 从 /user/sso/login 响应中获取
    public static final String TOKEN_HEADER = "Authorization";
    public static final String TOKEN_PREFIX = "Bearer ";

    // 分页默认值
    public static final int DEFAULT_PAGE = 1;
    public static final int DEFAULT_PAGE_SIZE = 10;
}
```

**`RetrofitClient.java`：**

```java
public class RetrofitClient {
    private static volatile Retrofit retrofit;

    public static Retrofit getInstance() {
        if (retrofit == null) {
            synchronized (RetrofitClient.class) {
                if (retrofit == null) {
                    OkHttpClient client = new OkHttpClient.Builder()
                        .addInterceptor(new AuthInterceptor())  // 自动携带 Token
                        .addInterceptor(new HttpLoggingInterceptor()
                            .setLevel(HttpLoggingInterceptor.Level.BODY))
                        .connectTimeout(15, TimeUnit.SECONDS)
                        .readTimeout(15, TimeUnit.SECONDS)
                        .writeTimeout(30, TimeUnit.SECONDS)  // 上传文件需要较长超时
                        .build();

                    retrofit = new Retrofit.Builder()
                        .baseUrl(ApiConstants.BASE_URL)
                        .client(client)
                        .addConverterFactory(GsonConverterFactory.create())
                        .build();
                }
            }
        }
        return retrofit;
    }
}
```

**`AuthInterceptor.java`：**

```java
public class AuthInterceptor implements Interceptor {
    @Override
    public Response intercept(Chain chain) throws IOException {
        Request original = chain.request();
        String token = TokenManager.getToken();

        // 登录、注册、商品浏览等公开接口不需要 Token
        String path = original.url().encodedPath();
        if (path.contains("/user/sso/login")
            || path.contains("/ums/user/register")
            || path.contains("/ums/user/checkValue")
            || path.contains("/front/")
            || path.contains("/search/")
            || path.contains("/seckill/spu")
            || path.contains("/seckill/sku")
            || path.contains("/upload/")) {
            return chain.proceed(original);
        }

        // 需要认证的接口添加 Token
        if (token != null) {
            Request.Builder builder = original.newBuilder()
                .header(ApiConstants.TOKEN_HEADER,
                        ApiConstants.TOKEN_PREFIX + token);
            return chain.proceed(builder.build());
        }
        return chain.proceed(original);
    }
}
```

**`TokenManager.java`：**

```java
public class TokenManager {
    private static final String PREF_NAME = "coolshark_pref";
    private static final String KEY_TOKEN = "jwt_token";
    private static SharedPreferences prefs;

    public static void init(Context context) {
        prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
    }

    public static void saveToken(String token) {
        prefs.edit().putString(KEY_TOKEN, token).apply();
    }

    public static String getToken() {
        return prefs.getString(KEY_TOKEN, null);
    }

    public static void clearToken() {
        prefs.edit().remove(KEY_TOKEN).apply();
    }
}
```

---

## 二、后端接口对接配置

### 2.1 架构总览

```
┌─────────────────────────────────────────────────────────┐
│                    Android App                           │
│  ┌────────────────────────────────────────────────────┐  │
│  │       Retrofit + OkHttp (HTTP 请求)                 │  │
│  └──────────┬─────────────────────────────────────────┘  │
└─────────────┼───────────────────────────────────────────┘
              │ http://8.156.85.160/xxx
              ▼
┌──────────────────────────────────────┐
│         Nginx（端口 80）              │
│  /api/* → Gateway                    │
│  /picuture/* → 静态文件直接返回        │
└──────────┬───────────────────────────┘
           ▼
┌──────────────────────────────────────┐
│  Gateway Server（端口 10087）         │
│  路由分发到各微服务                    │
└──┬───┬───┬───┬───┬───┬───┬───┬──────┘
   │   │   │   │   │   │   │   │
   ▼   ▼   ▼   ▼   ▼   ▼   ▼   ▼
 SSO Front Order Search Seckill Resource AI UMS
10009 10004 10005 10008  10007   9060  10010 10006
```

### 2.2 服务器连接信息

| 配置项 | 值 |
|--------|-----|
| 公网 IP | `8.156.85.160` |
| Nginx 端口 | `80`（HTTP） |
| Gateway 端口 | `10087`（内网，Nginx 反向代理） |
| Android Base URL | `http://8.156.85.160/` |
| 商城域名 | `coolshark-shop.cn`（待 ICP 备案，暂时用 IP） |
| 图片访问前缀 | `http://8.156.85.160/picuture/` |

> **注意：** 后端 Gateway 端口 10087 只在内网监听，外部请求统一走 Nginx 80 端口。Android 端 **Base URL 直接配 Nginx 地址**，不需要加端口号。

### 2.3 路由映射表

Android 端请求的路径前缀与后端的对应关系：

| 路径前缀 | 目标服务 | 端口 | 是否需要登录 | Android 端示例 |
|----------|---------|------|------------|---------------|
| `/user/sso/**` | SSO | 10009 | 登录登出公开，其余需 Token | `POST /user/sso/login` |
| `/admin/sso/**` | SSO 管理员 | 10009 | 需 Token | Android 端通常不用 |
| `/front/**` | 前台商品 | 10004 | **公开** | `GET /front/spu/list/{categoryId}` |
| `/search/**` | 搜索 | 10008 | **公开** | `GET /search/?keyword=手机` |
| `/oms/cart/**` | 购物车 | 10005 | **需 Token** | `GET /oms/cart/list` |
| `/oms/order/**` | 订单 | 10005 | **需 Token** | `POST /oms/order/add` |
| `/seckill/spu/**` | 秒杀 SPU | 10007 | **公开** | `GET /seckill/spu/list` |
| `/seckill/sku/**` | 秒杀 SKU | 10007 | **公开** | `GET /seckill/sku/list/{spuId}` |
| `/seckill/{randCode}` | 秒杀下单 | 10007 | **需 Token** | `POST /seckill/{randCode}` |
| `/ums/user/register` | 注册 | 10006 | **公开** | `POST /ums/user/register` |
| `/ums/user/checkValue` | 校验字段 | 10006 | **公开** | `POST /ums/user/checkValue` |
| `/ums/user/renew/password` | 改密 | 10006 | 需 Token | `POST /ums/user/renew/password` |
| `/ums/deliveryAddress/**` | 地址管理 | 10006 | **需 Token** | `GET /ums/deliveryAddress/list` |
| `/ums/userDetail/**` | 用户详情 | 10006 | **需 Token** | `GET /ums/userDetail/show` |
| `/upload/**` | 文件上传 | 9060 | **公开**（见注意） | `POST /upload/picture/single` |
| `/ai/**` | AI 导购 | 10010 | **公开** | `GET /ai/xxx`（需确认详情） |

> **⚠ 关于文件上传鉴权：** 当前 `/upload/**` 路由在 Gateway 层**无鉴权过滤**。任何人知道地址即可上传文件。如果未来需要限制只有登录用户才能上传，需要在 `AuthInterceptor` 中对该路径也添加 Token。

### 2.4 鉴权方式

#### 2.4.1 登录流程

```
Android                         后端 SSO
  │                                │
  │  POST /user/sso/login          │
  │  { username, password }        │
  │ ──────────────────────────────>│
  │                                │
  │  JsonResult<TokenVO> {         │
  │    state: 200,                 │
  │    data: {                     │
  │      tokenHeader: "Bearer ",   │
  │      tokenValue: "eyJhbG..."   │
  │    }                           │
  │  }                             │
  │ <──────────────────────────────│
  │                                │
  │  TokenManager.saveToken(       │
  │    tokenVO.getTokenValue())    │
  │                                │
```

#### 2.4.2 请求鉴权头

所有需要登录的接口，在 `Authorization` 头中携带：

```
Authorization: Bearer eyJhbGciOiJIUzUxMiJ9.eyJzdWIiOi...
```

Token 由 `AuthInterceptor` 自动添加（见第一章代码）。

#### 2.4.3 注销登录

```
POST /user/sso/logout
Authorization: Bearer eyJhbG...
```

后端将该 Token 加入 Redis 黑名单，后续携带此 Token 的请求将被拒绝。

### 2.5 统一响应格式

所有接口返回格式一致：

```json
{
    "state": 200,
    "message": null,
    "data": { ... }
}
```

| state | 含义 | 说明 |
|-------|------|------|
| 200 | 成功 | `message` 为 null |
| 400 | 参数错误 | `message` 描述错误原因 |
| 401 | 未认证 | Token 过期或无效，需跳转登录页 |
| 403 | 无权限 | 角色权限不足 |
| 404 | 未找到 | 资源不存在 |
| 406 | 不可接受 | 业务校验不通过 |
| 500 | 服务器错误 | 内部异常 |

**分页响应格式**（`data` 字段内嵌 `JsonPage`）：

```json
{
    "state": 200,
    "data": {
        "page": 1,
        "pageSize": 10,
        "total": 58,
        "totalPage": 6,
        "list": [ ... ]
    }
}
```

**Android 端 Java 模型**：

```java
public class JsonResult<T> {
    private int state;
    private String message;
    private T data;

    public boolean isSuccess() { return state == 200; }
}

public class JsonPage<T> {
    private int page;
    private int pageSize;
    private int total;
    private int totalPage;
    private List<T> list;
}
```

### 2.6 图片资源访问

| 类型 | 存储路径 | 访问 URL |
|------|---------|---------|
| 商品图片 | `/data/csmall-upload/picuture/2026/05/28/xxx.jpg` | `http://8.156.85.160/picuture/2026/05/28/xxx.jpg` |
| 品牌 Logo | `/data/csmall-upload/brand-logo/xxx/xxx.png` | `http://8.156.85.160/brand-logo/xxx/xxx.png` |
| 分类图标 | `/data/csmall-upload/category-icon/xxx/xxx.png` | `http://8.156.85.160/category-icon/xxx/xxx.png` |

图片由 **Nginx 直接提供静态文件**，不经过 Java 服务栈，性能较好。

> **特别说明：** 数据库中图片字段存储的是**相对路径**（如 `spu_1_1.jpg`）。Android 端拿到的商品数据中的图片 URL 已经是后端 `ImageUrlPrefixHelper` 拼接好的完整 URL（如 `http://8.156.85.160/picuture/2026/05/28/spu_1_1.jpg`），**无需 Android 端自行拼接**。如果个别字段返回的是纯文件名，说明前端 Vue 项目中有额外的拼接逻辑，Android 端需要确定最终渲染时的完整规则。

### 2.7 环境变量参考（仅后端）

以下环境变量在后端服务器 `/data/jars/csmall.env` 中定义，Android 端不需要关心，但了解有助于排查问题：

| 环境变量 | 值 | 作用 |
|---------|------|------|
| `ALIYUN_SERVER_IP` | `127.0.0.1` | 后端服务内部互调 |
| `RESOURCE_HOST` | `http://8.156.85.160/` | 图片 URL 前缀 |
| `MYSQL_*` | (敏感) | 数据库 |
| `REDIS_PASSWORD` | (敏感) | Redis |

---

## 三、功能清单

### 3.1 用户登录 / 注册

| 项 | 说明 |
|----|------|
| **后端接口** | `POST /user/sso/login` → `JsonResult<TokenVO>` |
| | `POST /ums/user/register` → `JsonResult<RegisterUserVO>` |
| | `POST /ums/user/checkValue` → `JsonResult.ok()` |
| | `POST /user/sso/logout` → `JsonResult.ok()` |
| **Auth** | 登录/注册/校验均为公开接口 |
| **页面交互** | ① 注册页：填写用户名/昵称/手机/邮箱/密码 → 点击注册 → 自动登录 |
| | ② 登录页：输入用户名+密码 → 点击登录 → 存储 Token → 跳转首页 |
| | ③ 个人中心 → 退出登录 → 清除 Token → 回到登录页 |
| **数据流转** | `UserRegistryDTO` → 后端 → 写入 `cs_mall_ums` 库 → 返回 `RegisterUserVO` |
| | `UserLoginDTO` → 后端 → SSO 校验密码 → 生成 JWT → 返回 `TokenVO` |

### 3.2 商品分类浏览

| 项 | 说明 |
|----|------|
| **后端接口** | `GET /front/category/all` → `JsonResult<FrontCategoryTreeVO>` |
| **Auth** | **公开**，无需登录 |
| **页面交互** | 首页 → 加载三级分类树（一级→二级→三级）→ 用户点击三级分类 → 跳转商品列表 |
| **数据流转** | 无参数请求 → 后端从 DB 读取分类树 → 返回嵌套的树形结构 |

### 3.3 商品列表

| 项 | 说明 |
|----|------|
| **后端接口** | `GET /front/spu/list/{categoryId}?page=1&pageSize=10` → `JsonResult<JsonPage<SpuListItemVO>>` |
| **Auth** | **公开** |
| **页面交互** | 选择分类 → 加载该分类下的 SPU 列表（分页）→ 上拉加载更多 |
| | 每个 SPU 项展示：标题、价格、主图、销量 |
| **数据流转** | `categoryId` 作为路径参数 → 后端分页查询 → 返回 `SpuListItemVO` 列表 |

### 3.4 商品搜索

| 项 | 说明 |
|----|------|
| **后端接口** | `GET /search/?keyword=手机&page=1&pageSize=10` → `JsonResult<JsonPage<SpuForElastic>>` |
| **Auth** | **公开** |
| **页面交互** | 搜索框输入关键字 → 回车或点击搜索 → 展示搜索结果列表（分页） |
| **数据流转** | `keyword` 参数 → 后端 ES 全文检索 → 返回匹配的 SPU 列表 |

### 3.5 商品详情

| 项 | 说明 |
|----|------|
| **后端接口** | `GET /front/spu/{spuId}` → `JsonResult<SpuStandardVO>` |
| | `GET /front/spu/detail/{spuId}` → `JsonResult<SpuDetailStandardVO>` |
| | `GET /front/sku/{spuId}` → `JsonResult<List<SkuStandardVO>>` |
| | `GET /front/spu/template/{id}` → `JsonResult<List<AttributeStandardVO>>` |
| **Auth** | **公开** |
| **页面交互** | 点击商品 → 加载：SPU 信息（标题/价格/主图/详情描述）+ SKU 列表（规格选择）+ 属性模板 |
| | 用户选择 SKU → 加入购物车 或 立即购买 |
| **数据流转** | `spuId` → 后端查 `cs_mall_pms` 库 → 返回 SPU/SKU/详情/属性 → 拼接展示 |

### 3.6 购物车管理

| 项 | 说明 |
|----|------|
| **后端接口** | `GET /oms/cart/list?page=1&pageSize=10` → `JsonResult<JsonPage<CartStandardVO>>` |
| | `POST /oms/cart/add` body: `CartAddDTO` → `JsonResult.ok()` |
| | `POST /oms/cart/delete` param: `ids` → `JsonResult.ok()` |
| | `POST /oms/cart/delete/all` → `JsonResult.ok()` |
| | `POST /oms/cart/update/quantity` body: `CartUpdateDTO` → `JsonResult.ok()` |
| **Auth** | **需 Token**（`ROLE_user`） |
| **页面交互** | 购物车列表 → 展示商品 + 数量 + 小计 |
| | 左滑删除 / 点击删除按钮 → 删除商品 |
| | 点击 + / - → 修改数量 |
| | 点击结算 → 跳转订单确认页 |
| **数据流转** | 所有操作通过 Token 识别用户 → CRUD `cs_mall_oms` 购物车表 |

### 3.7 下单支付

| 项 | 说明 |
|----|------|
| **后端接口** | `POST /oms/order/add` body: `OrderAddDTO` → `JsonResult<OrderAddVO>` |
| | `POST /oms/order/pay` body: `PayOrderDTO` → `JsonResult<PayOrderVO>` |
| | `GET /oms/order/detail?id=xxx` → `JsonResult<OrderDetailVO>` |
| | `GET /oms/order/list?startTime=&endTime=&page=&pageSize=` → `JsonResult<JsonPage<OrderListVO>>` |
| | `POST /oms/order/update/state` body: `OrderStateUpdateDTO` → `JsonResult.ok()` |
| **Auth** | **需 Token**（`ROLE_user`） |
| **页面交互** | ① 订单确认页：选择收货地址 + 确认商品清单 → 提交订单 |
| | ② 支付页：确认金额 → 点击支付 → 模拟支付成功 |
| | ③ 订单列表：查看历史订单 → 按时间筛选 |
| | ④ 订单详情：查看订单状态、物流信息等 |
| **数据流转** | `OrderAddDTO`（含地址+商品+金额）→ 后端创建订单 → 锁定库存 → 返回 `OrderAddVO` |
| | 支付 → 后端更新订单状态 → 扣减真实库存 |

### 3.8 地址管理

| 项 | 说明 |
|----|------|
| **后端接口** | `GET /ums/deliveryAddress/list?page=&pageSize=` → `JsonResult<JsonPage<...>>` |
| | `POST /ums/deliveryAddress/add` body: `DeliveryAddressAddDTO` → `JsonResult.ok()` |
| | `POST /ums/deliveryAddress/edit` body: `DeliveryAddressEditDTO` → `JsonResult.ok()` |
| | `POST /ums/deliveryAddress/delete?id=xxx` → `JsonResult.ok()` |
| **Auth** | **需 Token**（`ROLE_user`） |
| **页面交互** | 地址列表页 → 展示所有收货地址 |
| | 新增/编辑地址：省市区级联选择 + 详细地址 + 手机号 |
| | 左滑删除地址 |
| **数据流转** | Token 识别用户 → CRUD `cs_mall_ums` 地址表 |

### 3.9 秒杀

| 项 | 说明 |
|----|------|
| **后端接口** | `GET /seckill/spu/list?page=&pageSize=` → `JsonResult<JsonPage<SeckillSpuVO>>` |
| | `GET /seckill/spu/{spuId}` → `JsonResult<SeckillSpuVO>` |
| | `GET /seckill/spu/{spuId}/detail` → `JsonResult<SeckillSpuDetailSimpleVO>` |
| | `GET /seckill/sku/list/{spuId}` → `JsonResult<List<SeckillSkuVO>>` |
| | `POST /seckill/{randCode}` body: `SeckillOrderAddDTO` → `JsonResult<SeckillCommitVO>` |
| **Auth** | 列表/详情 **公开**，秒杀下单 **需 Token** |
| **页面交互** | 秒杀列表 → 展示秒杀商品 + 倒计时 |
| | 点击进入详情 → 查看秒杀价 + 选择 SKU |
| | 秒杀开始时 → 点击"立即秒杀" → 输入随机码（由后端验证） → 提交订单 |
| **数据流转** | Redis 预扣库存 → RabbitMQ 异步落库 → 防止超卖 |
| | 随机码机制防止恶意刷单 |

### 3.10 CameraX 拍照上传

| 项 | 说明 |
|----|------|
| **后端接口** | `POST /upload/picture/single` multipart: `file` → `JsonResult<ImageFileVO>` |
| | 返回：`{ url, contentType, size, width, height }` |
| **Auth** | **公开**（当前无鉴权） |
| **页面交互** | ① 进入拍照页面 → 打开 CameraX 预览 |
| | ② 点击拍照按钮 → 自动对焦 → 拍摄 JPEG 照片 |
| | ③ 拍照完成 → 预览缩略图 → 点击"使用"或"重拍" |
| | ④ 点击上传 → 显示上传进度 → 完成后展示图片 URL |
| **数据流转** | CameraX `ImageCapture` → 保存到临时文件 → OkHttp `MultipartBody.Part` → |
| | POST 到 `/upload/picture/single` → Nginx → Gateway → `mall-resource` → |
| | 保存到 `/data/csmall-upload/picuture/{date}/{filename}.jpg` → |
| | 返回 `ImageFileVO`（含 URL: `http://8.156.85.160/picuture/...`） |

**CameraX 核心代码示意**（仅作参考，不修改项目）：

```java
// 拍照
imageCapture.takePicture(
    new ImageCapture.OnImageCapturedCallback() {
        @Override
        public void onCaptureSuccess(@NonNull ImageProxy image) {
            // 将 ImageProxy 转为 File 或 byte[]
            ByteBuffer buffer = image.getPlanes()[0].getBuffer();
            byte[] bytes = new byte[buffer.remaining()];
            buffer.get(bytes);
            // 保存到临时文件
            File photoFile = saveToTempFile(bytes);
            // 准备上传
            uploadFile(photoFile);
        }
    }
);

// 上传
private void uploadFile(File file) {
    UploadApi api = RetrofitClient.getInstance().create(UploadApi.class);
    RequestBody requestBody = RequestBody.create(MediaType.parse("image/jpeg"), file);
    MultipartBody.Part part = MultipartBody.Part.createFormData("file", file.getName(), requestBody);

    Call<JsonResult<ImageFileVO>> call = api.uploadPicture(part);
    call.enqueue(new Callback<>() {
        @Override
        public void onResponse(...) {
            if (result.isSuccess()) {
                String imageUrl = result.getData().getUrl();
                // 使用 imageUrl
            }
        }
    });
}
```

**`UploadApi.java`：**

```java
public interface UploadApi {
    @Multipart
    @POST("upload/picture/single")
    Call<JsonResult<ImageFileVO>> uploadPicture(
        @Part MultipartBody.Part file
    );
}
```

### 3.11 AI 智能导购

| 项 | 说明 |
|----|------|
| **后端接口** | `GET /ai/...`（需查看 `mall-ai` 模块具体端点） |
| **Auth** | 以实际接口文档为准 |
| **页面交互** | 对话界面 → 输入问题 → AI 回复（RAG 问答） |
| | 商品对比 → 输入商品名称 → AI 对比分析 |
| **数据流转** | 用户输入 → DeepSeek V4 API + ES 检索 → 生成回答 |
| | 多轮对话通过 Redis 存储会话上下文 |

### 3.12 功能清单速查表

| # | 功能 | 公开/需登录 | 涉及后端服务 | 核心接口数 |
|---|------|------------|------------|-----------|
| 1 | 用户注册 | 公开 | UMS | 2 |
| 2 | 用户登录/登出 | 公开 | SSO | 2 |
| 3 | 商品分类 | 公开 | Front | 1 |
| 4 | 商品列表 | 公开 | Front | 1 |
| 5 | 商品搜索 | 公开 | Search | 2 |
| 6 | 商品详情 | 公开 | Front | 4 |
| 7 | 购物车 | 需登录 | Order | 5 |
| 8 | 下单支付 | 需登录 | Order | 5 |
| 9 | 地址管理 | 需登录 | UMS | 4 |
| 10 | 秒杀浏览 | 公开 | Seckill | 4 |
| 11 | 秒杀下单 | 需登录 | Seckill | 1 |
| 12 | 拍照上传 | 公开 | Resource | 1 |
| 13 | 改密/个人资料 | 需登录 | UMS | 3 |
| 14 | AI 导购 | 公开 | AI | TBD |

---

## 附录

### A. 本地开发联调地址速查

| 场景 | Base URL | 说明 |
|------|----------|------|
| Android 模拟器 → 宿主机本地后端 | `http://10.0.2.2:10087/` | 模拟器专用地址 |
| 真机 → 本机电脑后端 | `http://192.168.x.x:10087/` | 电脑局域网 IP |
| 真机 → 生产环境 | `http://8.156.85.160/` | 已部署的云服务器 |

### B. OkHttp 超时建议

| 操作 | 连接超时 | 读取超时 | 写入超时 |
|------|---------|---------|---------|
| 普通 API 请求 | 15s | 15s | 15s |
| 文件上传 | 15s | 30s | 60s |
| AI 对话（流式） | 15s | 60s | 15s |

### C. 错误处理建议

```java
// 在 ResponseHandler 中统一处理
if (!result.isSuccess()) {
    switch (result.getState()) {
        case 401:
            // Token 过期 → 清除 Token → 跳转登录页
            TokenManager.clearToken();
            navigateToLogin();
            break;
        case 400:
        case 406:
            // 参数错误 / 业务校验失败 → Toast 提示 message
            showToast(result.getMessage());
            break;
        case 500:
            // 服务器异常 → 通用提示
            showToast("服务器繁忙，请稍后重试");
            break;
    }
}
```
