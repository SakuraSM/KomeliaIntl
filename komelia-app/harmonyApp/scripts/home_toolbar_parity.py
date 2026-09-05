#!/usr/bin/env python3
"""Gate Android/HarmonyOS compact home toolbar geometry from UI layout dumps."""

from __future__ import annotations

import argparse
import json
import math
import re
import sys
import xml.etree.ElementTree as ElementTree
from dataclasses import dataclass
from pathlib import Path
from typing import Any, Iterable


BOUNDS_PATTERN = re.compile(r"\[(\d+),(\d+)\]\[(\d+),(\d+)\]")


@dataclass(frozen=True)
class Bounds:
    left: float
    top: float
    right: float
    bottom: float

    @classmethod
    def parse(cls, value: str) -> "Bounds":
        match = BOUNDS_PATTERN.fullmatch(value)
        if match is None:
            raise ValueError(f"invalid bounds: {value!r}")
        return cls(*(float(match.group(index)) for index in range(1, 5)))

    @property
    def width(self) -> float:
        return self.right - self.left

    @property
    def height(self) -> float:
        return self.bottom - self.top

    def scaled(self, scale: float) -> "Bounds":
        return Bounds(self.left / scale, self.top / scale, self.right / scale, self.bottom / scale)


def android_descendant_texts(node: ElementTree.Element) -> set[str]:
    return {child.attrib.get("text", "") for child in node.iter() if child.attrib.get("text", "")}


def android_button_for_label(root: ElementTree.Element, label: str) -> Bounds:
    candidates: list[Bounds] = []
    for node in root.iter():
        if node.attrib.get("clickable") != "true" and node.attrib.get("checkable") != "true":
            continue
        if label in android_descendant_texts(node):
            candidates.append(Bounds.parse(node.attrib["bounds"]))
    if not candidates:
        raise ValueError(f"Android button not found for {label!r}")
    return min(candidates, key=lambda value: value.width * value.height)


def walk_harmony(node: dict[str, Any]) -> Iterable[dict[str, Any]]:
    yield node
    for child in node.get("children", []):
        yield from walk_harmony(child)


def harmony_descendant_texts(node: dict[str, Any]) -> set[str]:
    return {
        str(child.get("attributes", {}).get("text", ""))
        for child in walk_harmony(node)
        if child.get("attributes", {}).get("text", "")
    }


def harmony_button_for_label(root: dict[str, Any], label: str) -> Bounds:
    candidates: list[Bounds] = []
    for node in walk_harmony(root):
        attributes = node.get("attributes", {})
        if attributes.get("type") != "Button" or label not in harmony_descendant_texts(node):
            continue
        candidates.append(Bounds.parse(str(attributes["bounds"])))
    if not candidates:
        raise ValueError(f"HarmonyOS button not found for {label!r}")
    return min(candidates, key=lambda value: value.width * value.height)


def android_text_below(root: ElementTree.Element, label: str, minimum_top: float) -> Bounds:
    candidates = [
        Bounds.parse(node.attrib["bounds"])
        for node in root.iter()
        if node.attrib.get("text") == label and Bounds.parse(node.attrib["bounds"]).top > minimum_top
    ]
    if not candidates:
        raise ValueError(f"Android content anchor not found for {label!r}")
    return min(candidates, key=lambda value: value.top)


def harmony_text_below(root: dict[str, Any], label: str, minimum_top: float) -> Bounds:
    candidates = [
        Bounds.parse(str(node["attributes"]["bounds"]))
        for node in walk_harmony(root)
        if node.get("attributes", {}).get("type") == "Text"
        and node.get("attributes", {}).get("text") == label
        and Bounds.parse(str(node["attributes"]["bounds"])).top > minimum_top
    ]
    if not candidates:
        raise ValueError(f"HarmonyOS content anchor not found for {label!r}")
    return min(candidates, key=lambda value: value.top)


def similarity(first: float, second: float) -> float:
    average = max((abs(first) + abs(second)) / 2.0, 1e-9)
    return max(0.0, (1.0 - abs(first - second) / average) * 100.0)


def root_width_from_android(root: ElementTree.Element) -> float:
    bounds = [Bounds.parse(node.attrib["bounds"]) for node in root.iter() if "bounds" in node.attrib]
    if not bounds:
        raise ValueError("Android layout does not contain bounds")
    return max(value.right for value in bounds) - min(value.left for value in bounds)


def root_width_from_harmony(root: dict[str, Any]) -> float:
    return Bounds.parse(str(root["attributes"]["bounds"])).width


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser()
    parser.add_argument("--android-layout", required=True, type=Path)
    parser.add_argument("--harmony-layout", required=True, type=Path)
    parser.add_argument("--android-logical-width", required=True, type=float)
    parser.add_argument("--harmony-logical-width", required=True, type=float)
    parser.add_argument("--labels", default="概览,继续阅读,更多")
    parser.add_argument("--content-anchor", default="继续阅读")
    parser.add_argument("--min-score", default=98.0, type=float)
    return parser.parse_args()


def main() -> int:
    args = parse_args()
    labels = [label.strip() for label in args.labels.split(",") if label.strip()]
    if len(labels) < 2:
        raise ValueError("at least two toolbar labels are required")

    android_root = ElementTree.parse(args.android_layout).getroot()
    harmony_root = json.loads(args.harmony_layout.read_text(encoding="utf-8"))
    android_scale = root_width_from_android(android_root) / args.android_logical_width
    harmony_scale = root_width_from_harmony(harmony_root) / args.harmony_logical_width

    android_buttons = [android_button_for_label(android_root, label).scaled(android_scale) for label in labels]
    harmony_buttons = [harmony_button_for_label(harmony_root, label).scaled(harmony_scale) for label in labels]
    if any(first.left >= second.left for first, second in zip(android_buttons, android_buttons[1:])):
        raise ValueError("Android toolbar label order is unstable")
    if any(first.left >= second.left for first, second in zip(harmony_buttons, harmony_buttons[1:])):
        raise ValueError("HarmonyOS toolbar label order is unstable")

    android_anchor = android_text_below(
        android_root, args.content_anchor, max(button.bottom for button in android_buttons) * android_scale
    ).scaled(android_scale)
    harmony_anchor = harmony_text_below(
        harmony_root, args.content_anchor, max(button.bottom for button in harmony_buttons) * harmony_scale
    ).scaled(harmony_scale)

    metrics: dict[str, float] = {
        "left_inset": similarity(android_buttons[0].left, harmony_buttons[0].left),
        "height": similarity(android_buttons[0].height, harmony_buttons[0].height),
        "total_span": similarity(
            android_buttons[-1].right - android_buttons[0].left,
            harmony_buttons[-1].right - harmony_buttons[0].left,
        ),
        "content_offset": similarity(
            android_anchor.top - android_buttons[0].top,
            harmony_anchor.top - harmony_buttons[0].top,
        ),
    }
    for index, label in enumerate(labels):
        metrics[f"width_{label}"] = similarity(android_buttons[index].width, harmony_buttons[index].width)
    for index in range(len(labels) - 1):
        metrics[f"gap_{index + 1}"] = similarity(
            android_buttons[index + 1].left - android_buttons[index].right,
            harmony_buttons[index + 1].left - harmony_buttons[index].right,
        )

    score = min(metrics.values())
    report = {
        "labels": labels,
        "android_scale": round(android_scale, 4),
        "harmony_scale": round(harmony_scale, 4),
        "android_buttons": [button.__dict__ for button in android_buttons],
        "harmony_buttons": [button.__dict__ for button in harmony_buttons],
        "android_content_offset": round(android_anchor.top - android_buttons[0].top, 4),
        "harmony_content_offset": round(harmony_anchor.top - harmony_buttons[0].top, 4),
        "metrics_percent": {name: round(value, 4) for name, value in metrics.items()},
        "layout_similarity_percent": round(score, 4),
    }
    print(json.dumps(report, ensure_ascii=False, indent=2))
    if not math.isfinite(score) or score < args.min_score:
        print(f"home toolbar layout similarity {score:.2f}% is below {args.min_score:.2f}%", file=sys.stderr)
        return 1
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
