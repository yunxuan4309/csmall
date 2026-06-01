# CoolShark Android 开发指南 — 登录与拍照上传

> 本文档聚焦 Android 端的两个核心功能：**用户登录** 和 **CameraX 拍照上传**，提供接口对接说明、数据流走向、关键代码示例和注意事项。

---

## 目录

- [一、登录功能](#一登录功能)
- [二、拍照上传功能](#二拍照上传功能)
- [三、通用最佳实践](#三通用最佳实践)
- [四、常见问题](#四常见问题)

---

## 一、登录功能

### 1.1 接口说明

| 项 | 值 |
|----|-----|
| **URL** | `POST /user/sso/login` |
| **完整地址** | `http://8.156.85.160/user/sso/login` |
| **Auth** | **公开**（无需 Token） |
| **Content-Type** | `application/json` |

### 1.2 请求与响应

**请求体：**

```json
{
    "username": "testuser",
    "password": "123456"
}
```

**成功响应：**

```json
{
    "state": 200,
    "message": null,
    "data": {
        "tokenHeader": "Bearer ",
        "tokenValue": "eyJhbGciOiJIUzUxMiJ9.eyJzdWIiOiJ7XCJpZFwiOjE..."
    }
}
```

**失败响应：**

```json
{
    "state": 400,
    "message": "用户名或密码错误",
    "data": null
}
```

### 1.3 Android 端对接要点

#### Retrofit 接口定义

```java
public interface AuthApi {
    @POST("user/sso/login")
    Call<JsonResult<TokenVO>> login(@Body UserLoginDTO dto);

    @POST("user/sso/logout")
    Call<JsonResult<Void>> logout();
}
```

#### DTO 定义

```java
public class UserLoginDTO {
    private String username;
    private String password;
    // getter / setter
}

public class TokenVO {
    private String tokenHeader;  // "Bearer "
    private String tokenValue;   // JWT 字符串
}
```

#### 登录成功后

1. 取出 `tokenValue`（JWT 字符串）
2. 用 `TokenManager.saveToken(tokenValue)` 存入 `SharedPreferences`
3. 后续所有需认证的接口，由 `AuthInterceptor` 自动在 Header 中添加：

```
Authorization: Bearer eyJhbGciOiJIUzUxMiJ9...
```

#### 注册接口（首次使用）

| 项 | 值 |
|----|-----|
| **URL** | `POST /ums/user/register` |
| **完整地址** | `http://8.156.85.160/ums/user/register` |
| **Auth** | **公开** |

**请求体：**

```json
{
    "username": "newuser",
    "nickname": "新用户",
    "phone": "13800138000",
    "email": "user@example.com",
    "password": "123456",
    "ackPassword": "123456"
}
```

注册成功后返回 `RegisterUserVO`（含 id / username / nickname），然后调用登录接口获取 Token。

#### 退出登录

```
POST /user/sso/logout
Authorization: Bearer eyJhbG...
```

后端将该 Token 加入 Redis 黑名单，此后该 Token 失效。Android 端同时清理本地存储的 Token。

### 1.4 Token 管理建议

```java
// 保存 Token
TokenManager.saveToken(tokenValue);

// 读取 Token（Interceptor 中使用）
String token = TokenManager.getToken();

// 登出时清除
TokenManager.clearToken();
```

Token 有效期由后端控制（`jwt.expiration: 604800` = 7 天）。过期后调用需认证接口会返回 `401`，此时应跳转回登录页。

---

## 二、拍照上传功能

### 2.1 完整流程

```
用户打开相机页面
    │
    ▼
CameraX 启动预览（显示取景框）
    │
    ▼
用户点击拍照按钮
    │
    ▼
ImageCapture 拍摄 JPEG 照片
    │
    ▼
保存到应用临时目录（FileProvider 生成 Uri）
    │
    ▼
用户确认照片（预览 → 重拍 / 使用）
    │
    ▼
构建 MultipartBody.Part 上传
    │
    ▼
POST /upload/picture/single（Header 带 Token）
    │
    ▼
返回 ImageFileVO（url / width / height / size）
    │
    ▼
显示图片（Glide 加载 url）+ 可选将 url 存入本地列表
```

### 2.2 上传接口说明

| 项 | 值 |
|----|-----|
| **URL** | `POST /upload/picture/single` |
| **完整地址** | `http://8.156.85.160/upload/picture/single` |
| **Auth** | **可选**（携带 Token 则自动记录到用户上传历史） |
| **Content-Type** | `multipart/form-data` |
| **参数名** | `file`（`MultipartFile`） |
| **文件类型** | 仅允许 `image/jpeg` 和 `image/png` |
| **文件上限** | 10MB |

**成功响应：**

```json
{
    "state": 200,
    "data": {
        "url": "http://8.156.85.160/picuture/2026/06/01/20260601213000123456.jpg",
        "contentType": "image/jpeg",
        "size": 2048576,
        "width": 1920,
        "height": 1080
    }
}
```

### 2.3 CameraX Android 端集成

#### 权限声明（AndroidManifest.xml）

```xml
<uses-permission android:name="android.permission.CAMERA" />
```

Android 6.0+ 还需要运行时申请：

```java
if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
        != PackageManager.PERMISSION_GRANTED) {
    ActivityCompat.requestPermissions(this,
            new String[]{Manifest.permission.CAMERA}, CAMERA_REQUEST_CODE);
}
```

#### 依赖（build.gradle.kts）

```kotlin
implementation("androidx.camera:camera-core:1.3.1")
implementation("androidx.camera:camera-camera2:1.3.1")
implementation("androidx.camera:camera-lifecycle:1.3.1")
implementation("androidx.camera:camera-view:1.3.1")
```

#### 核心流程代码示意

**① 初始化 CameraX 预览：**

```java
// 在布局中使用 PreviewView
PreviewView previewView = findViewById(R.id.previewView);

// 获取 LifecycleOwner
LifecycleOwner lifecycleOwner = this;

// 创建 Preview 用例
Preview preview = new Preview.Builder().build();
preview.setSurfaceProvider(previewView.getSurfaceProvider());

// 创建 ImageCapture 用例
ImageCapture imageCapture = new ImageCapture.Builder()
        .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
        .setTargetRotation(Surface.ROTATION_0)
        .build();

// 绑定到 Lifecycle
Camera camera = new ProcessCameraProvider.getInstance(this)
        .get()
        .bindToLifecycle(lifecycleOwner, CameraSelector.DEFAULT_BACK_CAMERA,
                preview, imageCapture);
```

**② 拍照并保存到临时文件：**

```java
// 创建临时文件
File photoFile = File.createTempFile("photo_", ".jpg", getCacheDir());
ImageCapture.OutputFileOptions options =
        new ImageCapture.OutputFileOptions.Builder(photoFile).build();

imageCapture.takePicture(options, ContextCompat.getMainExecutor(this),
        new ImageCapture.OnImageSavedCallback() {
            @Override
            public void onImageSaved(ImageCapture.OutputFileResults output) {
                // 拍照成功，photoFile 即为 JPEG 文件
                uploadPhoto(photoFile);
            }

            @Override
            public void onError(ImageCaptureException exception) {
                // 拍照失败处理
            }
        });
```

**③ 上传到服务器：**

```java
public interface UploadApi {
    @Multipart
    @POST("upload/picture/single")
    Call<JsonResult<ImageFileVO>> uploadPicture(@Part MultipartBody.Part file);
}

private void uploadPhoto(File photoFile) {
    UploadApi api = RetrofitClient.getInstance().create(UploadApi.class);

    RequestBody requestBody = RequestBody.create(MediaType.parse("image/jpeg"), photoFile);
    MultipartBody.Part part = MultipartBody.Part.createFormData("file",
            photoFile.getName(), requestBody);

    Call<JsonResult<ImageFileVO>> call = api.uploadPicture(part);
    call.enqueue(new Callback<JsonResult<ImageFileVO>>() {
        @Override
        public void onResponse(Call<JsonResult<ImageFileVO>> call,
                               Response<JsonResult<ImageFileVO>> response) {
            if (response.isSuccessful() && response.body().isSuccess()) {
                ImageFileVO vo = response.body().getData();
                // vo.getUrl() 即为图片访问地址
                // 用 Glide 加载：Glide.with(context).load(vo.getUrl()).into(imageView)
                // 可缓存到本地列表供后续浏览
            }
        }

        @Override
        public void onFailure(Call<JsonResult<ImageFileVO>> call, Throwable t) {
            // 网络异常处理
        }
    });
}
```

### 2.4 查看历史上传记录

| 项 | 值 |
|----|-----|
| **URL** | `GET /upload/user/list?page=1&pageSize=10` |
| **Auth** | **必须携带 Token** |
| **响应** | `JsonResult<JsonPage<UploadRecordVO>>` |

**响应示例：**

```json
{
    "state": 200,
    "data": {
        "page": 1,
        "pageSize": 10,
        "totalPage": 1,
        "total": 2,
        "list": [
            {
                "id": 12345,
                "url": "http://8.156.85.160/picuture/2026/06/01/xxxx.jpg",
                "contentType": "image/jpeg",
                "fileSize": 2048576,
                "width": 1920,
                "height": 1080,
                "originalFilename": "photo_123456.jpg",
                "gmtCreate": "2026-06-01 21:30:00"
            }
        ]
    }
}
```

Android 端可用 Glide 加载 `url` 展示缩略图列表，点击查看大图。

### 2.5 上传图片的预览

| 场景 | 加载方式 |
|------|---------|
| 刚拍完照、还没上传 | 显示 `photoFile.toUri()`（本地文件） |
| 上传成功后立即展示 | 用返回的 `ImageFileVO.url` 直接加载 |
| 历史记录中展示 | 从 `GET /upload/user/list` 拿到 url 列表，分批加载 |

**Glide 加载示例：**

```java
// 加载网络图片（上传返回的 URL）
Glide.with(context)
        .load(imageFileVO.getUrl())
        .placeholder(R.drawable.placeholder)
        .error(R.drawable.error)
        .into(imageView);

// 加载本地文件（拍照后还没上传）
Glide.with(context)
        .load(photoFile)
        .into(imageView);
```

### 2.6 拍照上传页面交互建议

```
┌─────────────────────────────┐
│         相机预览区           │
│    (CameraX PreviewView)    │
│                             │
│                             │
│                             │
│                             │
│      [ 拍照按钮 ]           │
├─────────────────────────────┤
│  拍完照后切换到确认页：       │
│  ┌────────────────────┐     │
│  │  缩略图预览         │     │
│  │  [重拍]  [使用]     │     │
│  └────────────────────┘     │
├─────────────────────────────┤
│  上传中：进度条 / Loading   │
│  上传完成：显示图片 + URL   │
│  [查看历史记录] 按钮         │
└─────────────────────────────┘
```

**流程状态机建议：**

```
IDLE → CAMERA_READY → PHOTO_TAKEN → CONFIRMING
  → UPLOADING → UPLOAD_SUCCESS / UPLOAD_FAIL → IDLE
```

---

## 三、通用最佳实践

### 3.1 Retrofit 超时设置

| 操作 | 连接超时 | 读取超时 | 写入超时 |
|------|---------|---------|---------|
| 登录/注册 | 15s | 15s | 15s |
| 拍照上传 | 15s | 30s | 60s |
| 历史记录查询 | 15s | 15s | 15s |

### 3.2 错误处理

```java
// 在 ResponseHandler 中统一处理
if (!result.isSuccess()) {
    switch (result.getState()) {
        case 401:
            // Token 过期或无效 → 清除 Token → 跳转登录页
            TokenManager.clearToken();
            startActivity(new Intent(this, LoginActivity.class));
            finish();
            break;
        case 400:
        case 406:
            // 参数校验失败 → 展示错误信息
            showToast(result.getMessage());
            break;
        case 500:
            // 服务器异常
            showToast("服务器繁忙，请稍后重试");
            break;
    }
}
```

### 3.3 拍照上传常见问题

| 问题 | 原因 | 解决 |
|------|------|------|
| 拍照返回空白 | 权限未申请 | 检查 CAMERA 权限运行时请求 |
| 上传失败 400 | 文件类型不是 JPEG/PNG | CameraX 输出格式确保为 JPEG |
| 上传失败 413 | 文件超过 10MB | 压缩图片后再上传 |
| 上传超时 | 文件过大或网络差 | 增加 OkHttp 写入超时为 60s |
| Token 失效后上传不记录用户 | Token过期后端无法识别用户 | 先跳转登录页重新登录 |

### 3.4 图片压缩建议（超过 10MB 时）

```java
// 简单压缩到指定尺寸
private File compressImage(File file, int maxWidth, int maxHeight) {
    Bitmap bitmap = BitmapFactory.decodeFile(file.getPath());
    Bitmap resized = Bitmap.createScaledBitmap(bitmap, maxWidth, maxHeight, true);
    File output = new File(getCacheDir(), "compressed_" + file.getName());
    try (FileOutputStream out = new FileOutputStream(output)) {
        resized.compress(Bitmap.CompressFormat.JPEG, 85, out);
    }
    return output;
}
```

---

## 四、常见问题

### Q1: Android 模拟器如何连接本地后端？

使用 `10.0.2.2` 替代 `localhost`：

```java
// 模拟器 → 宿主机 Gateway
public static final String BASE_URL = "http://10.0.2.2:10087/";
```

### Q2: 真机调试如何连接本地后端？

电脑开启移动热点，手机连接后使用电脑局域网 IP：

```java
public static final String BASE_URL = "http://192.168.x.x:10087/";
```

### Q3: 上传的文件存在哪里？

服务器存储路径：`/data/csmall-upload/picuture/{yyyy/MM/dd}/{filename}.jpg`，通过 Nginx 直接提供静态访问。

### Q4: 上传时是否需要 Token？

不需要也能上传，但不记录用户身份，后续无法通过 `GET /upload/user/list` 查到。如果要实现"登录 → 上传 → 下次还能看到"，必须在上传时携带 Token。

### Q5: Token 过期了怎么办？

调用需认证的接口返回 `state: 401`。此时清除本地 Token 并跳转登录页，让用户重新登录获取新 Token。

---

> **参考文档**
> - `docs/android-dev-guide.md` — 完整的 Android 项目搭建与全部后端接口对接文档
> - `PROJECT_CONTEXT.md` — 后端项目上下文与服务部署信息
