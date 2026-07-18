# Controller @RequestBody 注解检查报告

## 检查时间
2026-04-08

## 检查目的
确保所有接收 JSON 请求体的 POST 方法都有 @RequestBody 注解

---

## 发现的问题

### 1. mall-ums 模块

**文件**: `mall-ums/mall-ums-webapi/src/main/java/com/cooxiao/mall/ums/controller/UserController.java`

| 方法 | 行号 | 问题 |
|------|------|------|
| `doRegister` | 50 | 缺少 @RequestBody |
| `renewPassword` | 76 | 缺少 @RequestBody |

---

### 2. mall-product 模块

**文件**: `mall-product/mall-product-webapi/src/main/java/com/cooxiao/mall/product/controller/`

| 文件 | 方法 | 行号 | 问题 |
|------|------|------|------|
| AlbumController.java | `addNew` | 42 | 缺少 @RequestBody |
| AlbumController.java | `updateById` | 75 | 缺少 @RequestBody |
| BrandController.java | `addNew` | 42 | 缺少 @RequestBody |
| BrandController.java | `updateById` | 72 | 缺少 @RequestBody |
| PictureController.java | `addNew` | 48 | 缺少 @RequestBody |
| SpuController.java | `addNew` | 44 | 缺少 @RequestBody |
| SpuController.java | `updateById` | 59 | 缺少 @RequestBody |
| SkuController.java | `addNew` | 42 | 缺少 @RequestBody |
| SkuController.java | `updateById` | 57 | 缺少 @RequestBody |
| CategoryController.java | `addNew` | 44 | 缺少 @RequestBody |
| CategoryController.java | `updateById` | 134, 149 | 缺少 @RequestBody |
| AttributeController.java | `addNew` | 43 | 缺少 @RequestBody |
| AttributeController.java | `updateById` | 73 | 缺少 @RequestBody |
| AttributeTemplateController.java | `addNew` | 45 | 缺少 @RequestBody |
| AttributeTemplateController.java | `updateById` | 75 | 缺少 @RequestBody |

---

### 3. mall-order 模块

**文件**: `mall-order/mall-order-webapi/src/main/java/com/cooxiao/mall/order/controller/`

| 文件 | 方法 | 行号 | 问题 |
|------|------|------|------|
| OmsOrderController.java | `addOrder` | 32 | 缺少 @RequestBody |
| OmsOrderController.java | `updateOrderState` | 48 | 缺少 @RequestBody |
| OmsCartController.java | `addCart` | 36 | 缺少 @RequestBody |
| OmsCartController.java | `updateQuantity` | 72 | 缺少 @RequestBody |

---

### 4. mall-seckill 模块

**文件**: `mall-seckill/mall-seckill-webapi/src/main/java/com/cooxiao/mall/seckill/controller/SeckillController.java`

| 方法 | 行号 | 问题 |
|------|------|------|
| `commitSeckill` | 38 | 缺少 @RequestBody |

---

## 问题严重性说明

### 影响范围
如果这些接口是通过 JSON 请求体传参的，缺少 `@RequestBody` 注解会导致：
- 参数无法正确绑定
- 接收到的数据为 null
- 验证注解（如 @Valid）失效

### 修复方式
为 DTO/VO 参数添加 `@RequestBody` 注解：

```java
// 修复前
public JsonResult doRegister(UserRegistryDTO userRegistyDTO)

// 修复后
public JsonResult doRegister(@RequestBody @Valid UserRegistryDTO userRegistyDTO)
```

---

## 建议

1. **优先级：高** - 用户注册、登录、订单相关接口必须修复
2. **优先级：中** - 商品管理相关接口需要修复
3. **优先级：低** - 秒杀相关接口需要修复

建议根据业务重要性逐步修复这些问题。
