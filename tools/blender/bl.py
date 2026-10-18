"""Send Python to a running Blender (official Blender Lab MCP add-on) and print the result.

The add-on speaks NUL-terminated JSON, one request per connection:
    -> {"type": "execute", "code": "...", "strict_json": false}\0
    <- {"status": "ok"|"error", "result"|"message": ..., "stdout"?: ..., "stderr"?: ...}\0

Usage:
    python tools/blender/bl.py [--port 9877] -c "import bpy; result = {'n': len(bpy.data.objects)}"
    python tools/blender/bl.py [--port 9877] script.py [--set key=value ...]

`--set` values are injected as a dict named ARGS before the script runs.
The Cabin Mod Blender runs on 9877 so it never collides with other sessions on 9876.
"""
import argparse
import json
import socket
import sys


def send(code, host="localhost", port=9877, timeout=300.0):
    payload = (json.dumps({"type": "execute", "code": code, "strict_json": False}) + "\0").encode("utf-8")
    with socket.create_connection((host, port), timeout=timeout) as sock:
        sock.sendall(payload)
        buf = bytearray()
        while b"\0" not in buf:
            chunk = sock.recv(65536)
            if not chunk:
                break
            buf.extend(chunk)
    end = buf.find(b"\0")
    return json.loads(bytes(buf if end < 0 else buf[:end]).decode("utf-8"))


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("script", nargs="?")
    ap.add_argument("-c", "--code")
    ap.add_argument("--host", default="localhost")
    ap.add_argument("--port", type=int, default=9877)
    ap.add_argument("--set", action="append", default=[], metavar="KEY=VALUE")
    args = ap.parse_args()

    if args.code:
        code = args.code
    elif args.script:
        with open(args.script, encoding="utf-8") as f:
            code = f.read()
    else:
        ap.error("give a script path or -c CODE")

    params = dict(kv.split("=", 1) for kv in args.set)
    code = f"ARGS = {params!r}\n" + code

    resp = send(code, args.host, args.port)
    for stream in ("stdout", "stderr"):
        if resp.get(stream):
            print(resp[stream], file=sys.stderr if stream == "stderr" else sys.stdout, end="")
    if resp.get("status") != "ok":
        print(resp.get("message", resp), file=sys.stderr)
        sys.exit(1)
    print(json.dumps(resp.get("result"), indent=2))


if __name__ == "__main__":
    main()
