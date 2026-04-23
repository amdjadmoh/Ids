#!/usr/bin/env python
import json
import sys
from collections import Counter

from model import model


def main() -> int:
    args = sys.argv[1:]
    as_json = False
    if "--json" in args:
        as_json = True
        args.remove("--json")

    if len(args) != 1:
        print("Usage: python offline_predict.py [--json] <csv-path>", file=sys.stderr)
        return 1

    csv_path = args[0]
    detector = model()
    detector.load_data_csv(csv_path)
    results = detector.predict()
    counts = Counter(str(label) for label in results)

    if as_json:
        payload = {"rows": len(results), "counts": dict(counts)}
        print(json.dumps(payload))
        return 0

    print(f"rows={len(results)}")
    for label, count in sorted(counts.items()):
        print(f"{label}: {count}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
