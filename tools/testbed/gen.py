#!/usr/bin/env python3
"""Generates a local sing-box *server* config covering every protocol/transport WayTun supports,
plus the matching client share links (links.txt) and a WireGuard client config.

Usage: gen.py <workdir> <host-lan-ip> <path-to-sing-box-binary>
Then:  <sing-box> run -c <workdir>/server.json
"""
import base64, json, os, subprocess, sys, urllib.parse as up

W, HOST, B = sys.argv[1], sys.argv[2], sys.argv[3]
os.makedirs(W, exist_ok=True)


def run(*args):
    return subprocess.check_output([B, *args], text=True).strip()


def kv(text):
    return {k.strip(): v.strip() for k, v in (l.split(":", 1) for l in text.splitlines() if ":" in l)}


def need(name, make):
    p = os.path.join(W, name)
    if not os.path.exists(p):
        open(p, "w").write(make())
    return open(p).read()


tls_pair = need("tls.txt", lambda: run("generate", "tls-keypair", "test.local"))
open(f"{W}/key.pem", "w").write(tls_pair[tls_pair.index("-----BEGIN PRIVATE KEY"):tls_pair.index("-----END PRIVATE KEY-----") + 25] + "\n")
open(f"{W}/cert.pem", "w").write(tls_pair[tls_pair.index("-----BEGIN CERTIFICATE"):tls_pair.index("-----END CERTIFICATE-----") + 25] + "\n")
reality = kv(need("reality.txt", lambda: run("generate", "reality-keypair")))
wgs = kv(need("wg-server.txt", lambda: run("generate", "wg-keypair")))
wgc = kv(need("wg-client.txt", lambda: run("generate", "wg-keypair")))
uuid = need("uuid.txt", lambda: run("generate", "uuid")).strip()
ss2022 = need("ss2022.key", lambda: next(k for k in (run("generate", "rand", "--base64", "32") for _ in range(200)) if "+" in k)).strip()

tls = lambda alpn=None: dict(enabled=True, server_name="test.local", certificate_path=f"{W}/cert.pem", key_path=f"{W}/key.pem", **({"alpn": alpn} if alpn else {}))
vless = lambda tag, port, flow=None, **kw: dict(type="vless", tag=tag, listen="0.0.0.0", listen_port=port, users=[dict(uuid=uuid, **({"flow": flow} if flow else {}))], **kw)

inbounds = [
    vless("vless-tcp", 20001),
    vless("vless-ws-tls", 20002, tls=tls(), transport=dict(type="ws", path="/ws", max_early_data=2048, early_data_header_name="Sec-WebSocket-Protocol")),
    vless("vless-grpc-tls", 20003, tls=tls(["h2"]), transport=dict(type="grpc", service_name="gun")),
    vless("vless-httpupgrade", 20004, transport=dict(type="httpupgrade", path="/hu")),
    vless("vless-h2-tls", 20005, tls=tls(["h2"]), transport=dict(type="http", path="/h2")),
    vless("vless-quic-tls", 20006, tls=tls(["h3"]), transport=dict(type="quic")),
    vless("vless-reality", 20007, flow="xtls-rprx-vision", tls=dict(enabled=True, server_name="www.microsoft.com", reality=dict(enabled=True, handshake=dict(server="www.microsoft.com", server_port=443), private_key=reality["PrivateKey"], short_id=[""]))),
    dict(type="vmess", tag="vmess-ws", listen="0.0.0.0", listen_port=20008, users=[dict(uuid=uuid, alterId=0)], transport=dict(type="ws", path="/vm")),
    dict(type="vmess", tag="vmess-tcp-tls", listen="0.0.0.0", listen_port=20009, users=[dict(uuid=uuid, alterId=0)], tls=tls()),
    dict(type="trojan", tag="trojan-tls", listen="0.0.0.0", listen_port=20010, users=[dict(password="trojanpass")], tls=tls()),
    dict(type="trojan", tag="trojan-grpc-tls", listen="0.0.0.0", listen_port=20011, users=[dict(password="trojanpass")], tls=tls(["h2"]), transport=dict(type="grpc", service_name="tgun")),
    dict(type="shadowsocks", tag="ss-aes", listen="0.0.0.0", listen_port=20013, method="aes-256-gcm", password="ss-test-pass"),
    dict(type="shadowsocks", tag="ss-2022", listen="0.0.0.0", listen_port=20014, method="2022-blake3-aes-256-gcm", password=ss2022),
    dict(type="hysteria2", tag="hy2-plain", listen="0.0.0.0", listen_port=20015, users=[dict(password="hy2pass")], tls=tls(["h3"])),
    dict(type="tuic", tag="tuic", listen="0.0.0.0", listen_port=20016, users=[dict(uuid=uuid, password="tuicpass")], congestion_control="bbr", tls=tls(["h3"])),
    dict(type="hysteria2", tag="hy2-obfs", listen="0.0.0.0", listen_port=20017, users=[dict(password="hy2pass")], obfs=dict(type="salamander", password="obfspass"), tls=tls(["h3"])),
    dict(type="hysteria", tag="hy1", listen="0.0.0.0", listen_port=20018, up_mbps=100, down_mbps=100, users=[dict(auth_str="hy1auth")], tls=tls(["h3"])),
]
cfg = dict(
    log=dict(level="info", timestamp=True),
    inbounds=inbounds,
    endpoints=[dict(type="wireguard", tag="wg-server", system=False, mtu=1408, address=["10.66.0.1/24"], private_key=wgs["PrivateKey"], listen_port=20012,
                    peers=[dict(public_key=wgc["PublicKey"], allowed_ips=["10.66.0.2/32"])])],
    outbounds=[dict(type="direct", tag="direct")],
    route=dict(final="direct"),
)
json.dump(cfg, open(f"{W}/server.json", "w"), indent=1)

q = lambda **p: up.urlencode(p, quote_via=up.quote)
ins = dict(security="tls", sni="test.local", allowInsecure="1")
ss_ui = base64.urlsafe_b64encode(b"aes-256-gcm:ss-test-pass").decode().rstrip("=")
links = [
    ("vless-tcp", f"vless://{uuid}@{HOST}:20001?{q(type='tcp', security='none')}#T01%20VLESS%20TCP"),
    ("vless-ws-tls", f"vless://{uuid}@{HOST}:20002?{q(type='ws', path='/ws?ed=2048', host='test.local', **ins)}#T02%20VLESS%20WS%20TLS"),
    ("vless-grpc-tls", f"vless://{uuid}@{HOST}:20003?{q(type='grpc', serviceName='gun', **ins)}#T03%20VLESS%20gRPC%20TLS"),
    ("vless-httpupgrade", f"vless://{uuid}@{HOST}:20004?{q(type='httpupgrade', path='/hu', host='test.local', security='none')}#T04%20VLESS%20HTTPUpgrade"),
    ("vless-h2-tls", f"vless://{uuid}@{HOST}:20005?{q(type='http', path='/h2', host='test.local', **ins)}#T05%20VLESS%20H2%20TLS"),
    ("vless-quic-tls", f"vless://{uuid}@{HOST}:20006?{q(type='quic', **ins)}#T06%20VLESS%20QUIC%20TLS"),
    ("vless-reality", f"vless://{uuid}@{HOST}:20007?{q(type='tcp', security='reality', sni='www.microsoft.com', fp='chrome', pbk=reality['PublicKey'], flow='xtls-rprx-vision')}#T07%20VLESS%20REALITY%20Vision"),
    ("vmess-ws", "vmess://" + base64.b64encode(json.dumps(dict(v="2", ps="T08 VMess WS", add=HOST, port="20008", id=uuid, aid="0", net="ws", path="/vm", host="test.local", tls="")).encode()).decode()),
    ("vmess-tcp-tls", "vmess://" + base64.b64encode(json.dumps(dict(v="2", ps="T09 VMess TCP TLS", add=HOST, port="20009", id=uuid, aid="0", net="tcp", tls="tls", sni="test.local", allowInsecure="1")).encode()).decode()),
    ("trojan-tls", f"trojan://trojanpass@{HOST}:20010?{q(sni='test.local', allowInsecure='1')}#T10%20Trojan%20TLS"),
    ("trojan-grpc-tls", f"trojan://trojanpass@{HOST}:20011?{q(type='grpc', serviceName='tgun', **ins)}#T11%20Trojan%20gRPC%20TLS"),
    ("ss-aes", f"ss://{ss_ui}@{HOST}:20013#T12%20SS%20AES"),
    ("ss-2022", f"ss://2022-blake3-aes-256-gcm:{ss2022}@{HOST}:20014#T13%20SS%202022"),  # raw '+' on purpose
    ("hy2-plain", f"hysteria2://hy2pass@{HOST}:20015/?sni=test.local&insecure=1#T14%20Hysteria2"),
    ("hy2-obfs", f"hy2://hy2pass@{HOST}:20017/?sni=test.local&insecure=1&obfs=salamander&obfs-password=obfspass#T15%20Hysteria2%20obfs"),
    ("tuic", f"tuic://{uuid}:tuicpass@{HOST}:20016?congestion_control=bbr&udp_relay_mode=native&alpn=h3&sni=test.local&allow_insecure=1#T16%20TUIC"),
    ("hy1", f"hysteria://{HOST}:20018?auth=hy1auth&peer=test.local&insecure=1&upmbps=50&downmbps=100&alpn=h3#T17%20Hysteria1"),
]
open(f"{W}/links_all.txt", "w").write("\n".join(l for _, l in links))
open(f"{W}/links.txt", "w").write("\n".join(l for _, l in links))
open(f"{W}/tags.txt", "w").write("\n".join(t for t, _ in links))
open(f"{W}/wg-client.conf", "w").write(f"""[Interface]
PrivateKey = {wgc['PrivateKey']}
Address = 10.66.0.2/32
DNS = 1.1.1.1

[Peer]
PublicKey = {wgs['PublicKey']}
AllowedIPs = 0.0.0.0/0
Endpoint = {HOST}:20012
PersistentKeepalive = 25
""")
print(f"{len(links)} links, server.json written to {W}")
