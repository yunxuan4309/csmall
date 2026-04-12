# Git 提交日志(登录功能bug修复1)

## 提交类型：修复 + 改进

---

## 提交信息

```
fix(sso): 修复管理员登录功能的多个技术问题

1. 添加 @RequestBody 注解解决 JSON 参数绑定失败
   - AdminSSOController.doLogin()
   - UserSSOController.doLogin()

2. 修复 CORS 跨域配置
   - 使用 setAllowedOriginPatterns 替代已废弃的 setAllowedOrigins
   - 添加 allowCredentials(true) 配置

3. 添加 JAXB API 依赖 (javax.xml.bind)
   - 解决 Java 9+ 环境下 JWT Token 生成失败
   - pom.xml 和 mall-common/pom.xml

4. 修复登录日志表字段映射错误
   - 移除不存在的 nickname 和 gmtModified 字段
   - mall-sso/mapper/admin/AdminLoginLogMapper.xml

5. 添加 Jackson 配置确保 JSON 正确序列化
   - 新增 JacksonConfig.java 配置类
   - 配置 MappingJackson2HttpMessageConverter

6. 优化 Spring Boot 自动配置排除
   - MallPassportWebApiApplication.java

其他修复：
- mall-ams: 修复 AdminMapper.xml, LoginLogMapper.xml, PermissionMapper.xml 字段名
- mall-ums: 修复 UserMapper.xml 字段名
- 统一 application.yml 中 jackson 配置
```

---

## Git 命令

```bash
# 添加所有修改的文件
git add .

# 提交
git commit -m "fix(sso): 修复管理员登录功能的多个技术问题

1. 添加 @RequestBody 注解解决 JSON 参数绑定失败
2. 修复 CORS 跨域配置使用 setAllowedOriginPatterns
3. 添加 JAXB API 依赖解决 Java 9+ JWT Token 生成失败
4. 修复登录日志表字段映射错误
5. 添加 JacksonConfig 确保 JSON 正确序列化
6. 优化 Spring Boot 自动配置排除

其他修复：
- mall-ams: 修复字段名 is_enable -> enable, value -> code
- mall-ums: 修复字段名 is_enable -> enable
- 统一 jackson 配置"
```

---

## 修改文件清单

### mall-sso 模块
- `pom.xml` - 添加 JAXB API 依赖管理
- `MallPassportWebApiApplication.java` - 优化自动配置排除
- `config/JacksonConfig.java` - 新增 Jackson 配置类
- `controller/AdminSSOController.java` - 添加 @RequestBody
- `controller/UserSSOController.java` - 添加 @RequestBody
- `security/config/SSOWebSecurityConfig.java` - 修复 CORS 配置
- `security/service/admin/AdminSSOUserDetailsService.java` - 添加调试日志
- `security/service/admin/impl/AdminSSOServiceImpl.java` - 添加调试日志
- `resources/mapper/admin/AdminLoginLogMapper.xml` - 修复字段映射
- `resources/mapper/admin/AdminMapper.xml` - 修复字段名
- `resources/mapper/admin/PermissionMapper.xml` - 修复字段名
- `resources/mapper/user/UserMapper.xml` - 修复字段名
- `resources/application*.yml` - 统一 jackson 配置

### mall-ams 模块
- `src/main/resources/mapper/AdminMapper.xml` - 修复字段名
- `src/main/resources/mapper/LoginLogMapper.xml` - 修复字段名
- `src/main/resources/mapper/PermissionMapper.xml` - 修复字段名
- `security/config/ResourceWebSecurityConfiguration.java` - 修复 Spring Security 路径模式

### mall-ums 模块
- `src/main/resources/mapper/UserMapper.xml` - 修复字段名
- `security/config/ResourceWebSecurityConfiguration.java` - 修复 Spring Security 路径模式

### mall-common 模块
- `pom.xml` - 添加 JAXB API 依赖

### 其他模块
- `mall-front` - 修复 Spring Security 路径模式
- `mall-order` - 修复 Spring Security 路径模式
- `mall-product` - 修复 Spring Security 路径模式
- `mall-search` - 修复 Spring Security 路径模式
- `mall-seckill` - 修复 Spring Security 路径模式

### 根目录
- `pom.xml` - 添加 JAXB API 版本管理

---

# Git 提交日志(订单购物车秒杀服务修复)

## 提交类型：修复 + 新增功能

---

## 提交信息

```
fix(order,seckill): 修复订单详情查询、购物车字段映射、秒杀数据库结构问题

1. 订单模块修复
   - 实现 getOrderDetail() 方法，添加订单详情查询功能
   - 添加 OmsOrderMapper.selectOrderById() 方法
   - 添加 OmsOrderItemMapper.selectOrderItemsByOrderId() 方法
   - 修复 OmsOrderItemMapper.xml 字段映射 (picture_url, sku_properties)
   - 在 OmsOrderController 添加 /detail 接口

2. 购物车模块修复
   - 修复 OmsCartMapper.xml 字段映射 (picture_url)
   - 添加 bar_code、data、is_checked 字段映射
   - OmsCart.java 添加 isChecked 属性
   - CartStandardVO.java 添加 isChecked 属性

3. 秒杀模块修复
   - 修复 05-seckill.sql 表结构匹配代码实体类
   - seckill_spu 表: 添加 spu_id, list_price 字段
   - seckill_sku 表: 修改为 sku_id, spu_id, seckill_stock 等字段

4. 网关跨域配置
   - 新增 CorsConfig.java 配置类
   - 解决前端 CORS 跨域请求问题

5. 数据库脚本更新
   - 03-oms-order.sql: 添加 bar_code 字段
   - 05-seckill.sql: 完全重构表结构
```

---

## Git 命令

```bash
# 添加所有修改的文件
git add .

# 提交
git commit -m "fix(order,seckill): 修复订单详情查询、购物车字段映射、秒杀数据库结构问题

1. 订单模块: 实现getOrderDetail方法,添加详情查询接口
2. 购物车模块: 修复字段映射,添加isChecked属性
3. 秒杀模块: 修复数据库表结构匹配代码
4. 网关: 添加CORS跨域配置
5. 数据库: 更新oms和seckill表结构"
```

---

## 修改文件清单

### mall-gateway-server 模块
- `config/CorsConfig.java` - 新增 CORS 跨域配置类

### mall-order 模块
- `controller/OmsOrderController.java` - 添加 getOrderDetail 接口
- `mapper/OmsOrderMapper.java` - 添加 selectOrderById 方法
- `mapper/OmsOrderItemMapper.java` - 添加 selectOrderItemsByOrderId 方法
- `service/impl/OmsOrderServiceImpl.java` - 实现 getOrderDetail 方法
- `resources/mapper/OmsOrderMapper.xml` - 添加查询SQL
- `resources/mapper/OmsOrderItemMapper.xml` - 修复字段映射,添加查询SQL
- `resources/mapper/OmsCartMapper.xml` - 修复字段映射

### mall-pojo 模块
- `order/model/OmsCart.java` - 添加 isChecked 属性
- `order/vo/CartStandardVO.java` - 添加 isChecked 属性

### database 数据库脚本
- `03-oms-order.sql` - 添加 bar_code 字段
- `05-seckill.sql` - 重构 seckill_spu 和 seckill_sku 表结构

### 文档
- `秒杀购物车订单服务修改1.md` - 新增修改记录文档
