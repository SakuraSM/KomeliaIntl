#!/usr/bin/env python3
"""Gate Android/HarmonyOS Medium home-shell geometry from UI layout dumps.

The two captures may have different safe-area widths and different enabled home
groups. The gate therefore compares the navigation rail, shared group chips,
section rhythm, and normalized three-column grid geometry instead of private
book pixels or absolute content widths.
"""

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


def android_text_nodes(root: ElementTree.Element, label: str) -> list[ElementTree.Element]:
    return [node for node in root.iter() if node.attrib.get("text") == label]


def android_navigation_item(root: ElementTree.Element, label: str) -> Bounds:
    text_nodes = android_text_nodes(root, label)
    if not text_nodes:
        raise ValueError(f"Android navigation label not found for {label!r}")
    text_bounds = Bounds.parse(text_nodes[0].attrib["bounds"])
    candidates: list[Bounds] = []
    for node in root.iter():
        if label not in android_descendant_texts(node) or "bounds" not in node.attrib:
            continue
        bounds = Bounds.parse(node.attrib["bounds"])
        if bounds.width >= text_bounds.width + 80 and bounds.height >= text_bounds.height + 60:
            candidates.append(bounds)
    if not candidates:
        raise ValueError(f"Android navigation item not found for {label!r}")
    return min(candidates, key=lambda value: value.width * value.height)


def android_chip(root: ElementTree.Element, label: str) -> Bounds:
    candidates = [
        Bounds.parse(node.attrib["bounds"])
        for node in root.iter()
        if node.attrib.get("checkable") == "true" and label in android_descendant_texts(node)
    ]
    if not candidates:
        raise ValueError(f"Android home chip not found for {label!r}")
    return min(candidates, key=lambda value: value.width * value.height)


def android_section_title(root: ElementTree.Element, label: str, minimum_top: float) -> Bounds:
    candidates = [
        Bounds.parse(node.attrib["bounds"])
        for node in android_text_nodes(root, label)
        if Bounds.parse(node.attrib["bounds"]).top > minimum_top
    ]
    if not candidates:
        raise ValueError(f"Android section title not found for {label!r}")
    return min(candidates, key=lambda value: value.top)


def android_first_grid_row(root: ElementTree.Element, minimum_top: float) -> list[Bounds]:
    candidates = [
        Bounds.parse(node.attrib["bounds"])
        for node in root.iter()
        if node.attrib.get("clickable") == "true"
        and node.attrib.get("text", "") == ""
        and Bounds.parse(node.attrib["bounds"]).top > minimum_top
        and Bounds.parse(node.attrib["bounds"]).width > 200
        and Bounds.parse(node.attrib["bounds"]).height > 300
    ]
    if not candidates:
        raise ValueError("Android home grid cards not found")
    first_top = min(value.top for value in candidates)
    row = sorted((value for value in candidates if value.top == first_top), key=lambda value: value.left)
    if len(row) < 2:
        raise ValueError("Android first home grid row has fewer than two cards")
    return row


def walk_harmony(node: dict[str, Any]) -> Iterable[dict[str, Any]]:
    yield node
    for child in node.get("children", []):
        yield from walk_harmony(child)


def harmony_attributes(node: dict[str, Any]) -> dict[str, Any]:
    return node.get("attributes", {})


def harmony_by_id(root: dict[str, Any], component_id: str) -> Bounds:
    candidates = [
        Bounds.parse(str(harmony_attributes(node)["bounds"]))
        for node in walk_harmony(root)
        if harmony_attributes(node).get("id") == component_id
    ]
    if not candidates:
        raise ValueError(f"HarmonyOS component not found for id {component_id!r}")
    return min(candidates, key=lambda value: value.width * value.height)


def harmony_descendant_texts(node: dict[str, Any]) -> set[str]:
    return {
        str(harmony_attributes(child).get("text", ""))
        for child in walk_harmony(node)
        if harmony_attributes(child).get("text", "")
    }


def harmony_chip(root: dict[str, Any], label: str) -> Bounds:
    candidates = [
        Bounds.parse(str(harmony_attributes(node)["bounds"]))
        for node in walk_harmony(root)
        if harmony_attributes(node).get("type") == "Button" and label in harmony_descendant_texts(node)
    ]
    if not candidates:
        raise ValueError(f"HarmonyOS home chip not found for {label!r}")
    return min(candidates, key=lambda value: value.width * value.height)


def harmony_section_title(root: dict[str, Any], label: str, minimum_top: float) -> Bounds:
    candidates = [
        Bounds.parse(str(harmony_attributes(node)["bounds"]))
        for node in walk_harmony(root)
        if harmony_attributes(node).get("type") == "Text"
        and harmony_attributes(node).get("text") == label
        and Bounds.parse(str(harmony_attributes(node)["bounds"])).top > minimum_top
    ]
    if not candidates:
        raise ValueError(f"HarmonyOS section title not found for {label!r}")
    return min(candidates, key=lambda value: value.top)


def harmony_first_grid_row(root: dict[str, Any], minimum_top: float) -> list[Bounds]:
    candidates = [
        Bounds.parse(str(harmony_attributes(node)["bounds"]))
        for node in walk_harmony(root)
        if harmony_attributes(node).get("type") == "GridItem"
        and Bounds.parse(str(harmony_attributes(node)["bounds"])).top > minimum_top
    ]
    if not candidates:
        raise ValueError("HarmonyOS home grid cards not found")
    first_top = min(value.top for value in candidates)
    row = sorted((value for value in candidates if value.top == first_top), key=lambda value: value.left)
    if len(row) < 2:
        raise ValueError("HarmonyOS first home grid row has fewer than two cards")
    return row


def similarity(first: float, second: float) -> float:
    average = max((abs(first) + abs(second)) / 2.0, 1e-9)
    return max(0.0, (1.0 - abs(first - second) / average) * 100.0)


def root_width_from_android(root: ElementTree.Element) -> float:
    bounds = [Bounds.parse(node.attrib["bounds"]) for node in root.iter() if "bounds" in node.attrib]
    if not bounds:
        raise ValueError("Android layout does not contain bounds")
    return max(value.right for value in bounds) - min(value.left for value in bounds)


def root_width_from_harmony(root: dict[str, Any]) -> float:
    return Bounds.parse(str(harmony_attributes(root)["bounds"])).width


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser()
    parser.add_argument("--android-layout", required=True, type=Path)
    parser.add_argument("--harmony-layout", required=True, type=Path)
    parser.add_argument("--android-logical-width", required=True, type=float)
    parser.add_argument("--harmony-logical-width", required=True, type=float)
    parser.add_argument(
        "--labels",
        default="概览,继续阅读,最近添加的书籍,最近添加的系列,最近更新的系列",
    )
    parser.add_argument("--section-anchor", default="继续阅读")
    parser.add_argument("--min-score", default=98.0, type=float)
    return parser.parse_args()


def main() -> int:
    args = parse_args()
    labels = [label.strip() for label in args.labels.split(",") if label.strip()]
    android_root = ElementTree.parse(args.android_layout).getroot()
    harmony_root = json.loads(args.harmony_layout.read_text(encoding="utf-8"))
    android_scale = root_width_from_android(android_root) / args.android_logical_width
    harmony_scale = root_width_from_harmony(harmony_root) / args.harmony_logical_width

    android_nav = [
        android_navigation_item(android_root, label).scaled(android_scale)
        for label in ("书库", "首页")
    ]
    harmony_nav = [
        harmony_by_id(harmony_root, component_id).scaled(harmony_scale)
        for component_id in ("nav_library", "nav_home")
    ]
    android_rail = Bounds(
        min(value.left for value in android_nav),
        min(value.top for value in android_nav),
        max(value.right for value in android_nav),
        max(value.bottom for value in android_nav),
    )
    harmony_rail_component = harmony_by_id(harmony_root, "primary_navigation_rail").scaled(harmony_scale)

    android_chips = [android_chip(android_root, label).scaled(android_scale) for label in labels]
    harmony_chips = [harmony_chip(harmony_root, label).scaled(harmony_scale) for label in labels]
    android_section = android_section_title(
        android_root, args.section_anchor, max(value.bottom for value in android_chips) * android_scale
    ).scaled(android_scale)
    harmony_section = harmony_section_title(
        harmony_root, args.section_anchor, max(value.bottom for value in harmony_chips) * harmony_scale
    ).scaled(harmony_scale)
    android_cards = [value.scaled(android_scale) for value in android_first_grid_row(
        android_root, android_section.bottom * android_scale)]
    harmony_cards = [value.scaled(harmony_scale) for value in harmony_first_grid_row(
        harmony_root, harmony_section.bottom * harmony_scale)]

    android_grid_span = android_cards[-1].right - android_cards[0].left
    harmony_grid_span = harmony_cards[-1].right - harmony_cards[0].left
    metrics: dict[str, float] = {
        "rail_width": similarity(android_rail.width, harmony_rail_component.width),
        "nav_item_width": similarity(android_nav[0].width, harmony_nav[0].width),
        "nav_item_height": similarity(android_nav[0].height, harmony_nav[0].height),
        "nav_item_gap": similarity(
            android_nav[1].top - android_nav[0].bottom,
            harmony_nav[1].top - harmony_nav[0].bottom,
        ),
        "toolbar_left_inset": similarity(
            android_chips[0].left - android_rail.right,
            harmony_chips[0].left - harmony_rail_component.right,
        ),
        "toolbar_height": similarity(android_chips[0].height, harmony_chips[0].height),
        "section_left_inset": similarity(
            android_section.left - android_rail.right,
            harmony_section.left - harmony_rail_component.right,
        ),
        "section_top_offset": similarity(
            android_section.top - android_chips[0].top,
            harmony_section.top - harmony_chips[0].top,
        ),
        "grid_left_inset": similarity(
            android_cards[0].left - android_rail.right,
            harmony_cards[0].left - harmony_rail_component.right,
        ),
        "grid_top_offset": similarity(
            android_cards[0].top - android_section.top,
            harmony_cards[0].top - harmony_section.top,
        ),
        "grid_gap": similarity(
            android_cards[1].left - android_cards[0].right,
            harmony_cards[1].left - harmony_cards[0].right,
        ),
        "grid_card_fraction": similarity(
            android_cards[0].width / android_grid_span,
            harmony_cards[0].width / harmony_grid_span,
        ),
    }
    for index, label in enumerate(labels):
        metrics[f"chip_width_{label}"] = similarity(android_chips[index].width, harmony_chips[index].width)
    for index in range(len(labels) - 1):
        metrics[f"chip_gap_{index + 1}"] = similarity(
            android_chips[index + 1].left - android_chips[index].right,
            harmony_chips[index + 1].left - harmony_chips[index].right,
        )

    columns_match = len(android_cards) == len(harmony_cards)
    score = min(metrics.values()) if columns_match else 0.0
    report = {
        "labels": labels,
        "android_scale": round(android_scale, 4),
        "harmony_scale": round(harmony_scale, 4),
        "android_columns": len(android_cards),
        "harmony_columns": len(harmony_cards),
        "android_rail_width": round(android_rail.width, 4),
        "harmony_rail_width": round(harmony_rail_component.width, 4),
        "metrics_percent": {name: round(value, 4) for name, value in metrics.items()},
        "layout_similarity_percent": round(score, 4),
    }
    print(json.dumps(report, ensure_ascii=False, indent=2))
    if not math.isfinite(score) or score < args.min_score:
        print(f"Medium home layout similarity {score:.2f}% is below {args.min_score:.2f}%", file=sys.stderr)
        return 1
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
