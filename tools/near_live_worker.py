#!/usr/bin/env python3
import argparse
import json
import shutil
import subprocess
import sys
import time
from pathlib import Path
from urllib import request


DEFAULT_COUNTS = {
    "Bot": 0,
    "DoS attack": 0,
    "Brute Force": 0,
    "DDoS attacks": 0,
    "0": 0,
}


def run(command, cwd=None, check=True, capture_output=False):
    return subprocess.run(
        command,
        cwd=cwd,
        check=check,
        text=True,
        capture_output=capture_output,
    )


def read_state(path: Path):
    if not path.exists():
        return {"status": "off"}
    try:
        return json.loads(path.read_text(encoding="utf-8"))
    except json.JSONDecodeError:
        return {"status": "off"}


def write_json(path: Path, payload):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(payload), encoding="utf-8")


def post_json(url: str, payload):
    body = json.dumps(payload).encode("utf-8")
    req = request.Request(
        url,
        data=body,
        headers={"Content-Type": "application/json"},
        method="POST",
    )
    with request.urlopen(req, timeout=15) as response:
        response.read()


def score_csv(repo_root: Path, csv_path: Path):
    relative_csv = f"/runtime/{csv_path.name}"
    command = [
        "docker",
        "compose",
        "exec",
        "-T",
        "model",
        "sh",
        "-lc",
        f"cd /app && python offline_predict.py --json {relative_csv}",
    ]
    last_error = None
    for _ in range(3):
        result = subprocess.run(
            command,
            cwd=repo_root,
            check=False,
            text=True,
            capture_output=True,
        )
        if result.returncode == 0:
            return json.loads(result.stdout.strip())
        last_error = result.stderr.strip() or result.stdout.strip() or f"exit {result.returncode}"
        time.sleep(2)
    raise RuntimeError(f"Model scoring failed: {last_error}")


def build_counts(prediction_payload):
    counts = dict(DEFAULT_COUNTS)
    for key, value in prediction_payload.get("counts", {}).items():
        counts[key] = int(value)
    return counts


def process_chunk(repo_root: Path, cic_dir: Path, pcap_path: Path, output_dir: Path, runtime_csv: Path):
    run(
        ["sh", "run_offline_pcap.sh", str(pcap_path), str(output_dir)],
        cwd=cic_dir,
    )
    csv_name = f"{pcap_path.name}_Flow.csv"
    csv_path = output_dir / csv_name
    if not csv_path.exists():
        raise FileNotFoundError(f"Expected CSV was not created: {csv_path}")
    shutil.copy2(csv_path, runtime_csv)
    prediction_payload = score_csv(repo_root, runtime_csv)
    counts = build_counts(prediction_payload)
    write_json(repo_root / "runtime" / "near_live_last_result.json", counts)
    post_json("http://127.0.0.1:7777/post-predict", counts)
    return prediction_payload


def main():
    parser = argparse.ArgumentParser(description="Near-live IDS worker")
    parser.add_argument("--interface", required=True, help="Network interface to capture")
    parser.add_argument("--chunk-seconds", type=int, default=20, help="Seconds per capture chunk")
    parser.add_argument("--repo-root", default=str(Path(__file__).resolve().parents[1]), help="Repository root")
    args = parser.parse_args()

    repo_root = Path(args.repo_root).resolve()
    cic_dir = repo_root / "CICFlowMeter-master"
    runtime_dir = repo_root / "runtime"
    state_path = runtime_dir / "near_live_state.json"
    runtime_csv = runtime_dir / "near_live.csv"
    pcap_dir = repo_root / "tmp" / "near_live"
    output_dir = cic_dir / "data" / "offline" / "near_live"
    pcap_dir.mkdir(parents=True, exist_ok=True)
    output_dir.mkdir(parents=True, exist_ok=True)
    runtime_dir.mkdir(parents=True, exist_ok=True)

    print(f"Near-live worker watching {state_path} on interface {args.interface}")
    while True:
        state = read_state(state_path)
        if state.get("status") != "on":
            time.sleep(2)
            continue

        timestamp = time.strftime("%Y%m%d-%H%M%S")
        pcap_path = pcap_dir / f"chunk-{timestamp}.pcap"
        print(f"Capturing {pcap_path.name} for {args.chunk_seconds}s")
        run(
            [
                "timeout",
                str(args.chunk_seconds),
                "tcpdump",
                "-i",
                args.interface,
                "-U",
                "-w",
                str(pcap_path),
            ],
            check=False,
        )

        if not pcap_path.exists() or pcap_path.stat().st_size == 0:
            print(f"Skipping empty capture {pcap_path}")
            time.sleep(1)
            continue

        try:
            prediction_payload = process_chunk(repo_root, cic_dir, pcap_path, output_dir, runtime_csv)
            print(f"Processed {pcap_path.name}: {prediction_payload}")
        except Exception as exc:
            print(f"Chunk processing failed: {exc}", file=sys.stderr)
        time.sleep(1)


if __name__ == "__main__":
    raise SystemExit(main())
