#!/usr/bin/env python3
"""Fake subscription panel: serves <workdir>/links.txt base64-encoded with Happ-style headers and
logs the request headers it receives to <workdir>/requests.log.

Usage: subserver.py <workdir> [port]   (reach it from the phone with `adb reverse tcp:PORT tcp:PORT`)
"""
import base64, http.server, json, sys, urllib.parse

W = sys.argv[1]
PORT = int(sys.argv[2]) if len(sys.argv) > 2 else 8088


def xray_json(links):
    """vless:// links -> a Happ-style JSON subscription (array of full Xray configs)."""
    out = []
    for link in links:
        if not link.startswith("vless://"):
            continue
        u = urllib.parse.urlsplit(link)
        q = dict(urllib.parse.parse_qsl(u.query))
        stream = {"network": q.get("type", "tcp"), "security": q.get("security", "none")}
        if stream["security"] == "reality":
            stream["realitySettings"] = {"serverName": q.get("sni", ""), "fingerprint": q.get("fp", "chrome"),
                                         "publicKey": q.get("pbk", ""), "shortId": q.get("sid", "")}
        elif stream["security"] == "tls":
            stream["tlsSettings"] = {"serverName": q.get("sni", ""), "allowInsecure": q.get("allowInsecure") == "1"}
        if stream["network"] == "ws":
            stream["wsSettings"] = {"path": q.get("path", "/"), "headers": {"Host": q.get("host", "")}}
        out.append({
            "remarks": urllib.parse.unquote(u.fragment) or "json-entry",
            "outbounds": [{
                "protocol": "vless", "tag": "proxy",
                "settings": {"vnext": [{"address": u.hostname, "port": u.port, "users": [
                    {"id": urllib.parse.unquote(u.username), "encryption": "none", "flow": q.get("flow", "")}]}]},
                "streamSettings": stream}]})
    return out


class Handler(http.server.BaseHTTPRequestHandler):
    def do_GET(self):
        with open(f"{W}/requests.log", "a") as f:
            f.write(json.dumps({"path": self.path, "headers": dict(self.headers)}) + "\n")
        links_text = open(f"{W}/links.txt").read()
        if self.path.startswith("/json"):
            body = json.dumps(xray_json(links_text.splitlines()), ensure_ascii=False).encode()
        else:
            body = base64.b64encode(links_text.encode())
        self.send_response(200)
        self.send_header("Content-Type", "text/plain")
        self.send_header("profile-title", "base64:" + base64.b64encode("WayTest VPN".encode()).decode())
        self.send_header("subscription-userinfo", "upload=1073741824; download=5368709120; total=107374182400; expire=1798761600")
        self.send_header("profile-update-interval", "1")
        self.send_header("announce", "base64:" + base64.b64encode("Тестовое объявление панели".encode()).decode())
        self.send_header("profile-description", "base64:" + base64.b64encode("Описание подписки: тестовый тариф.\nВторая строка с подробностями и правилами использования.".encode()).decode())
        self.send_header("announce-url", "https://example.com/news")
        self.send_header("support-email", "support@example.com")
        self.send_header("support-url", "https://t.me/waytun_test")
        self.send_header("profile-web-page-url", "https://example.com/account")
        self.end_headers()
        self.wfile.write(body)

    def log_message(self, *args):
        pass


http.server.HTTPServer(("127.0.0.1", PORT), Handler).serve_forever()
