#!/usr/bin/env python3
"""登录获取Token — 从命令行参数或环境变量读取手机号和验证码"""
import urllib.request
import json
import os
import sys

def login(phone, code):
    url = os.environ.get("API_BASE_URL", "http://localhost:8382") + "/api/auth/phone/login"
    data = json.dumps({"phone": phone, "code": code}).encode('utf-8')
    req = urllib.request.Request(url, data=data, headers={"Content-Type": "application/json"}, method="POST")
    try:
        with urllib.request.urlopen(req, timeout=10) as resp:
            result = resp.read().decode('utf-8')
            print(f"登录响应: {result}")
            return json.loads(result)
    except Exception as e:
        print(f"登录失败: {e}")
        return None

if __name__ == "__main__":
    phone = sys.argv[1] if len(sys.argv) > 1 else os.environ.get("TEST_PHONE", "")
    code = sys.argv[2] if len(sys.argv) > 2 else os.environ.get("TEST_CODE", "")
    if not phone or not code:
        print("用法: python do_login.py <phone> <code>")
        print("或设置环境变量: TEST_PHONE, TEST_CODE")
        sys.exit(1)
    print("="*50)
    print("步骤2: 登录获取Token")
    print("="*50)
    result = login(phone, code)
    if result and result.get('success'):
        token = result.get('data', {}).get('token')
        print(f"\n获取到Token: {token}")
        print("\n现在可以运行WebSocket测试脚本更新Token后进行测试")
    else:
        print("登录失败")