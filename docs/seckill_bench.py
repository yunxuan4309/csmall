#!/usr/bin/env python3
"""
CoolShark 秒杀压测脚本（服务器内部运行）— 100 用户版
用法: python3 seckill_bench.py <randCode>
示例: python3 seckill_bench.py 241467

说明:
- 压测前自动重置库存 + 清理购买标记（保证每次干净）
- 用 benchuser01~100 共 100 个用户，每个发 1 次 = 100 并发
- Sentinel QPS=10 限流 → 预期约 10 成功, 90 被限流
- 无重复购买干扰（每个用户只发 1 次）
"""
import sys
import json
import subprocess
import urllib.request
import concurrent.futures
import time

LOGIN_BASE = "http://localhost:10009/user/sso/login"
SECKILL_BASE = "http://localhost:10007"
SKU_ID = 5  # SPU2 对应的 skuId

def redis_cmd(*args):
    """执行 redis-cli 命令"""
    cmd = ["docker", "exec", "csmall-redis", "redis-cli"] + list(args)
    return subprocess.run(cmd, capture_output=True, text=True).stdout.strip()

def reset_stock():
    """重置库存为充足值"""
    # 重置 Redis 预扣库存
    redis_cmd("SET", f"mall:seckill:sku:stock:{SKU_ID}", "1000")
    print(f"✅ 库存已重置: mall:seckill:sku:stock:{SKU_ID} = 1000")

def clean_purchase_marks():
    """清理 100 个用户的购买标记"""
    keys = []
    for i in range(1, 101):
        keys.append(f"mall:seckill:reseckill:{SKU_ID}:{i}")
        keys.append(f"mall:seckill:ordered:{SKU_ID}:{i}")
    # 批量删除（分块避免命令过长）
    for j in range(0, len(keys), 50):
        chunk = keys[j:j+50]
        redis_cmd("DEL", *chunk)
    print(f"✅ 已清理 {len(keys)} 个购买标记 key")

def login(username):
    req = urllib.request.Request(
        LOGIN_BASE,
        data=json.dumps({"username": username, "password": "123456"}).encode('utf-8'),
        headers={"Content-Type": "application/json"},
        method="POST"
    )
    with urllib.request.urlopen(req, timeout=10) as resp:
        data = json.loads(resp.read().decode('utf-8'))
        return data["data"]["tokenValue"]

def main():
    if len(sys.argv) < 2:
        print("用法: python3 seckill_bench.py <randCode>")
        sys.exit(1)
    rand_code = sys.argv[1]

    # 重置库存 + 清理标记
    print("=== 压测前准备 ===")
    reset_stock()
    clean_purchase_marks()

    # 登录 100 个用户
    print("\n=== 登录 100 个压测用户 ===")
    users = [f"benchuser{i:02d}" for i in range(1, 101)]
    tokens = {}
    for u in users:
        try:
            tokens[u] = login(u)
        except Exception as e:
            print(f"❌ {u} 登录失败: {e}")
    if len(tokens) < 80:
        print(f"可用用户不足: {len(tokens)}/100，终止")
        sys.exit(1)
    print(f"✅ 登录成功: {len(tokens)}/100")

    url = f"{SECKILL_BASE}/seckill/{rand_code}"
    results = {"success": 0, "limited": 0, "purchased": 0, "other": 0, "errors": []}
    user_list = list(tokens.keys())

    def send(i):
        user = user_list[i]  # 每个用户发 1 次
        token = tokens[user]
        names = ["张伟", "李娜", "王芳", "赵敏", "刘洋", "陈静", "杨磊", "黄丽", "周涛", "吴霞"]
        contact = names[i % 10] + str(i % 10)
        body = {
            "spuId": 2,
            "contactName": contact,
            "mobilePhone": f"138{i:08d}"[:11],
            "telephone": None,
            "provinceCode": "510000",
            "provinceName": "四川省",
            "cityCode": "510100",
            "cityName": "成都市",
            "districtCode": "510104",
            "districtName": "锦江区",
            "streetCode": "510104001",
            "streetName": "测试街道",
            "detailedAddress": "测试路1号",
            "tag": None,
            "paymentType": 1,
            "state": 0,
            "rewardPoint": 0,
            "amountOfOriginalPrice": 5999.00,
            "amountOfFreight": 0,
            "amountOfDiscount": 0,
            "amountOfActualPay": 5999.00,
            "seckillOrderItemAddDTO": {
                "skuId": SKU_ID,
                "title": "华为 Mate 60 Pro 256G",
                "mainPicture": "spu_2_1.jpg",
                "price": 5999.00,
                "quantity": 1,
                "totalPrice": 5999.00,
                "spuId": 2
            }
        }
        req = urllib.request.Request(
            url,
            data=json.dumps(body).encode('utf-8'),
            headers={"Authorization": f"Bearer {token}", "Content-Type": "application/json"},
            method="POST"
        )
        try:
            with urllib.request.urlopen(req, timeout=15) as resp:
                data = json.loads(resp.read().decode('utf-8'))
                state = data.get("state")
                msg = data.get("message", "")
                if state == 200:
                    results["success"] += 1
                elif "服务器忙" in str(msg) or "限流" in str(msg) or "繁忙" in str(msg):
                    results["limited"] += 1
                elif "已经购买过" in str(msg) or "未支付" in str(msg):
                    results["purchased"] += 1
                else:
                    results["other"] += 1
                    results["errors"].append(f"req{i}: state={state} msg={msg[:60]}")
        except urllib.error.HTTPError as e:
            body = e.read().decode('utf-8', errors='ignore')
            if "服务器忙" in body or "限流" in body or "繁忙" in body or e.code == 429:
                results["limited"] += 1
            elif "已经购买过" in body:
                results["purchased"] += 1
            else:
                results["other"] += 1
                results["errors"].append(f"req{i}: HTTP {e.code} {body[:80]}")
        except Exception as e:
            results["other"] += 1
            results["errors"].append(f"req{i}: {str(e)[:80]}")

    total = 100
    print(f"\n=== 秒杀压测: {total} 并发（100个不同用户） → {url} ===")
    print(f"Sentinel 限流 QPS=10, 预期约 10 成功, 90 被限流")
    start = time.time()
    with concurrent.futures.ThreadPoolExecutor(max_workers=total) as executor:
        futures = [executor.submit(send, i) for i in range(total)]
        concurrent.futures.wait(futures)
    elapsed = time.time() - start

    print(f"\n=== 压测结果 ===")
    print(f"总请求:    {total}")
    print(f"成功下单:  {results['success']}")
    print(f"被限流:    {results['limited']}")
    print(f"已购买拦截: {results['purchased']}")
    print(f"其他:      {results['other']}")
    print(f"耗时:      {elapsed:.2f}s")
    print(f"QPS(实际): {total/elapsed:.1f}")
    if results["errors"]:
        print(f"\n其他请求明细 (前8条):")
        for e in results["errors"][:8]:
            print(f"  {e}")

if __name__ == "__main__":
    main()
