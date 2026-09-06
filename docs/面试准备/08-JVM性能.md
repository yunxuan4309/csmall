# 08 JVM 性能
> **核心问题**：JVM 调优依据、堆/堆外内存、GC、压测
> 难度：高
> **内容时效声明（2026-09-06 第一轮复习完成）**：本文档内容已按当时服务器/代码实测核对。**此后不随意修改**——若因 TODO 实施、代码变更导致内容过期，先更新对应事实来源文档（问题解决/评估报告/上下文文档），再回来改本档；面试使用请以最新实测为准。
---
### Q0. JVM 调优基础知识 🔴
【追问1】GC 是什么？为什么需要它？
【追问2】Full GC 是什么？为什么可怕？
【追问3】对象从创建到回收的完整流程？（场景串联）
【追问4】GC Roots / 引用链是什么意思？
【追问5】静态变量会一直占用 Eden 吗？要不要避免？
【追问6】动态年龄判定是什么？
【追问7】G1 是什么？为什么项目用它？
【简答】**GC = Garbage Collection（垃圾回收）**——JVM 自动管理内存、回收不再使用对象的机制，开发者不用手动 free。JVM 内存分堆和堆外：堆按对象存活概率分成新生代（Eden + 2×Survivor）和老年代（Old Gen）；GC 自动回收不再使用的对象，分 Minor GC（只扫新生代，毫秒级）和 Full GC（扫全堆，秒级）。诊断用 jstat 家族工具看各区域使用量和 GC 次数。
【深挖】**一、堆内存结构（分代模型）**

| 区域 | 放什么 | 回收方式 |
|---|---|---|
| Eden | 新对象出生地（所有 new 先在这分配） | Minor GC，频繁 |
| Survivor S0/S1 | 熬过一次 GC 的幸存者 | GC 时两区互相复制交换 |
| Old Gen | 熬过多次 GC 晋升的长寿对象 | Full GC，慢 |
| Metaspace（堆外） | 类元数据（JDK8 起替代 PermGen） | Full GC 也会扫 |
- **为什么分代**：统计规律"绝大多数对象朝生夕死"（80%+ 活不过第一轮 GC）→ 让它们死在新生代（区域小、回收快），老年代保持干净（Full GC 少）
- **对象一生**：new → Eden → Minor GC 存活 → 进 S0/S1 反复复制（默认 15 次，动态年龄）→ 晋升 Old → 等 Full GC
- **大对象例外**：超大对象（大数组）直接进老年代，绕开新生代——大对象多了会挤爆 Old 引发 Full GC
- **类比**：新生代=新员工试用区（大部分试用期就走人），Old=正式员工区（人少但清退成本高）**二、GC 是什么**
- **GC = Garbage Collection 垃圾回收**：自动找出"不再被引用的对象"，释放其内存——开发者不用手动 free（C/C++ 要手动，易内存泄漏/悬垂指针）
- **怎么判断垃圾**：可达性分析——从 **GC Roots**（局部变量/静态变量/常量池/本地方法栈）出发做引用链扫描，不可达 = 垃圾。引用计数法有循环引用缺陷，主流不用
- **三大回收算法**（先懂"碎片"：内存被零散回收后留下的小洞——总空间够，但找不到连续大块放不下大对象。类比停车场：B 车被拖走空出 5 格，但被 A/C 夹住，要放 6 格的车放不下。类比 OS：就是操作系统内存管理的**外部碎片**，JVM = 自己管理堆的"迷你 OS"，分页/紧凑同一套思想）：  ① **标记-清除**：标记存活 → 清除垃圾。不搬对象所以快，但垃圾零散分布 → **有碎片**（老年代用，常配合整理）  ② **标记-复制**：内存分两块，存活对象复制到空块（紧凑排列）→ 整块清空。**无碎片**但浪费一半空间（新生代用，存活率低搬得少，Survivor 兜底——所以新生代永远没碎片）  ③ **标记-整理**：标记后把存活对象全推到一端消除碎片。**无碎片 + 利用率高**但最慢（老年代用，存活率高复制不划算）

| 算法 | 搬对象吗 | 碎片 | 速度 | 适用 |
|---|---|---|---|---|
| 标记-清除 | 不搬 | 有 | 快 | 老年代 |
| 标记-复制 | 搬存活 | 无 | 快 | 新生代 |
| 标记-整理 | 搬存活 | 无 | 最慢 | 老年代 |  💡 **面试深度点**：G1 把堆分成固定大小 Region（区域化），就是借鉴 OS **分页**"固定大小页框消除外部碎片"的思想——这也是项目用 G1 的原因之一
- **STW（Stop The World）**：GC 时应用线程必须暂停——这是 GC 调优的核心矛盾：追求低停顿（G1/ZGC）还是高吞吐（Parallel）
- 面试话术："GC 是 JVM 自动的内存回收机制，用可达性分析判断垃圾，开发者不用手动管理内存。代价是 STW——回收时应用线程暂停，所以调优就是在这对矛盾里找平衡"**三、Full GC 是什么**
- **Minor GC（Young GC）**：只扫新生代，毫秒级、频繁——正常现象，不用怕
- **Full GC**：扫**整个堆**（老年代 + 新生代 + Metaspace），STW 秒级——高并发下就是"服务假死"
- **触发条件**：① 老年代满 ② Metaspace 满 ③ 晋升失败（Survivor 放不下直接升 Old，Old 也满）④ 代码调 System.gc()（可加 -XX:+DisableExplicitGC 禁用）
- **健康指标**：FGC 次数应该极少——项目实测 Seata 5 天 **YGC=19 / FGC=0**（⭐ 实测），0 次 Full GC 才是敢把 2G 堆降到 512M 的底气
- **Full GC 频繁的排查方向**：内存泄漏（对象只增不减）/ 大对象直进老年代 / 堆设太小 / 晋升失败
- 面试话术："Minor GC 扫新生代毫秒级不可怕，**Full GC 扫全堆停顿秒级才是线上卡顿的头号嫌疑**。我调优的核心目标就是让 FGC 趋近于 0"**四、诊断工具（jstat 家族）**
- `jstat -gc <pid>`：堆各区域容量/使用量 + GC 次数耗时，输出列：  
- S0C/S1C/S0U/S1U（Survivor 容量/使用）、EC/EU（Eden）、OC/OU（Old）、MC/MU（Metaspace）  
- YGC/YGCT（Minor GC 次数/耗时）、FGC/FGCT（Full GC 次数/耗时）、GCT（总耗时）
- 其他指令：`jstat -gcutil`（使用率百分比，更直观）、`-gccapacity`（含未分配容量）、`-gcnew`/`-gcold`（只看新生代/老年代）、`-class`（类加载数）、`-compiler`（JIT 编译）
- **兄弟工具**：`jps`（找 PID，一切的前提）/ `jmap -heap`（堆概览）/ `jstack`（线程栈，CPU 暴涨时抓）/ `jinfo`（看参数）/ `jcmd`（万能入口）
- ⚠️ **诚实点**：生产容器是 JRE（temurin:21-jre-alpine），只有 java/jfr/keytool，**无 jstat/jstack/jmap/jcmd**（TODO #28 实测）——调优当时能跑 jstat 是因为 Seata 官方镜像是 JDK。面试答"用什么测的"要补一句"现在容器是 JRE 抓不了，所以我评估换 JDK 镜像/装 Arthas"**五、对象的一生（场景流程：秒杀压测）**
> 场景：100 并发打秒杀接口，后端每秒创建大量对象。盯住其中一个订单对象看完整生命周期：
- **① 出生**：`new SeckillOrder()` → 分配在 **Eden**。同时出生一堆"工具人"对象（DTO/日志/序列化缓冲）全挤在 Eden。类比：新生婴儿室
- **② 第一次 Minor GC**（Eden 满触发）：JVM 从 GC Roots 沿引用链扫描——大部分请求已处理完、栈帧弹出、没人引用 → **当场死亡回收**；少数还被引用的 → 复制到 **S0**，Eden 清空。类比：第一次试用期考核，80% 走人
- **③ 反复 Minor GC**：对象在 **S0 ↔ S1** 之间来回复制（像两个桶倒水：永远一边倒空一边接收），每次存活 age+1。复制 = 紧凑排列 = 无碎片
- **④ 晋升**：age 到 15（或动态年龄判定 / Survivor 放不下）→ 晋升 **Old Gen**。能活这么久的多是被长寿对象引用（会话/缓存/连接池）。⚠️ 大对象例外：直接进老年代
- **⑤ Full GC**：老年代也满 → 扫**整个堆**（新生代+老年代+Metaspace），标记-整理移动存活对象，**STW 秒级 = 服务假死**
- **⑥ 死亡**：不可达 → 回收 → 内存归还**六、GC Roots 与引用链**
- **GC Roots = 回收的"根"，引用扫描的起点**——绝对活着、不可回收：栈上局部变量 / 静态变量 / 常量池 / 活跃线程
- **引用链**：从 GC Roots 出发，对象 A 引用 B、B 引用 C……连成的链条——注意是**对象实例之间的引用关系**，不是类之间的关系  
```  GC Roots（栈上局部变量 order）    └──
> Order 对象 ──
> Address 对象              └──
> List<Item
> ──
> Item 对象    ← 链上全存活  
```
- 能走到的 = 存活；走不到的 = **不可达 = 垃圾**。类比：族谱——从祖先往下数得着的都是活人，失联的清理
- **静态变量是 GC Roots** → 它引用的对象永远可达、永不回收（除非类被卸载）**七、静态变量与内存泄漏**
- 静态变量引用的对象**不占 Eden**——它永远活着，熬过 15 轮自然晋升**老年代**长期驻留
- **真正要避免的不是静态变量，而是"无界静态集合"**（static Map/List 只进不出）：  
- 每次请求往里塞数据、永不删除 → 老年代被撑满 → **Full GC 频繁 → 假死**  
- 这是内存泄漏的经典案例（泄漏 = 该回收的回收不掉，不是内存真的"漏"了）
- 正确姿势：缓存带 **TTL/淘汰策略/上限**（呼应项目 Redis 缓存 TTL 思想）；Spring 单例 Bean 里不要用静态集合攒数据
- 面试话术："静态变量本身没问题（它是 GC Roots），问题是无界增长——缓存必须有淘汰策略，不然老年代被撑爆，Full GC 频繁"
- **为什么无界静态集合会挤爆老年代**：static Map 是 GC Roots → 每次请求 put 进去的 value **永远可达** → Minor GC 死不了 → 熬过 15 轮晋升老年代 → 老年代**只进不出被填满** → 满则触发 Full GC，但扫了也白扫（对象还被引用着）→ 老年代依旧满 → 又触发 → **Full GC 越来越频繁 → 假死 → 最终 OOM**。类比：只进不出的仓库，货都有主人，翻仓库也扔不掉
- **为什么会只进不出**：① 只 put 不 remove（最常见——拿 HashMap 当缓存却忘了淘汰）② key 永不重复（时间戳/UUID 当 key，每次请求都是新 key，map 只增不减）③ key 是增长维度（userId 当 key，用户越多 map 越大）。根治 = 缓存带 TTL/淘汰策略/上限，或直接上 Redis/Caffeine

## 八、动态年龄判定（Survivor 快满时提前晋升）
- 默认要熬到 age=15 才晋升，但如果 **Survivor 快满了**，JVM 不等 15，提前晋升一批
- **规则**：从 age=1 开始累加各年龄对象的大小，**累加超过 Survivor 一半（50%）的那个年龄及以上的对象，全部晋升**
- **数字例子**：Survivor 容量 10MB，里面住着——  
- 1 岁对象 2MB  
- 2 岁对象 3MB  
- 3 岁对象 5MB（合计 10MB，满了）  
- JVM 累加：1 岁 2MB → 加上 2 岁共 5MB = **达到一半（5MB）** → **2 岁及以上（3+5=8MB）全部晋升老年代**，Survivor 只剩 1 岁的 2MB，腾出空间
- 类比：宿舍 10 个床位住满了——宿管不一个个劝退，直接算"哪一批老员工加起来超过一半床位"整批转正搬走，腾地方
- 作用：防止 Survivor 溢出（放不下强行升 Old 反而更糟）；`-XX:MaxTenuringThreshold=15` 只是上限，实际晋升由动态判定决定**九、G1 收集器速览（Garbage First，JDK 9+ 默认）**
- **G1 = Garbage First（垃圾优先）**：回收时**优先收"垃圾最多"的 Region**——哪块垃圾密度高先收哪块，回收收益最大化（名字由来）
- **堆切成大量固定大小 Region**（典型 1~32MB），**没有物理分代**——每个 Region 角色动态：这轮是 Eden，下轮可能变 Survivor/Old，按需划分
- **回收不扫全堆，增量收**：每次只收一批垃圾最多的 Region，配合 `-XX:MaxGCPauseMillis=200`（项目实测）= 告诉 G1"每次停顿别超 200ms"，它自己算收多少块不超时 → **停顿可控**
- **对比传统**：传统（Parallel/CMS）= 物理一整块 Eden/Old，必须全区域扫，停顿不可控；G1 = Region 化增量收，停顿可控（类比：传统=全小区大扫除日一次全扫停摆；G1=哪栋楼垃圾最多先清哪栋）
- **借鉴 OS 分页**：固定大小 Region = 固定大小页框 → 消除外部碎片 + 只处理需要的区域（呼应二、碎片类比）
- **项目实证**：11 个微服务容器实测全部 `UseG1GC + MaxGCPauseMillis=200`（08 Q1 参数表）；低流量下停顿目标几乎不触发，配合"FGC 趋 0"
- 面试话术："G1 是 JDK9+ 默认收集器，把堆切成固定大小 Region、角色动态划分，回收时优先处理垃圾最多的 Region，配合 MaxGCPauseMillis 做到停顿可控——像分页让 OS 只换需要的页，G1 只收收益最高的区域"【速记】"堆分新生代（Eden+Survivor）老年代（Old），Metaspace 在堆外；GC 自动回收垃圾，Minor 扫新生代毫秒级、**Full 扫全堆秒级=线上卡顿元凶**；对象一生：Eden → Minor GC 存活 → S0/S1 反复复制 → age 15 或动态年龄判定 → 晋升 Old → Full GC 回收；GC Roots=引用扫描起点（局部变量/静态变量），引用链走不到=垃圾；静态变量引用的对象永不回收，要防的是**无界静态集合**；G1=垃圾优先收集器，Region 化、增量回收、停顿可控（项目 UseG1GC+MaxGCPauseMillis=200）；调优目标 FGC 趋 0——Seata 实测 5 天 0 次 Full GC；诊断 jstat -gc 看 OU/EU/YGC/FGC"
---
### Q1. 你做了哪些 JVM 调优？依据是什么？🔴
【追问1】为什么敢降 Seata 的堆？
【追问2】堆外内存为什么要限制？
【简答】对 Seata、SkyWalking OAP、11 个微服务做 JVM 调优。核心依据是用 `jstat` 实测堆使用——Seata 2G 堆实际只用 27MB，所以敢降到 512M。
【深挖】
- **Seata 调优**：  
- 实测 `jstat -gc`：Old Gen 776MB 容量只用     27MB（3.5%），Eden 用了 62%  
- 结论：2G 堆 97% 空间浪费 → `-Xmx2048m →     512m`，RSS 从 1.46G → 0.38G
- **SkyWalking OAP**：`-Xmx1024m →   512m`（11 服务 trace 量小）
- **微服务堆外限制**（2026-08-28 容器实测）：  `MaxDirectMemorySize=64m`、  `MaxMetaspaceSize=256m`（不是 128m——历史坑：  SW Agent 40+ 插件类超 128MB 撑爆 Metaspace，提到 256m）、  `ReservedCodeCacheSize=64m`
- **为什么限制堆外**：堆外（Metaspace/直接内存）  默认无上限，Spring Boot 类多时 Metaspace   轻松破 120MB；Docker 看到的是 RSS（堆+堆外），  不限制堆外 RSS 会虚高
- **可调的参数全景**（面试展开）：  ① 堆：`-Xms` 初始堆 / `-Xmx` 最大堆（最关键，够用就好——     大堆 Full GC 停顿长，小堆频繁 GC）  ② 堆外：`MaxDirectMemorySize`（NIO/Netty 直接内存，防泄漏     撑爆）、`MaxMetaspaceSize`（类元数据，防类加载爆）、     `ReservedCodeCacheSize`（JIT 代码缓存）  ③ GC：`-XX:+UseG1GC`（JDK9+ 默认，区域化回收）、     `-XX:MaxGCPauseMillis=200`（G1 目标停顿）  ④ 诊断：`-XX:+HeapDumpOnOutOfMemoryError` + `HeapDumpPath`     （OOM 自动 dump 留现场）、`-Xlog:gc`（GC 日志）  ⑤ 其他：`-XX:+UseStringDeduplication`（字符串去重）、     `-Xss`（线程栈 1M，线程多才动）
- **调优流程**（面试讲专业）：① 先测 jstat -gc/jmap -heap  看实际使用 → ② 按实测设 -Xmx（留 20~30% 余量）→  ③ 堆外全限制（DirectMemory/Metaspace/CodeCache）→  ④ 重启验证 GC/内存 → ⑤ 迭代。**堆不是越大越好，  够用就好**
- **面试话术**："调优的第一原则是**先测量再动手**——我用   jstat 看到 Seata 堆只用了 3.5%，才知道 2G   是浪费。没有数据支撑的调优都是瞎猜。可调参数分四类：  堆（-Xmx）、堆外（DirectMemory/Metaspace）、GC（G1/  pause 目标）、诊断（HeapDump 留现场）"
---
### Q2. Docker 里 JVM 内存为什么比 -Xmx 大？🔴
【追问1】RSS 是什么？
【追问2】堆外内存包括什么？
【追问3】内存超过限制会怎样？OOM 吗？
【简答】Docker 看到的是 RSS（常驻内存）= 堆 + 堆外。堆外包括 Metaspace、线程栈、NIO 直接内存、JIT 编译缓存。这些默认无上限。
【深挖】
- **RSS 详解（Resident Set Size，常驻内存）**：  
- 定义：进程**实际占用的物理内存**（RAM）——`top`/`ps aux` 里的 **RES 列**，`docker stats` 看的就是它  
- **vs VSZ（虚拟内存）**：VSZ = 进程"看到的"地址空间（含未真正使用的映射，像租房合同面积）；RSS = 实际住进去的房间（真实占用）——**容器 OOM 判断以 RSS 为准**  
- **RSS = 堆 + 堆外 + JVM 自身开销**：堆有 -Xmx 封顶，堆外（Metaspace/线程栈/直接内存/CodeCache）默认无上限 → 这就是"虚高"来源
- **例子**：mall-product `-Xmx448m`，但   RSS 906MB——多出的 458MB 是堆外
- **堆外构成**：

| 部分 | 大小 | 说明 |
|---|---|---|
| Metaspace | ~200M | 类元数据（Spring Boot 类多） |
| 线程栈 | ~100M | 每线程 1MB |
| NIO 直接内存 | ~60M | Netty/Dubbo 缓冲 |
| JIT Code Cache | ~50M | 编译缓存 |
- **调优后**：加   Metaspace/DirectMemory/CodeCache   限制，RSS 下降
- **内存超限两层结局**（追问3）：  
- **第一层 JVM 内部**（超 -Xmx/Metaspace/DirectMemory）：先 Full GC 自救 → 救不了抛 `OutOfMemoryError`（Java heap space / Metaspace / Direct buffer memory）——**可捕获、有现场**（项目配 `HeapDumpOnOutOfMemoryError` 自动留 dump）  
- **第二层 容器层**（RSS 超 mem_limit，cgroup）：**内核 OOM Killer SIGKILL 强杀**——无异常、无 dump，容器 OOMKilled、RestartCount+1，服务直接消失靠 restart 拉起。类比：超公司预算=HR 谈话（有交代）；超房东合同上限=断水断电赶人（没机会收拾）  
- **顺序认知**：堆内分配失败 → JVM 抛 OOM（自己管，有现场）；RSS 超容器限制 → 内核强杀（OS 管，无现场）  
- **项目现状**：容器 `mem_limit=0`（无限制，TODO R7 实测）→ 失控进程可吃满宿主 16G → 宿主机 OOM 连坐（历史杀过 ES）；R7 加 mem_limit 后：被杀的是超限容器进程，宿主机安全 = 隔离故障边界  
- 面试话术："超内存分两层：JVM 内部先 Full GC 自救，救不了抛 OOM——可捕获、配 HeapDump 留现场；但更危险的是容器层——RSS 超 mem_limit 被内核 OOM Killer 直接强杀，没异常没 dump。所以我两边都做：JVM 限制 + HeapDump，容器 mem_limit 兜底防连坐"
- 面试话术："很多人以为 -Xmx 就是内存上限，其实**RSS =   堆 + 堆外**，堆外默认无上限。这也是容器里 JVM   内存虚高的原因"
---
### Q3. 压测怎么做的？结果怎么解读？🔴
【追问1】压测工具？为什么？
【追问2】压测结果说明了什么？
【追问3】没有真实用户，调优数据能作数吗？真实用户进来数据变了怎么办？
【简答】用 Python 脚本 100 并发压测秒杀接口，结果 8 成功/89 限流。Sentinel 显示通过 10/拒绝 62，验证限流机制生效。
【深挖】
- **工具选型**：Python 脚本（纯标准库），不用 JMeter（  500MB JVM，服务器内存紧），不用 hey（下载失败）
- **为什么打内部端口**：`localhost:10007` 绕开   5M 公网带宽
- **结果**：100 并发 → 8-36 成功（Sentinel   QPS=10 放行）+ 64-89 限流
- **无超卖**：数据库验证库存准确扣减
- **诚实边界 + 真实用户来了怎么办**（追问3）：  
- **诚实点**：当前数据来源 = 秒杀压测（100 并发）+ 测试环境 + 空转观测（CPU<5%、Seata 5 天 0 笔事务）——是**低负载基线**，不是真实用户流量；真实用户进来堆使用/GC/RSS 一定会变  
- **判断分两类**：① **静态事实**（不依赖流量）——Seata 2G 堆只用 27MB = 资源错配（"交通指挥灯不需要停车场"），有没有用户都是浪费，这类修正永远成立；② **动态容量**（会随流量变）——-Xmx/Metaspace 是"当前负载 + 20~30% 余量"设的，不是结论是"当前值"，靠**测量→调整→再测量**循环修正（调优本质 = 循环，不是一次性交付）  
- **真实用户进来的五步应对**：① 上线前压测建模（已有压测演练）② 灰度小流量观察（盯 jstat/GC/RSS）③ 按新数据重调参数 ④ **架构扩容优先**（流量涨 10 倍先想缓存/限流/加副本，不是调参——单机 4C16G 有物理极限，见 TODO #4 集群化）⑤ 告警兜底（没告警调得再好挂了不知道 = 白调，见 TODO #30）  
- **一句话**：JVM 调参是**最后 10% 的优化**，前 90% 是架构（缓存/异步/限流/扩容）——这也解释 TODO #4 结论"无流量压力不加副本"  
- 面试话术："演示项目数据来自压测和空转，不是真实流量。但判断分两类：静态事实（Seata 用 27MB 配 2G 就是浪费，有无用户都是浪费）和动态容量（确实会变，靠测量-调整-再测量循环）。真实用户进来：先压测建模、灰度观察、按新数据重调，更关键的是先架构扩容——JVM 调参只是最后 10%"
- **面试话术**："压测验证的不是'扛 100 QPS'，  而是**保护机制在高并发下真的在工作**——限流拦住了超出的请求，  库存没超卖"
---
### Q4. 说几个你在项目中用到的 GC/内存概念？
【追问1】G1 和 CMS 区别？
【追问2】Metaspace 和堆的区别？
【简答】项目用 G1 GC（低延迟，可预测停顿）。Metaspace 存类元数据（不在堆内），G1 把堆分成 Region 管理。
【深挖】
- **G1 vs CMS**：G1 分 Region、可预测停顿（  MaxGCPauseMillis=200），适合大堆；CMS   标记清除、碎片化、已弃用  
- **CMS 是什么**：Concurrent Mark-Sweep 并发标记清除（JDK5 老年代收集器）——把标记/清除并发化（初始标记 STW 短 → 并发标记不停 → 重新标记 STW 短 → 并发清除不停），当年"低延迟"代名词  
- **为什么被淘汰**：① 标记-清除 → 碎片 ② Concurrent Mode Failure（并发清除时内存不够 → 退化 Serial Old 全 STW，停顿反而更长）③ 停顿不可预测（尽力低，不是可控低）→ JDK9 废弃、JDK14 移除  
- **与 G1 关系**：不是敌对，是**接班**——G1 继承"低延迟"目标、补上短板（Region 化无碎片 + MaxGCPauseMillis 停顿可控）；配置上同一 JVM 只能二选一（`UseConcMarkSweepGC`/`UseG1GC` 互斥）  
- 话术："CMS 是低延迟老方案，思路是标记清除并发化，但有碎片和并发失败退化全 STW 两个硬伤；G1 是它的继任者，Region 化消碎片 + 停顿可控，所以 JDK9 起默认、CMS 在 JDK14 移除，我们项目直接用 G1"
- **Metaspace vs 堆**：Metaspace 存类元数据（  JVM 启动后加载的类），在堆外，默认无上限（所以要   MaxMetaspaceSize）
- **分代模型**（补充）：堆分新生代（Eden 新对象 +  Survivor S0/S1 幸存区）+ 老年代（Old Gen，多次 GC  存活晋升，放 Spring 单例/缓存/连接池等长生命周期对象）。  **判断内存够不够看 Old Gen 使用量最准**（稳定存量）；  你的例子：Seata Old Gen 776MB 只用 27MB（3.5%）→ 2G  堆 97% 浪费；Old Gen 持续涨 + Full GC 频繁 = 泄漏/堆小
- **调优参数**：`-XX:+UseG1GC`、`-XX:MaxGCPauseMillis=200`、`-XX:MaxMetaspaceSize=256m`（实测；128m 曾致 SW Metaspace 撑爆）
- 面试话术："项目用 G1 是因为它**停顿可预测**，  适合微服务这种对响应敏感的；Metaspace   是类元数据区，在堆外，  不限制会无限增长"
---
### Q5. 内存泄漏/溢出排查过吗？
【追问1】Druid 线程挂死？
【追问2】OOM 怎么处理？
【简答】遇到过 Druid 连接池线程挂死（资源服务上传时），排查后换 HikariCP。OOM 处理：配置 HeapDump + 分析。
【深挖】
- **Druid 线程挂死**：上传请求时线程挂死，定位到连接池问题，  换 HikariCP 解决
- **OOM 应对**：JVM 配 `-XX:  +HeapDumpOnOutOfMemoryError` +   `HeapDumpPath`，OOM 时自动 dump 分析
- **堆外 OOM**：Direct buffer memory（  Netty 等），限制 MaxDirectMemorySize
- **CPU 暴涨排查流程**（面试场景题标准答案）：  ① top 找 CPU 高进程 → ② top -Hp 找线程 → ③ 线程号转十六  进制 → ④ jstack 抓栈搜 nid → ⑤ 判断：业务死循环/锁等待/  GC 线程（GC 就转内存排查）
- **Full GC 排查流程**：  ① jstat -gcutil 看 GC 频率/停顿/Old 占比 → ② jmap -heap  看堆配置 → ③ jmap -dump 抓堆转储（⚠️ STW 低峰做）→  ④ MAT Dominator Tree/Leak Suspects 找大头 → ⑤ 对照代码：  无界集合/连接未关/ThreadLocal 不 remove/大对象查询
- **ThreadLocal 深入**（追问：是什么 / 为什么泄漏 / 父子线程通信）：  
- **是什么**：线程本地变量——每个线程有自己独立的副本（Thread 内部 `ThreadLocalMap`，key=ThreadLocal（弱引用），value=存的值），线程间互不可见。类比：每个员工有自己的储物柜，钥匙（ThreadLocal）一样，但柜子内容各自独立  
- **打饭类比（最好懂）**：线程=打饭的人，ThreadLocal=发饭盒的规则（谁来给谁一个新饭盒），set=往自己饭盒打菜，get=看自己饭盒——A 打红烧肉、B 打青菜互不干扰；普通变量=只有一个公共饭盒，后写覆盖（要加锁）。子线程=你创建的线程，默认饭盒内容不共享，要共享用 InheritableThreadLocal 复印一份  
- **为什么泄漏**：key 是**弱引用**（ThreadLocal 外部引用没了 → key 变 null），但 value 是强引用还留在 map；若线程是**线程池长命线程**（不销毁）→ value 永远被持有 → 泄漏。所以**用后必须 remove**（项目 TraceIdFilter 请求结束 MDC.remove 就是正确示范；不 remove 还会线程池复用串号——下个请求看到上个 traceId）  
- **父子线程通信**：ThreadLocal 默认**不传给子线程**（子线程自己的 map 是空的）；方案：① `InheritableThreadLocal`（JDK 内置，创建子线程时**复制一份**——只复制创建那一刻，之后父改子不变，且**线程池场景失效**）② `TransmittableThreadLocal`（TTL，阿里开源，**任务提交时传递、执行完恢复**——线程池/异步场景标配，跨线程传 traceId 用它）  
- **传递代码示例**（traceId 场景，看输出）：    
```java    // ① 默认 ThreadLocal:不传 → 子线程 null ❌    ThreadLocal<String
> tl = new ThreadLocal<>(); tl.set("T-001");    new Thread(() -
> System.out.println(tl.get())).start();        // null    // ② InheritableThreadLocal:创建时复印一次 ✅(之后父改子不变,线程池失效)    InheritableThreadLocal<String
> itl = new InheritableThreadLocal<>(); itl.set("T-001");    new Thread(() -
> System.out.println(itl.get())).start();       // T-001    // ③ TTL:每次任务提交时传递、执行完恢复 → 线程池标配 ✅    pool.submit(TtlRunnable.get(() -
> System.out.println(ttl.get())));  // 随任务传    
```  
- **总结表**：默认 ThreadLocal=不传(null)；InheritableThreadLocal=创建时复印一次（线程池场景失效）；TTL=每次任务都传（线程池/异步标配，跨线程 traceId 用它）  
- **MDC 用法示范**（防泄漏+防串号）：`try { MDC.put("traceId", uuid); chain.doFilter(...); } finally { MDC.remove(); }`——finally 保证异常也清（项目 TraceIdFilter 即此写法）  
- **与项目连接**：HTTP 链路 MDC 有值，但**定时任务/MQ/Dubbo 线程 `[]` 空**（ThreadLocal 默认不跨线程）→ 跨线程传递方案 = TTL；项目选 SW logback 集成（TODO #16）覆盖全场景，更省人力。面试讲："跨线程 traceId 我知道 TTL 方案，项目用 SW logback 集成覆盖"  
- 话术："ThreadLocal 是线程隔离变量，泄漏根因 = 弱引用 key + 线程池长命线程；MDC 就是 ThreadLocal，用完必须 remove。跨线程传递：InheritableThreadLocal 只复制一次，线程池要用 TTL"
- **生产诊断工具**：Arthas（阿里开源，免重启在线诊断  dashboard/thread/stack——大厂生产标配）；JDK 自带  jstack/jstat/jmap + MAT
- **⭐ 你的容器是 JRE 没有诊断工具**（2026-08-28 实测）：  temurin:21-jre-alpine 只有 java/jfr/keytool，**无 jstack/jmap/  jstat/jcmd**——当前生产想抓线程栈/堆转储抓不了。有  HeapDumpOnOutOfMemoryError（OOM 自动 dump ✅）+ JFR 可用。  改进：换 JDK 镜像 / 装 Arthas / 启动加 JFR 录制（TODO #28）
- **Arthas 速查**（阿里开源，生产诊断标配）：  能力：`dashboard` 全局指标 / `thread` 看线程（CPU/锁）/    `stack` 定位方法调用 / `watch` 观察方法入参返回值 /    `trace` 链路耗时——**免重启在线诊断**（JRE 也能 attach）  使用：`java -jar arthas-boot.jar` → 选 PID attach →    命令即输即用；比 jstack/jmap 强在"不用重启 + 在线观察"  大厂用法：**不是直接 SSH 敲，而是平台化**——命令走内部    诊断平台（Web/代理），**权限验证（普通研发只读，dump/    watch 要审批）+ 审计留痕**；金融/信创因 attach 权限大    禁用或审批。类比：不是不用螺丝刀，是做成带门禁的    电动工具箱；最小权限原则在诊断领域的应用
- 面试话术："Druid 线程挂死是真实踩过的坑，  最终**换连接池解决**。我也配了 HeapDump，OOM   时能留现场分析。CPU/Full GC 排查流程我清楚（top→  jstack / jstat→MAT），生产标配 Arthas（免重启在线  诊断）；大厂把 Arthas 平台化——权限+审批+审计。  诚实说我的容器是 JRE 抓不了 jstack，要换 JDK 镜像或  装 Arthas"
---
### Q6. 服务器内存紧张时怎么权衡？哪些能降哪些不能？
【追问1】ES 为什么不能乱降？
【追问2】为什么微服务堆外限制不影响性能？
【简答】中间件和微服务分开看：Seata/OAP 这类协调型可降堆；ES 要保留查询性能不能乱降；微服务的堆外限制选合适值不影响正常使用。
【深挖】
- **能降**：Seata（协调器，不用存大量数据）、OAP（  流式处理不驻留）、无状态微服务堆外
- **谨慎降**：ES（查询性能依赖内存）、MySQL（缓冲池）
- **为什么 OAP 512M 够**：OAP   是流式处理——Agent 发数据 → OAP 聚合 →   写 ES →   释放内存，不长期持有
- **为什么 Seata 512M 够**：事务协调器，  一个事务上下文几 KB，几百并发事务也只要几十 MB
- **完整故事线（面试讲述版，五步）**：  ① **发现**：`free -h` 看内存 93%，可用只剩 1.1G；     且历史 ES 被 OOM Killer 杀过（内存不足的教训在前）  ② **排查**：`docker stats` 逐个看容器 → 定位大头     （Seata 1.46G / OAP 1.17G / ES 1.12G / Nacos 963M）；     深入用 `jstat -gc` 实测 Seata：Old Gen 容量 776MB     **实际只用 27MB（3.5%）**——2G 堆 97% 是浪费；     再看 RSS vs -Xmx 差（堆外虚高，Q2 知识）  ③ **决策**（数据驱动 + 分类型）：     能降 = Seata 2G→512M（实测 27MB，留足余量）、       OAP 1G→512M（流式不驻留）、       11 微服务加堆外限制（DirectMemory/Metaspace/CodeCache）     不能降 = ES（查询性能靠内存，降了分片/OOM）、       MySQL buffer pool（性能）、       product/order/seckill（秒杀突发 Full GC 风险）  ④ **结果**：内存 93%→71%，释放 ~3G，可用 1.1G→4.4G；     容器更稳；**给后续加副本（TODO #4）留了空间**  ⑤ **原则**：够用就好 + 留余量 + **先测再调**；     **能降的降、不能降的碰都不碰**（业务核心/性能敏感保留）
- 面试话术："降内存要看**服务类型**——协调型（Seata/OAP）  堆利用率低可以降，数据型（ES/MySQL）要保性能不能乱降。  我的故事线：free 发现 93% → docker stats 定位大头 →  jstat 实测 Seata 只用 27MB → 才敢降 2G→512M →  结果释放 3G、可用翻 4 倍、给加副本留了空间。  核心：**数据驱动 + 分类型权衡，不是一刀切**"
---
### Q7. 调优后效果怎么验证的？
【追问1】指标对比？
【追问2】有没有副作用？
【简答】用 `docker stats` + `free -h` 对比调优前后 RSS 和系统内存。调优后 93% → 71%，服务正常运行无副作用。
【深挖】
- **对比数据**：  
- 系统内存：13G → 10G（释放 ~3G）  
- Seata：1.46G → 0.38G  
- OAP：1.17G → 1.08G  
- 11 微服务：7.2G → 5.0G
- **副作用检查**：Full GC 次数、响应时间、Seata   事务成功率（观察期）
- 面试话术："调优后我用 docker stats 对比前后内存，  同时观察 Full GC   和响应时间确认没副作用——调优不是降完就完，要验证"
---
### Q8. 微服务为什么每个都单独 JVM？能不能合并？
【追问1】JVM 数量的代价？
【追问2】什么时候该合并？
【简答】每个微服务独立 JVM 是微服务架构的代价（隔离 + 独立部署）。代价是内存开销大（每个 JVM 有堆+堆外），但换来故障隔离和独立扩缩容。
【深挖】
- **为什么独立**：故障隔离（一个服务 OOM 不影响其他）、  独立部署、独立扩缩容
- **代价**：11 个 JVM 基础开销叠加（每份类加载、  Metaspace、线程栈）
- **权衡**：学习项目可接受；生产如果资源紧张可考虑模块化单体（  Modulith）或按需合并  
- **Modulith 真实存在**：Spring 官方 2023 年推出 Spring Modulith——单体按业务模块划边界 + 编译期验证依赖方向 + 模块事件通信，需要时把模块拆成服务（保留拆的选项）  
- **企业案例**：Kakao Bank 落地实践；Amazon Prime Video 监控服务从微服务合并回单体**省 90% 成本**（"微服务退潮"最著名案例）；Segment 回归单体  
- **本质 = 成本账**：微服务收益（独立扩缩容/团队自治）靠规模兑现，小团队小流量时单体/Modulith 更划算——我们 11 个 JVM 内存吃紧就是这个账的活例子，但学习项目要跑通全家桶，保留微服务形态  
- **好处**：内存省（1 个 JVM vs 11 个）/ 运维简化（一套部署监控）/ 模块调用=本地方法（无网络开销）/ **本地事务（不用 Seata）**/ 模块边界清晰（演进主动权）  
- **⭐ 真实见闻（国企子公司兼职）**：亲见过"按业务模块分包的单体"——order/product 各模块有独立 Controller/Service/Mapper，整个应用还是一个 Spring Boot、一个 JVM = 模块化单体**朴素形态**  
- **朴素版 vs 正规版**：朴素版（大概率国企那种）= 按业务分包 + 模块间 @Autowired 直接调 + 边界靠自觉；正规版（Spring Modulith）= 编译期强制验证边界（import 内部类报错）+ 模块事件通信。思想一样，装置不同（"分区坐" vs "玻璃隔断+门禁"）  
- **国企为什么用**：流量不大（独立扩缩容收益兑现不了）/ 人力有限（运维全家桶不划算）/ 数据统一管控（一个库好审计好备份）/ 保留按模块拆分的可能  
- **面试用**："兼职见过模块化单体朴素形态——单体部署、按业务分包、数据共享；和我们现在 11 个微服务正好是同一问题的两个极端答案"
- 面试话术："11 个 JVM   是微服务的**必然代价**——换来隔离和独立部署。  我做了堆外限制缓解内存压力，但这是架构选择，不是 bug"
---
### Q9. 如果秒杀并发再大一倍（200/1000），你怎么优化？
【追问1】瓶颈在哪？
【追问2】怎么扩容？
【简答】瓶颈顺序：Sentinel 限流（已挡）、Redis（预扣）、MQ（削峰）、数据库（落库）。增大并发：① 提升 Sentinel 阈值（或分片）；② Redis 集群；③ MQ 分区/多个消费者；④ 数据库分库分表/读写分离。
【深挖】
- **当前**：QPS=10 限流，100 并发能挡 89 个，  数据库压力小
- **再翻倍**：如果提高限流阈值，Redis 和 MQ 是下一个瓶颈  
- Redis：单机内存/带宽有限 → 集群 + 分片  
- MQ：消费者单点 → 多消费者并行 / 队列分区  
- 数据库：订单落库 → 分库分表 / 异步批量
- **架构**：加一层负载均衡（Nginx/LVS）→ 多实例服务 →   Redis 集群
- 面试话术："增大并发是**逐层扩容**——先加实例（水平扩展），  Redis 做集群，MQ 加消费者，数据库分库分表。每层瓶颈不同，  要逐层分析"
---
### Q10. 有哪些性能指标你关注过？
【追问1】QPS/RT/成功率？
【追问2】怎么采集？
【简答】关注 QPS（每秒请求）、RT（响应时间）、成功率、内存、CPU。QPS/RT 从 SkyWalking 和 Sentinel 看，资源用 docker stats。
【深挖】
- **应用指标**：QPS（Sentinel/SkyWalking）、  响应时间（SkyWalking P50/P95/P99）、成功率
- **资源指标**：内存（free/docker stats）、CPU（  top/docker stats）、磁盘
- **采集工具**：Sentinel 实时监控、SkyWalking   仪表盘、docker stats
- **面试话术**："我关注**应用指标**（QPS、RT、成功率，从   Sentinel/SkyWalking 看）  和**资源指标**（内存、  CPU、磁盘，从 docker stats 看），  这是压测和调优的依据"
