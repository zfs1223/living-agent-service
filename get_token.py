#!/usr/bin/env python3
"""获取新Token并测试WebSocket — 从命令行参数或环境变量读取手机号"""

import urllib.request
import urllib.error
import json
import os
import sys

API_BASE = os.environ.get("API_BASE_URL", "http://localhost:8382")

# 发送验证码
def send_sms(phone):
    url = f"{API_BASE}/api/auth/sms/send"
    data = json.dumps({"phone": phone}).encode('utf-8')
    req = urllib.request.Request(url, data=data, headers={
        "Content-Type": "application/json"
    }, method="POST")
    try:
        with urllib.request.urlopen(req) as response:
            result = response.read().decode('utf-8')
            print(f"发送验证码响应: {result}")
            return json.loads(result)
    except urllib.error.HTTPError as e:
        print(f"发送验证码失败: {e.code} - {e.read().decode('utf-8')}")
        return None

# 登录获取Token
def login(phone, code):
    url = f"{API_BASE}/api/auth/phone/login"
    data = json.dumps({"phone": phone, "code": code}).encode('utf-8')
    req = urllib.request.Request(url, data=data, headers={
        "Content-Type": "application/json"
    }, method="POST")
    try:
        with urllib.request.urlopen(req, timeout=10) as response:
            result = response.read().decode('utf-8')
            print(f"登录响应: {result}")
            return json.loads(result)
    except urllib.error.HTTPError as e:
        print(f"登录失败: {e.code} - {e.read().decode('utf-8')}")
        return None

# 验证Token
def verify_token(token):
    url = f"{API_BASE}/api/auth/user"
    req = urllib.request.Request(url, headers={
        "Authorization": f"Bearer {token}"
    })
    try:
        with urllib.request.urlopen(req, timeout=10) as response:
            result = response.read().decode('utf-8')
            print(f"验证Token响应: {result}")
            return json.loads(result)
    except urllib.error.HTTPError as e:
        print(f"验证Token失败: {e.code} - {e.read().decode('utf-8')}")
        return None

if __name__ == "__main__":
    phone = sys.argv[1] if len(sys.argv) > 1 else os.environ.get("TEST_PHONE", "")
    if not phone:
        print("用法: python get_token.py <phone>")
        print("或设置环境变量: TEST_PHONE")
        sys.exit(1)
    print("="*50)
    print("步骤1: 发送验证码")
    print("="*50)
    send_result = send_sms(phone)
    if not send_result or not send_result.get('success'):
        print("发送验证码失败，需要从Docker日志获取验证码")
    else:
        print("请从Docker日志获取验证码，然后输入:")
        code = input("请输入验证码: ")
        if code:
            login_result = login(phone, code)
            if login_result and login_result.get('success'):
                token = login_result.get('data', {}).get('token')
                print(f"\n获取到Token: {token}")
                print("\n验证Token...")
                verify_token(token)
    print("\n注意: 请从Docker日志中查找验证码，格式如下:")
    print("docker logs --tail 100 living-agent-service 2>&1 | findstr \"code\"")

    print("\n" + "="*50)
    print("请手动从Docker日志获取验证码后，运行 test_login_and_websocket.py")
    print("="*50)