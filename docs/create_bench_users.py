#!/usr/bin/env python3
"""
批量注册 100 个压测用户
调用: POST /ums/user/register
用户名: benchuser01 ~ benchuser100 (字母开头，4-16位)
密码: 123456 (4-16位可打印字符)
手机: 13900000001 ~ 13900000100
邮箱: benchuser01@test.com ...
"""
import json
import urllib.request
import concurrent.futures

BASE = "http://localhost:10006/ums/user/register"
REGISTER_URL = "http://localhost:10006" + "/ums/user/register"

def register(i):
    uname = f"benchuser{i:02d}"  # benchuser01 ~ benchuser100
    body = {
        "username": uname,
        "nickname": f"压测{i:02d}",
        "email": f"{uname}@test.com",
        "phone": f"139{i:08d}"[:11],
        "password": "123456",
        "ackPassword": "123456"
    }
    req = urllib.request.Request(
        REGISTER_URL,
        data=json.dumps(body).encode('utf-8'),
        headers={"Content-Type": "application/json"},
        method="POST"
    )
    try:
        with urllib.request.urlopen(req, timeout=10) as resp:
            data = json.loads(resp.read().decode('utf-8'))
            state = data.get("state")
            msg = data.get("message", "")
            return (i, uname, "成功" if state == 200 else f"失败: {msg[:40]}")
    except Exception as e:
        return (i, uname, f"异常: {str(e)[:40]}")

def main():
    print("=== 批量注册 100 个压测用户 ===")
    success = 0
    fail = []
    with concurrent.futures.ThreadPoolExecutor(max_workers=10) as executor:
        futures = [executor.submit(register, i) for i in range(1, 101)]
        for f in concurrent.futures.as_completed(futures):
            i, uname, result = f.result()
            if "成功" in result:
                success += 1
            else:
                fail.append(result)
            if i % 20 == 0:
                print(f"  已处理 {i} 个...")

    print(f"\n=== 注册结果 ===")
    print(f"成功: {success}")
    print(f"失败: {len(fail)}")
    if fail:
        for f in fail[:5]:
            print(f"  {f}")

if __name__ == "__main__":
    main()
