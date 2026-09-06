# Kubernetes 与 ZooKeeper 使用评估

> **状态**: ✅ 评估完成（2026-08-22），**结论=项目未使用，无需部署**；
> 2026-08-26 补充 **k3s 轻量部署方案**（未来学习/实验用，仍不部署）
> **方法**: 服务器实测（容器/进程/端口/目录/命令/环境变量）+ 代码依赖分析
> **关联**: `docs/面试准备/基础知识速记.md`（§12/§13 简化版）、`docs/阿里云ECS服务器情况.md`

---

## 一、实测结论：两者均未使用

| 检查项 | 结果 |
|--------|------|
| Docker 容器 | ❌ 无 k8s/zookeeper/etcd 相关容器 |
| 宿主机进程 | ❌ 无 kubelet / kube-apiserver / k3s / zookeeper 进程 |
| 关键端口 | ❌ 2181（ZK）/ 2379（etcd）/ 10250（kubelet）/ 6443（apiserver）均无监听 |
| k8s 目录/命令 | ❌ 无 `/etc/kubernetes`、`/var/lib/kubelet`；无 `kubectl`/`kubelet`/`k3s` |
| 编排方式 | ✅ 纯 **Docker Compose**（Swarm: inactive） |
| 注册中心 | ✅ **Nacos**（Dubbo 3.x 注册中心，非 ZK） |

## 二、两处"看起来像"的误导点（排查时注意）

1. **SkyWalking OAP 镜像自带插件 jar**：classpath 里有 `zookeeper-3.5.7.jar`、`kubernetes-client-6.7.1.jar`、`cluster-kubernetes-plugin`、`cluster-zookeeper-plugin` 等——这是 OAP **内置的全套集群协调/配置插件库**（支持 zookeeper/k8s/etcd/consul），**只有配置了才启用**。实测 OAP 无 cluster 配置（默认 standalone），存储走 ES（`SW_STORAGE_ES_CLUSTER_NODES=elasticsearch:9200`），插件 jar 全部闲置。
2. **微服务 fat jar 内含 zookeeper 类**：`mall-ai.jar` 等——Dubbo 框架**内置支持** zookeeper/redis/nacos 多种注册中心实现，项目配置的是 Nacos，zk 只是框架依赖，未启用。

> 排查经验：**"jar 里有" ≠ "在运行"**，判断是否使用必须看进程/端口/配置，不能看 classpath。

## 三、知识要点（面试用）

### Kubernetes（K8s）
- **是什么**：容器编排平台——管理**多台服务器**上的容器，负责调度、自动扩缩容、自愈、服务发现
- **解决什么**：单机 Docker Compose 只能管一台机器；多机部署时需要"容器放哪台、挂了怎么办、流量大自动加实例"
- **为什么当前不用**：单机 4C16G 部署 21 容器，Compose 足够；K8s 自身要吃 1~2G 内存 + 运维复杂度，演示场景是负担
- **未来演进**：商业化多机部署时再引入（或先 Docker Swarm 过渡）

### ZooKeeper（ZK）
- **是什么**：分布式协调服务——注册中心 / 分布式锁 / 配置管理，**旧版 Dubbo（2.x）的经典注册中心**
- **为什么项目用 Nacos 不用 ZK**：
  | 维度 | Nacos | ZooKeeper |
  |------|-------|-----------|
  | 生态 | Spring Cloud Alibaba 全家桶 | 需自己集成 |
  | 功能 | 注册（AP）+ 配置（CP）+ 控制台 | 仅协调，配置要另搭 |
  | 一致性 | 注册 AP（可用性优先，服务发现场景合适） | CP（强一致，注册抖动时不可用） |
  | 运维 | 自带 Web 控制台、配置动态刷新 | 无控制台，命令行管理 |

## 四、面试讲解要点

- **"为什么不用 K8s"**："单机规模用 Docker Compose 足够；K8s 是为多机大规模设计的，我评估过——当前无多机、无流量压力，引入是负收益。商业化多机部署时是演进方向，但不会为用而用。"
- **"为什么用 Nacos 不用 ZooKeeper"**："体系选择——Spring Cloud Alibaba 生态 + 注册配置二合一 + 自带控制台；ZK 是旧 Dubbo 时代的方案，功能单一（只有协调），配置中心还要另搭，且 CP 模型在注册场景不如 AP 抗抖动。"

## 五、k3s 轻量部署方案（2026-08-26 补充，未来学习/实验用）

> **背景**：单机 4C16G 当前跑 21 容器（Compose），内存剩 ~2G。**当前仍不部署 K8s**（场景不匹配），但备好轻量方案，未来学习 K8s 或迁移实验时用。

### 为什么选 k3s（不选 minikube/kubeadm）
| 方式 | 内存 | 复杂度 | 场景 | 结论 |
|------|------|--------|------|------|
| **k3s** ✅ | ~500MB | 低 | 单机真实业务 + 可加节点成集群 | 推荐 |
| minikube | ~1G | 极低 | 本地学习 demo | 学习用 |
| kubeadm | 1-2G | 高 | 生产多机集群 | 多机才用 |

k3s 优势：Rancher 出品、一个二进制搞定控制平面+节点、
自带 etcd 替代（SQLite/嵌入式）、API 兼容 K8s、以后能加节点变集群。

### 安装步骤（k3s）
```bash
# ① 安装（一条命令）
curl -sfL https://get.k3s.io | sh -
# ② 验证
sudo k3s kubectl get nodes
# ③ 管理（k3s 自带 kubectl）
sudo k3s kubectl get pods -A
# ④ 外部 kubectl（可选）
#    把 /etc/rancher/k3s/k3s.yaml 的 kubeconfig 拷到本地
```

### K8s 架构名词（面试要懂）
```
控制平面（大脑）：
  API Server  = 操作入口（kubectl 调它）
  etcd        = 状态数据库（存所有配置/运行状态，最需要维护，
                高可用要 3/5 副本，挂了 K8s 全瘫）
  Scheduler   = 调度员（新容器放哪台机器）
  Controller Manager = 监工（实际状态 vs 期望状态）
工作节点（手脚）：
  kubelet     = 每台机器的代理（听指挥启停容器）
  kube-proxy  = 网络代理（服务发现/负载均衡）
```

### 单机 K8s 的诚实边界
- k3s 也要 ~500MB 内存 → 当前服务器（剩 2G）加装后更紧
- K8s 价值（自愈/扩缩容/滚动更新）在单机上发挥不出来
- 现有 Compose 稳定运行 → **现在不装是对的**
- 装 k3s 的时机：学习 K8s 实操 / 模拟多机集群实验 / 迁移演练

### 面试话术
"我评估过 K8s：控制平面（API Server/etcd/Scheduler/Controller）
+ 工作节点（kubelet/kube-proxy），etcd 是最需要维护的——
分布式强一致数据库，高可用要 3/5 副本，挂了 K8s 全瘫。
真要装我会选 k3s（轻量版 ~500MB，一个二进制搞定），
minikube 适合学习，kubeadm 是生产多机标准。但我现在不装：
单机 4C16G 内存剩 2G，K8s 价值单机用不上，Compose 已够——
这是场景匹配的选型，不是不会装。"

## 六、决策记录

| 日期 | 事项 |
|------|------|
| 2026-08-22 | 服务器实测确认：**K8s 与 ZooKeeper 均未使用**；两者仅以框架依赖 jar 形式存在于镜像中。无需部署、无后续动作 |
| 2026-08-26 | 补充 **k3s 轻量部署方案**（学习/实验备用）。**仍不部署**：单机场景不匹配 + 内存紧张；未来学习 K8s 实操或迁移演练时用 k3s |
| 待定 | 若未来商业化多机部署，评估引入 K8s（k3s 起步或 kubeadm）；注册中心继续 Nacos |

---

**关联文档**：`docs/面试准备/基础知识速记.md`（§12 Kubernetes、§13 ZooKeeper 简化速记）
