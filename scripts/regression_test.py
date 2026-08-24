#!/usr/bin/env python3
"""
N4 Monitor - Automated Regression Test Suite
Tests backend RBAC permissions, user lifecycle, authentication, security interception,
and core monitoring/alarm APIs.

Usage:
    python scripts/regression_test.py [--base-url http://localhost:8088/api] [--username admin] [--password admin123]
"""

import argparse
import json
import sys
import urllib.error
import urllib.parse
import urllib.request


def make_request(base_url, path, method="GET", data=None, token=None):
    url = f"{base_url}{path}"
    headers = {
        "Content-Type": "application/json",
        "Accept": "application/json",
    }
    if token:
        headers["Authorization"] = f"Bearer {token}"

    encoded_data = json.dumps(data).encode("utf-8") if data is not None else None
    req = urllib.request.Request(
        url, data=encoded_data, headers=headers, method=method
    )

    try:
        with urllib.request.urlopen(req, timeout=10) as response:
            status = response.status
            body = response.read().decode("utf-8")
            try:
                parsed = json.loads(body)
                if isinstance(parsed, dict) and "data" in parsed:
                    return status, parsed["data"]
            except Exception:
                parsed = body
            return status, parsed
    except urllib.error.HTTPError as e:
        body = e.read().decode("utf-8")
        try:
            parsed = json.loads(body)
            if isinstance(parsed, dict) and "data" in parsed:
                return e.code, parsed["data"]
        except Exception:
            parsed = body
        return e.code, parsed
    except Exception as e:
        return 0, str(e)


def run_regression_tests(base_url, username, password):
    results = []
    print("=" * 65)
    print("       N4-MONITOR AUTOMATED REGRESSION TEST SUITE")
    print("=" * 65)
    print(f"Target URL : {base_url}")
    print(f"Admin User : {username}")
    print("=" * 65)

    # -------------------------------------------------------------
    # 1. Authentication & Identity
    # -------------------------------------------------------------
    print("\n[1/5] Testing Authentication & Identity...")
    status, res = make_request(
        base_url,
        "/auth/login",
        method="POST",
        data={"username": username, "password": password},
    )
    if status != 200 or not isinstance(res, dict) or "token" not in res:
        print(f"[-] Login failed: status={status}, response={res}")
        sys.exit(1)

    token = res["token"]
    user_info = res.get("user", {})
    perms = user_info.get("permissions", [])
    print(
        f"  [+] Login successful. User: {user_info.get('username')}, Permissions: {len(perms)}"
    )
    results.append(
        (
            "Auth: Login (POST /api/auth/login)",
            "PASS",
            f"User: {user_info.get('username')}, Perms: {len(perms)}",
        )
    )

    status, res = make_request(base_url, "/auth/current-user", token=token)
    if status != 200 or not res:
        print(f"[-] Current user query failed: {res}")
        sys.exit(1)
    print(
        f"  [+] Current user verified. Real Name: {res.get('realName')}, Roles: {len(res.get('groups', []))}"
    )
    results.append(
        (
            "Auth: Info (GET /api/auth/current-user)",
            "PASS",
            f"RealName: {res.get('realName')}",
        )
    )

    # -------------------------------------------------------------
    # 2. RBAC Management Endpoints
    # -------------------------------------------------------------
    print("\n[2/5] Testing RBAC Management Endpoints...")
    # User pagination
    status, res = make_request(base_url, "/users?page=1&pageSize=10", token=token)
    assert status == 200 and "list" in res, f"User list failed: {res}"
    print(
        f"  [+] User list query returned {len(res['list'])} users, total={res['total']}"
    )
    results.append(
        ("RBAC: User List (GET /api/users)", "PASS", f"Total: {res['total']}")
    )

    # Role list
    status, res = make_request(base_url, "/groups", token=token)
    assert status == 200 and isinstance(res, list), f"Group list failed: {res}"
    print(
        f"  [+] Roles query returned {len(res)} roles: {[g.get('groupName') for g in res]}"
    )
    results.append(
        ("RBAC: Role List (GET /api/groups)", "PASS", f"Count: {len(res)}")
    )

    # Permission Tree
    status, res = make_request(base_url, "/permissions/tree", token=token)
    assert status == 200 and isinstance(res, list), f"Permission tree failed: {res}"
    print(f"  [+] Permission tree returned {len(res)} top-level menu nodes")
    results.append(
        (
            "RBAC: Permission Tree (GET /api/permissions/tree)",
            "PASS",
            f"Top nodes: {len(res)}",
        )
    )

    # Audit Logs
    status, res = make_request(base_url, "/logs?page=1&pageSize=10", token=token)
    assert status == 200 and "list" in res, f"Audit logs query failed: {res}"
    print(f"  [+] Audit logs query returned {len(res['list'])} records")
    results.append(
        ("RBAC: Audit Logs (GET /api/logs)", "PASS", f"Total: {res['total']}")
    )

    # -------------------------------------------------------------
    # 3. RBAC User Lifecycle CRUD
    # -------------------------------------------------------------
    print("\n[3/5] Testing RBAC User Lifecycle (Create -> Update -> Delete)...")
    import time
    test_username = f"auto_user_{int(time.time())}"
    test_user_payload = {
        "username": test_username,
        "password": "Password123!",
        "realName": "Automated Tester",
        "email": "auto_test@n4-monitor.local",
        "phone": "13900008888",
        "status": 1,
    }
    status, res = make_request(
        base_url, "/users", method="POST", data=test_user_payload, token=token
    )
    assert status == 200, f"User creation failed: {res}"
    print(f"  [+] Created test user: '{test_username}'")

    status, res = make_request(
        base_url, f"/users?username={test_username}", token=token
    )
    assert (
        status == 200 and len(res.get("list", [])) > 0
    ), "Newly created user not found in search"
    created_user = res["list"][0]
    user_id = created_user["userId"]

    update_payload = {
        "realName": "Automated Tester (Updated)",
        "email": "updated_auto@n4-monitor.local",
        "phone": "13900009999",
        "status": 1,
    }
    status, res = make_request(
        base_url, f"/users/{user_id}", method="PUT", data=update_payload, token=token
    )
    assert status == 200, f"User update failed: {res}"
    print(f"  [+] Updated user ID={user_id}")

    status, res = make_request(
        base_url, f"/users/{user_id}", method="DELETE", token=token
    )
    assert status == 200, f"User deletion failed: {res}"
    print(f"  [+] Deleted test user ID={user_id}")
    results.append(
        (
            "RBAC: Lifecycle (Create/Update/Delete)",
            "PASS",
            "Full CRUD lifecycle verified",
        )
    )

    # -------------------------------------------------------------
    # 4. Core Business Monitoring APIs
    # -------------------------------------------------------------
    print("\n[4/5] Testing Core Business Monitoring APIs...")
    # Device Templates
    status, res = make_request(base_url, "/device-templates/list", token=token)
    assert status == 200, f"Device templates query failed: {res}"
    tmpl_count = len(res) if isinstance(res, list) else 0
    print(f"  [+] /api/device-templates/list returned {tmpl_count} templates")
    results.append(
        (
            "Core: Device Templates (GET /api/device-templates/list)",
            "PASS",
            f"Templates: {tmpl_count}",
        )
    )

    # Navigation Tree
    status, res = make_request(base_url, "/nav/tree", token=token)
    assert status == 200, f"Navigation tree query failed: {res}"
    nav_count = len(res) if isinstance(res, list) else 0
    print(f"  [+] /api/nav/tree returned {nav_count} navigation nodes")
    results.append(
        ("Core: Nav Tree (GET /api/nav/tree)", "PASS", f"Nodes: {nav_count}")
    )

    # Active Alarms
    status, res = make_request(base_url, "/alarms/active", token=token)
    assert status == 200, f"Active alarms query failed: {res}"
    print("  [+] /api/alarms/active returned HTTP 200")
    results.append(
        ("Core: Active Alarms (GET /api/alarms/active)", "PASS", "HTTP 200 OK")
    )

    # Alarm History
    status, res = make_request(base_url, "/alarms/cleared", token=token)
    assert status == 200, f"Alarm history query failed: {res}"
    print("  [+] /api/alarms/cleared returned HTTP 200")
    results.append(
        ("Core: Alarm History (GET /api/alarms/cleared)", "PASS", "HTTP 200 OK")
    )

    # Numeric Alarm Config Rules
    status, res = make_request(
        base_url, "/alarm-configs/numeric/rules", token=token
    )
    assert status == 200, f"Numeric alarm rules query failed: {res}"
    print("  [+] /api/alarm-configs/numeric/rules returned HTTP 200")
    results.append(
        (
            "Core: Alarm Configs (GET /api/alarm-configs/numeric/rules)",
            "PASS",
            "HTTP 200 OK",
        )
    )

    # -------------------------------------------------------------
    # 5. Security Interception
    # -------------------------------------------------------------
    print("\n[5/5] Testing Security Interception...")
    status, res = make_request(base_url, "/users")  # No token
    assert status in (
        401,
        403,
    ), f"Unauthenticated request should return 401 or 403, got {status}"
    print(
        f"  [+] Unauthenticated request to /api/users correctly rejected with HTTP {status}"
    )
    results.append(
        (
            "Security: Unauthenticated Interception",
            "PASS",
            f"Rejected with HTTP {status}",
        )
    )

    # -------------------------------------------------------------
    # Final Summary Report
    # -------------------------------------------------------------
    print("\n" + "=" * 65)
    print("                 REGRESSION TEST REPORT SUMMARY")
    print("=" * 65)
    all_passed = True
    for name, stat, detail in results:
        status_str = f"[{stat}]"
        print(f"{status_str.ljust(8)} {name.ljust(42)} : {detail}")
        if stat != "PASS":
            all_passed = False
    print("=" * 65)
    if all_passed:
        print("RESULT: ALL 13 REGRESSION TEST CASES PASSED SUCCESSFULLY!")
        print("=" * 65)
        sys.exit(0)
    else:
        print("RESULT: SOME TEST CASES FAILED!")
        print("=" * 65)
        sys.exit(1)


if __name__ == "__main__":
    parser = argparse.ArgumentParser(
        description="N4-Monitor Automated Regression Tests"
    )
    parser.add_argument(
        "--base-url",
        default="http://localhost:8088/api",
        help="Backend API Base URL (default: http://localhost:8088/api)",
    )
    parser.add_argument(
        "--username",
        default="admin",
        help="Admin username (default: admin)",
    )
    parser.add_argument(
        "--password",
        default="admin123",
        help="Admin password (default: admin123)",
    )
    args = parser.parse_args()
    run_regression_tests(args.base_url, args.username, args.password)
