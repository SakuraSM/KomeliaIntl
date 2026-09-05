#!/usr/bin/env python3
"""Gate Android/HarmonyOS Medium settings-root geometry from layout dumps.

The devices may expose different landscape safe areas. Measurements are
therefore relative to each app's destination content bounds and compare the
shared application-settings section rather than private account/server rows.
"""

from __future__ import annotations

import argparse
import json
import re
import sys
import xml.etree.ElementTree as ElementTree
from dataclasses import dataclass
from pathlib import Path
from typing import Any, Iterable


BOUNDS_PATTERN = re.compile(r"\[(\d+),(\d+)\]\[(\d+),(\d+)\]")
SHARED_ROWS = (
    ("外观", "settings_appearance"),
    ("首页分组", "settings_home_groups"),
    ("网络连接", "settings_network"),
    ("图像阅读器", "settings_image_reader"),
    ("EPUB 阅读器", "settings_epub_reader"),
)


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


def android_text_nodes(root: ElementTree.Element, label: str) -> list[ElementTree.Element]:
    return [node for node in root.iter() if node.attrib.get("text") == label]


def android_descendant_texts(node: ElementTree.Element) -> set[str]:
    return {child.attrib.get("text", "") for child in node.iter() if child.attrib.get("text", "")}


def android_navigation_item(root: ElementTree.Element, label: str) -> Bounds:
    text_nodes = android_text_nodes(root, label)
    if not text_nodes:
        raise ValueError(f"Android navigation label not found for {label!r}")
    text_bounds = min((Bounds.parse(node.attrib["bounds"]) for node in text_nodes), key=lambda value: value.height)
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


def android_page_title(root: ElementTree.Element) -> tuple[ElementTree.Element, Bounds]:
    nodes = android_text_nodes(root, "设置")
    if len(nodes) < 2:
        raise ValueError("Android settings page title was not distinguished from the rail label")
    node = max(nodes, key=lambda candidate: Bounds.parse(candidate.attrib["bounds"]).height)
    return node, Bounds.parse(node.attrib["bounds"])


def android_parent_map(root: ElementTree.Element) -> dict[ElementTree.Element, ElementTree.Element]:
    return {child: node for node in root.iter() for child in node}


def android_content(root: ElementTree.Element, title_node: ElementTree.Element) -> Bounds:
    parents = android_parent_map(root)
    current = title_node
    title = Bounds.parse(title_node.attrib["bounds"])
    candidates: list[Bounds] = []
    while current in parents:
        current = parents[current]
        if "bounds" not in current.attrib:
            continue
        bounds = Bounds.parse(current.attrib["bounds"])
        if bounds.left < title.left and bounds.right > title.right:
            candidates.append(bounds)
    if not candidates:
        raise ValueError("Android settings content bounds not found")
    # The destination root starts immediately after the rail and includes safe-area offsets.
    return min((value for value in candidates if value.width > title.width * 4), key=lambda value: value.width)


def android_row(root: ElementTree.Element, label: str) -> Bounds:
    parents = android_parent_map(root)
    nodes = android_text_nodes(root, label)
    if not nodes:
        raise ValueError(f"Android settings row label not found for {label!r}")
    current = nodes[0]
    while current in parents:
        current = parents[current]
        if current.attrib.get("clickable") == "true" and "bounds" in current.attrib:
            return Bounds.parse(current.attrib["bounds"])
    raise ValueError(f"Android clickable settings row not found for {label!r}")


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


def harmony_parent_bounds_by_id(root: dict[str, Any], component_id: str) -> Bounds:
    for node in walk_harmony(root):
        for child in node.get("children", []):
            if harmony_attributes(child).get("id") == component_id:
                attributes = harmony_attributes(node)
                if "bounds" not in attributes:
                    break
                return Bounds.parse(str(attributes["bounds"]))
    raise ValueError(f"HarmonyOS parent not found for id {component_id!r}")


def harmony_text(root: dict[str, Any], label: str, minimum_left: float = 0.0) -> Bounds:
    candidates = [
        Bounds.parse(str(harmony_attributes(node)["bounds"]))
        for node in walk_harmony(root)
        if harmony_attributes(node).get("type") == "Text"
        and harmony_attributes(node).get("text") == label
        and Bounds.parse(str(harmony_attributes(node)["bounds"])).left >= minimum_left
    ]
    if not candidates:
        raise ValueError(f"HarmonyOS text not found for {label!r}")
    return min(candidates, key=lambda value: value.top)


def root_width_from_android(root: ElementTree.Element) -> float:
    bounds = [Bounds.parse(node.attrib["bounds"]) for node in root.iter() if "bounds" in node.attrib]
    return max(value.right for value in bounds) - min(value.left for value in bounds)


def root_width_from_harmony(root: dict[str, Any]) -> float:
    return Bounds.parse(str(harmony_attributes(root)["bounds"])).width


def similarity(first: float, second: float) -> float:
    average = max((abs(first) + abs(second)) / 2.0, 1e-9)
    return max(0.0, (1.0 - abs(first - second) / average) * 100.0)


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser()
    parser.add_argument("--android-layout", required=True, type=Path)
    parser.add_argument("--harmony-layout", required=True, type=Path)
    parser.add_argument("--android-logical-width", required=True, type=float)
    parser.add_argument("--harmony-logical-width", required=True, type=float)
    parser.add_argument("--min-score", default=98.0, type=float)
    return parser.parse_args()


def main() -> int:
    args = parse_args()
    android_root = ElementTree.parse(args.android_layout).getroot()
    harmony_root = json.loads(args.harmony_layout.read_text(encoding="utf-8"))
    android_scale = root_width_from_android(android_root) / args.android_logical_width
    harmony_scale = root_width_from_harmony(harmony_root) / args.harmony_logical_width

    android_title_node, android_title_raw = android_page_title(android_root)
    android_content_bounds = android_content(android_root, android_title_node).scaled(android_scale)
    android_title = android_title_raw.scaled(android_scale)
    android_section = android_text_nodes(android_root, "应用设置")[0]
    android_section_bounds = Bounds.parse(android_section.attrib["bounds"]).scaled(android_scale)
    android_rows = [android_row(android_root, label).scaled(android_scale) for label, _ in SHARED_ROWS]
    android_labels = [
        Bounds.parse(android_text_nodes(android_root, label)[0].attrib["bounds"]).scaled(android_scale)
        for label, _ in SHARED_ROWS
    ]
    android_nav = [
        android_navigation_item(android_root, label).scaled(android_scale)
        for label in ("书库", "首页")
    ]

    harmony_content = harmony_by_id(harmony_root, "settings_page").scaled(harmony_scale)
    harmony_title = harmony_text(harmony_root, "设置", harmony_content.left * harmony_scale).scaled(harmony_scale)
    harmony_section = harmony_text(
        harmony_root, "应用设置", harmony_content.left * harmony_scale
    ).scaled(harmony_scale)
    harmony_rows = [harmony_by_id(harmony_root, component_id).scaled(harmony_scale) for _, component_id in SHARED_ROWS]
    harmony_card = harmony_parent_bounds_by_id(
        harmony_root, SHARED_ROWS[0][1]
    ).scaled(harmony_scale)
    harmony_labels = [
        harmony_text(harmony_root, label, harmony_content.left * harmony_scale).scaled(harmony_scale)
        for label, _ in SHARED_ROWS
    ]
    harmony_nav = [
        harmony_by_id(harmony_root, component_id).scaled(harmony_scale)
        for component_id in ("nav_library", "nav_home")
    ]
    harmony_rail = harmony_by_id(harmony_root, "primary_navigation_rail").scaled(harmony_scale)
    android_rail_width = android_content_bounds.left - min(value.left for value in android_nav)

    metrics: dict[str, float] = {
        "rail_width": similarity(android_rail_width, harmony_rail.width),
        "nav_item_width": similarity(android_nav[0].width, harmony_nav[0].width),
        "nav_item_height": similarity(android_nav[0].height, harmony_nav[0].height),
        "nav_item_gap": similarity(android_nav[1].top - android_nav[0].bottom,
                                   harmony_nav[1].top - harmony_nav[0].bottom),
        "page_left_inset": similarity(android_title.left - android_content_bounds.left,
                                      harmony_title.left - harmony_content.left),
        "page_top_inset": similarity(android_title.top - android_content_bounds.top,
                                     harmony_title.top - harmony_content.top),
        "page_title_height": similarity(android_title.height, harmony_title.height),
        "title_to_section_gap": similarity(android_section_bounds.top - android_title.bottom,
                                           harmony_section.top - harmony_title.bottom),
        "section_height": similarity(android_section_bounds.height, harmony_section.height),
        "section_to_row_gap": similarity(android_rows[0].top - android_section_bounds.bottom,
                                         harmony_card.top - harmony_section.bottom),
        "row_height": similarity(android_rows[0].height, harmony_rows[0].height),
        "row_stride": similarity(android_rows[1].top - android_rows[0].top,
                                 harmony_rows[1].top - harmony_rows[0].top),
        "row_width_fraction": similarity(android_rows[0].width / android_content_bounds.width,
                                         harmony_card.width / harmony_content.width),
        "label_left_inset": similarity(android_labels[0].left - android_rows[0].left,
                                       harmony_labels[0].left - harmony_rows[0].left),
        "label_height": similarity(android_labels[0].height, harmony_labels[0].height),
    }
    for index, ((label, _), android_row_bounds, harmony_row_bounds) in enumerate(
        zip(SHARED_ROWS, android_rows, harmony_rows)
    ):
        harmony_top = harmony_card.top if index == 0 else harmony_row_bounds.top
        metrics[f"row_{index + 1}_{label}_top"] = similarity(
            android_row_bounds.top - android_content_bounds.top,
            harmony_top - harmony_content.top,
        )

    overall = min(metrics.values())
    print(json.dumps({"overall": overall, "metrics": metrics}, ensure_ascii=False, indent=2))
    if overall + 1e-9 < args.min_score:
        print(f"Settings Medium parity failed: {overall:.4f}% < {args.min_score:.4f}%", file=sys.stderr)
        return 1
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
