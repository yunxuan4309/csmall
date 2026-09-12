# 基础知识速记-服务通信与 RPC

> **所属**：基础知识速记分册。完整 22 节总索引见 [[基础知识速记]]。
> **节号保留原编号**（本册含节 01/02/03/04/17；外部引用'基础知识速记 N'= 本册 N 节）
> **格式**：每概念 = 一句话定义 → 解决什么 → 项目例子 → 面试怎么讲。

---

## 01-Dubbo

### 一句话定义
Dubbo 是高性能 Java RPC 框架，
让服务之间**像调用本地方法一样调用远程服务**。

### 解决什么问题
微服务拆开后，服务 A（mall-front）想调用服务 B（
mall-product）里的方法，但它们在**不同 JVM、
不同进程**，无法直接 `new` 对象调用。

### 项目里的例子
```java
// mall-front：注意是 @DubboReference 注入的"远程代理"，没有 new
@DubboReference
private IForFrontSpuService dubboSpuService;

public JsonPage<SpuListItemVO> listSpuByCategoryId(...) {
    // 看起来调本地方法，实际 Dubbo 帮你：序列化参数 → Netty长连接发送
    // → 到 mall-product 的 JVM → 反序列化 → 真正执行 → 结果传回
    return dubboSpuService.listSpuByCategoryId(categoryId, page, pageSize);
    }
```

### Dubbo 背后帮你做的事
1. **服务发现**：提供者启动时把 IP+端口注册到 Nacos；
   消费者调用前去 Nacos 查"提供者在哪"
2. **网络通信**：Netty 长连接（不是每次请求都握手）+ 
   二进制序列化（比 JSON 小且快）
3. **负载均衡**：多实例时自动选一台（默认加权随机）
4. **容错**：调用失败可自动换实例重试（failover）

### 面试怎么讲
> "Dubbo = 微服务之间的快递。你写好单号（接口），它负责把包裹（
> 参数）送到对方手里，再把回执（返回值）拿回来。"

### 常被追问
- **和 Feign 的区别**：Feign 走 HTTP 短连接 + 
  JSON；Dubbo 走长连接 + 二进制（Hessian2），
  性能更优（详见 01-架构设计决策.md Q2）
- **注册中心**：用 Nacos，提供服务发现
- **性能好在哪里**：长连接省去每次 HTTP 握手开销；
  二进制序列化比 JSON 小且快

### 补充：什么是"自定义协议"？（dubbo:// 协议）
> "自定义"指 **Dubbo 框架自己定义的一套二进制协议**，
> 不是让你自己配。它不用 HTTP，而是设计了自己的报文格式。

**与 HTTP 对比**：
| | HTTP | Dubbo 自定义协议 |
|---|------|-----------------|
| 报文形式 | 文本（可读，冗余多） | 二进制（机器直接读） |
| 头部 | 可变长、一大堆字段 | **固定 16 字节** |
| 解析 | 字符串解析（慢） | 按字节位置定位（快） |

**报文结构**（16 字节头 + 内容体）：
```
0    1     2    3       11        12     15    16
magic(0xdabb) flags status 请求ID(8B) body长度(4B) body(Hessian2)
└────────────── 固定 16 字节头 ─────────────┘
```
- **magic**：固定魔数 `0xdabb`，快速识别"这是 
  Dubbo 的包"
- **请求 ID**（8 字节）：
  配对请求和响应——这是长连接能复用的关键，
  一条连接上多个请求并发，靠 
  ID 认领响应
- **body 长度**（4 字节 int）：
  即"报文长度"由协议自己定义
- **body**：Hessian2 二进制序列化的参数/结果

**"自定义"的三个层次（防追问）**：
1. **框架定死**：16 字节头格式 Dubbo 内部写死，开箱即用
2. **可配参数**：`payload` 最大报文、序列化方式（默认 
   Hessian2，可换 Kryo/JSON）可在 yml 配，
   不影响协议结构
3. **SPI 扩展（高级）**：可通过 SPI 自写全新协议，基本用不到

**面试怎么讲**：
> "Dubbo 不用 HTTP，自己设计了固定 16 
> 字节头的二进制协议，头部按字节定位解析、请求 ID 支持长连接并发，
> 内容体用 Hessian2 二进制序列化，比 HTTP 文本解析快、
> 比 JSON 体积小。"

---


## 02-BFF

### 一句话定义
BFF = Backend For Frontend，
**专门为某个前端定制数据**的一层后端服务。

### 解决什么问题
后端内部服务（mall-product）是给"所有人"用的（
后台管理/订单/秒杀/前台），但不同调用方要的数据不同：
- 后台管理要全部字段（上架状态、库存、成本价、审核状态...）
- 前台页面只要（名称、图片、价格、卖点）

如果前端直接调内部服务：
1. **暴露太多不需要的字段**（有安全风险）
2. **内部服务一改接口，前端就崩**（耦合）

### 项目里的例子
```
前端页面 →(HTTP)→ mall-front（BFF：只留前台字段，重新打包）
                      ↓ Dubbo
                  mall-product（真正的业务逻辑，内部服务）
```
mall-front 消费 mall-product 数据，只暴露 
`/front/spu/**` 等前台精简接口。

### BFF 的三个价值
1. **接口定制化**：为前台裁剪字段、组合视图
2. **解耦**：前端只依赖 BFF 的稳定契约，内部服务改了前端零感知
3. **职责单一**：BFF 是薄代理，不塞业务逻辑，
   以后加缓存/组合逻辑放这层很干净

### 面试怎么讲
> "BFF = 前端的私人管家。内部服务是大厨房，什么菜都能做；
> 管家只挑你爱吃的端上桌。"

### 常被追问
- **和 Gateway 的区别**：Gateway 在网络层（
  路由/认证/CORS/限流）；BFF 在业务层（
  视图聚合/字段裁剪）。
  网关人人要过，BFF 只为特定前端服务
- **你的 BFF 只聚合了一个服务**：诚实回答"目前主要聚合商品域，
  定位是 C 端统一业务门面，未来可扩展聚合搜索/AI 等"

---


## 03-提供者 vs 消费者

### 一句话定义
- **提供者（Provider）** = 实现接口、
  **真正干活**的一方
- **消费者（Consumer）** = 调用接口、**发请求**的一方

### 纠正误区
"提供者"**不是**"创建请求"的意思，正好相反——它**接收请求、
干活、返回结果**；创建/发出请求的是**消费者**。

### 餐厅类比
| 角色 | 类比 | 在项目里 |
|------|------|---------|
| 提供者 | 厨师（真正做菜的人） | mall-product |
| 消费者 | 点菜的顾客 | mall-front / order / search / seckill / ai |

### 项目里的代码（两边对照）
```java
// 提供者侧 —— mall-product 的 ForFrontSpuServiceImpl（厨师）
@DubboService                       // ← 告诉 Dubbo"我对外提供这个服务"
@Service
public class ForFrontSpuServiceImpl implements IForFrontSpuService {
    @Autowired private SpuMapper spuMapper;  // ← 有 Mapper，真正查库的逻辑在这里
    }

    // 消费者侧 —— mall-front 的 FrontProductServiceImpl（顾客）
    @DubboReference                     // ← 注入"远程代理"，像调本地方法
    private IForFrontSpuService dubboSpuService;

    public JsonPage<SpuListItemVO> listSpuByCategoryId(...) {
    return dubboSpuService.listSpuByCategoryId(...);  // 发请求等结果，自己不干活
    }
```

### 对比速查表
| 对比项 | 提供者（Provider） | 消费者（Consumer） |
|--------|-------------------|-------------------|
| 注解 | `@DubboService`（注册服务） | `@DubboReference`（注入引用） |
| 谁干活 | ✅ 有 Mapper，真正查库 | ❌ 无 Mapper，纯转发 |
| 谁发请求 | 收请求 | 发请求 |
| 启动顺序 | 先启动（否则消费者找不到它） | 后启动 |

### 面试怎么讲
> "@DubboService = 挂牌营业，提供服务；
> @DubboReference = 拿张名片，远程请人帮忙。所以 
> mall-product 是'有真本事'的那个，其他 5 
> 个服务都是'请它帮忙'的。"

---


## 04-服务发现（Nacos + Dubbo 协作）

### 一句话定义
服务发现 = 让消费者**找到**提供者地址的过程。Nacos 
是注册中心，Dubbo 负责实际通信。**Nacos 
只负责"找地址"，调用时它不在场。**

### 整体定位（类比）
Nacos = 
**电话号码簿/中介**——你问它"mall-product 
的电话是多少"，它告诉你，然后**你自己拨号**（Dubbo 直连），
电话内容（数据）中介听不到。这是它和"请求都要经过"的网关的本质区别。

### 完整时序（有先后顺序）
| 顺序 | 谁 | 做什么 | 对 Nacos |
|------|-----|--------|---------|
| ① | **提供者先启动** | 注册 | 上报"接口名 + IP + 端口" |
| ② | **消费者后启动** | 订阅 | 声明要哪些接口，Nacos 推送当前列表 |
| ③ | 消费者 | 缓存 | 列表存本地，之后**不每次问 Nacos** |
| ④ | 消费者 | 调用 | 从缓存选一台（负载均衡）→ Dubbo **直连**提供者 |
| ⑤ | Nacos | 变更通知 | 实例增减 → 推送给订阅者 → 更新缓存 |

### 先后顺序为什么重要
- 提供者先注册、消费者后订阅 → 这就是项目里 **product 
  必须最先启动** 的原因（中间件就绪后的第 1 个业务服务，5 个消费者在其后）

### 常被追问
- **Nacos 挂了会怎样？** 
  已建立连接的消费者继续用**缓存的服务列表**，
  已建立的调用不受影响；
  但新服务无法注册、新消费者无法发现 → 
  扩缩容/重启场景出问题。单机 
  Nacos 无集群高可用，是学习项目边界
- **为什么提供者必须先启动？** ① 消费者启动时 Dubbo 有 
  `reference check`，检查依赖服务是否可用，
  提供者没注册会导致启动失败或告警；② 
  消费者立即发起调用会找不到目标

### 面试怎么讲
> "Nacos 是中介，只帮我找到服务地址；找到之后我用 Dubbo 
> 长连接直接调提供者，数据不经过 Nacos。所以 Nacos 挂了，
> 已建立的调用靠本地缓存还能继续。"

---


## 17-gRPC

### 一句话定义
Google 的 RPC 框架——**HTTP/2 + Protobuf 二进制 + 跨语言**（.proto 定义接口 → 生成各语言代码）。

### 项目里在哪（Nacos 2.x 协议）
```
Nacos 1.x：客户端 HTTP 长轮询
Nacos 2.x：客户端 gRPC 长连接（服务端主动推送）
→ 容器线程 grpc-nio-worker = gRPC 的 Netty 线程（实证）
→ resource 事故的 "Nacos gRPC 50s 未连上" 就是它
```

### gRPC vs Dubbo
| | gRPC | Dubbo |
|---|---|---|
| 出身 | Google | 阿里（Apache）|
| 序列化 | Protobuf（二进制）| Hessian2 / 自定义 |
| 传输 | **HTTP/2** | Netty 自定义 TCP |
| 跨语言 | ✅ | 主要 Java |
| 你的项目 | Nacos 客户端通信 | 服务间 RPC（dubbo://）|

### 面试怎么讲
> "gRPC 是 Google 的 RPC 框架：HTTP/2 + Protobuf 二进制 + 跨语言。我的项目里它是 **Nacos 2.x 的通信协议**（客户端 gRPC 长连接，线程 grpc-nio-worker 实证）；Dubbo 管服务间 RPC（Netty + Hessian2），gRPC 管 Nacos 通信——各管各的。"