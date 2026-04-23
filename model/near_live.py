import json
import os
from pathlib import Path
from typing import Dict


DEFAULT_COUNTS = {
    "Bot": 0,
    "DoS attack": 0,
    "Brute Force": 0,
    "DDoS attacks": 0,
    "0": 0,
}


class NearLiveController:
    def __init__(self, runtime_dir: str = None):
        base_dir = Path(runtime_dir or os.getenv("RUNTIME_DIR", "./runtime"))
        self.runtime_dir = base_dir
        self.runtime_dir.mkdir(parents=True, exist_ok=True)
        self.state_path = self.runtime_dir / "near_live_state.json"
        self.last_result_path = self.runtime_dir / "near_live_last_result.json"
        if not self.state_path.exists():
            self.write_state("off")

    def write_state(self, status: str) -> Dict[str, str]:
        payload = {"status": status}
        self.state_path.write_text(json.dumps(payload), encoding="utf-8")
        return payload

    def read_state(self) -> Dict[str, str]:
        if not self.state_path.exists():
            return self.write_state("off")
        try:
            return json.loads(self.state_path.read_text(encoding="utf-8"))
        except json.JSONDecodeError:
            return self.write_state("off")

    def write_result(self, result: Dict[str, int]) -> None:
        self.last_result_path.write_text(json.dumps(result), encoding="utf-8")

    def read_result(self) -> Dict[str, int]:
        if not self.last_result_path.exists():
            return dict(DEFAULT_COUNTS)
        try:
            return json.loads(self.last_result_path.read_text(encoding="utf-8"))
        except json.JSONDecodeError:
            return dict(DEFAULT_COUNTS)
