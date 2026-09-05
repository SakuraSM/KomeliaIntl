#!/usr/bin/env python3
"""Gate Android/HarmonyOS search-page geometry from UI layout dumps.

Compact captures compare the full destination. Wider captures additionally
compare the navigation rail while normalizing each device's landscape safe
area against its own destination content bounds.
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


def walk_harmony(node: dict[str, Any]) -> Iterable[dict[str, Any]]:
    yield node
    for child in node.get("children", []):
        yield from walk_harmony(child)


def harmony_attributes(node: dict[str, Any]) -> dict[str, str]:
    return node.get("attributes", {})


def harmony_node(root: dict[str, Any], *, component_type: str | None = None,
                 node_id: str | None = None, text: str | None = None) -> dict[str, Any]:
    matches: list[dict[str, Any]] = []
    for node in walk_harmony(root):
        attributes = harmony_attributes(node)
        if component_type is not None and attributes.get("type") != component_type:
            continue
        if node_id is not None and attributes.get("id") != node_id:
            continue
        if text is not None and attributes.get("text") != text:
            continue
        matches.append(node)
    if not matches:
        raise ValueError(f"HarmonyOS node missing: type={component_type!r}, id={node_id!r}, text={text!r}")
    return matches[0]


def harmony_bounds(node: dict[str, Any]) -> Bounds:
    return Bounds.parse(str(harmony_attributes(node)["bounds"]))


def harmony_descendants(node: dict[str, Any], component_type: str | None = None) -> list[dict[str, Any]]:
    result = list(walk_harmony(node))[1:]
    if component_type is not None:
        result = [candidate for candidate in result if harmony_attributes(candidate).get("type") == component_type]
    return result


def android_node(root: ElementTree.Element, *, class_name: str | None = None,
                 text: str | None = None, scrollable: str | None = None) -> ElementTree.Element:
    matches: list[ElementTree.Element] = []
    for node in root.iter("node"):
        if class_name is not None and node.attrib.get("class") != class_name:
            continue
        if text is not None and node.attrib.get("text") != text:
            continue
        if scrollable is not None and node.attrib.get("scrollable") != scrollable:
            continue
        matches.append(node)
    if not matches:
        raise ValueError(f"Android node missing: class={class_name!r}, text={text!r}, scrollable={scrollable!r}")
    return matches[0]


def android_bounds(node: ElementTree.Element) -> Bounds:
    return Bounds.parse(node.attrib["bounds"])


def android_descendant_texts(node: ElementTree.Element) -> set[str]:
    return {child.attrib.get("text", "") for child in node.iter() if child.attrib.get("text", "")}


def android_button(root: ElementTree.Element, label: str) -> Bounds:
    matches = [
        android_bounds(node)
        for node in root.iter("node")
        if node.attrib.get("clickable") == "true" and label in android_descendant_texts(node)
    ]
    if not matches:
        raise ValueError(f"Android button missing for {label!r}")
    return min(matches, key=lambda value: value.width * value.height)


def android_content_root(root: ElementTree.Element, input_node: ElementTree.Element) -> Bounds:
    parents = {child: node for node in root.iter() for child in node}
    input_bounds = android_bounds(input_node)
    current = input_node
    while current in parents:
        current = parents[current]
        if "bounds" not in current.attrib:
            continue
        bounds = android_bounds(current)
        if bounds.width >= input_bounds.width and bounds.height > input_bounds.height * 2:
            return bounds
    raise ValueError("Android app content root missing")


def android_first_card_and_cover(root: ElementTree.Element, list_bounds: Bounds) -> tuple[Bounds, Bounds]:
    cards = [
        (node, android_bounds(node))
        for node in root.iter("node")
        if node.attrib.get("clickable") == "true"
        and android_bounds(node).top >= list_bounds.top
        and android_bounds(node).left - list_bounds.left < list_bounds.width * 0.1
        and android_bounds(node).width > list_bounds.width * 0.8
    ]
    if not cards:
        raise ValueError("Android search result card missing")
    card_node, card = min(cards, key=lambda value: value[1].top)
    covers = [
        android_bounds(node)
        for node in card_node.iter("node")
        if node is not card_node and node.attrib.get("clickable") == "true"
        and android_bounds(node).width < card.width * 0.5
    ]
    if not covers:
        raise ValueError("Android search result cover missing")
    return card, max(covers, key=lambda value: value.width * value.height)


def harmony_first_card_and_cover(list_node: dict[str, Any]) -> tuple[Bounds, Bounds]:
    rows = [
        node for node in harmony_descendants(list_node, "Row")
        if harmony_attributes(node).get("clickable") == "true"
    ]
    if not rows:
        raise ValueError("HarmonyOS search result card missing")
    card_node = min(rows, key=lambda node: harmony_bounds(node).top)
    card = harmony_bounds(card_node)
    covers = [
        harmony_bounds(node) for node in harmony_descendants(card_node, "Stack")
        if harmony_bounds(node).width < card.width * 0.5
    ]
    if not covers:
        raise ValueError("HarmonyOS search result cover missing")
    return card, max(covers, key=lambda value: value.width * value.height)


def android_navigation_item(root: ElementTree.Element, label: str) -> Bounds:
    text_nodes = [node for node in root.iter("node") if node.attrib.get("text") == label]
    if not text_nodes:
        raise ValueError(f"Android navigation label missing for {label!r}")
    text_bounds = min((android_bounds(node) for node in text_nodes), key=lambda value: value.height)
    candidates = [
        android_bounds(node)
        for node in root.iter("node")
        if label in android_descendant_texts(node)
        and android_bounds(node).width >= text_bounds.width + 80
        and android_bounds(node).height >= text_bounds.height + 60
    ]
    if not candidates:
        raise ValueError(f"Android navigation item missing for {label!r}")
    return min(candidates, key=lambda value: value.width * value.height)


def size_similarity(first: float, second: float) -> float:
    average = max((abs(first) + abs(second)) / 2.0, 1e-9)
    return max(0.0, (1.0 - abs(first - second) / average) * 100.0)


def token_similarity(first: float, second: float, rounding_tolerance: float = 1.0) -> float:
    """Compare small layout tokens while ignoring at most one logical pixel of rounding."""
    average = max((abs(first) + abs(second)) / 2.0, 1e-9)
    difference = max(0.0, abs(first - second) - rounding_tolerance)
    return max(0.0, (1.0 - difference / average) * 100.0)


def position_similarity(first: float, second: float, logical_width: float) -> float:
    return max(0.0, (1.0 - abs(first - second) / max(logical_width, 1e-9)) * 100.0)


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser()
    parser.add_argument("--android-layout", required=True, type=Path)
    parser.add_argument("--harmony-layout", required=True, type=Path)
    parser.add_argument("--android-logical-width", required=True, type=float)
    parser.add_argument("--harmony-logical-width", required=True, type=float)
    parser.add_argument("--title-anchor", default="从零开始的异世界生活")
    parser.add_argument("--min-score", default=98.0, type=float)
    return parser.parse_args()


def main() -> int:
    args = parse_args()
    android_root = ElementTree.parse(args.android_layout).getroot()
    harmony_root = json.loads(args.harmony_layout.read_text(encoding="utf-8"))
    android_screen = android_bounds(next(android_root.iter("node")))
    harmony_screen = harmony_bounds(harmony_root)
    android_scale = android_screen.width / args.android_logical_width
    harmony_scale = harmony_screen.width / args.harmony_logical_width
    logical_width = (args.android_logical_width + args.harmony_logical_width) / 2.0

    android_input_node = android_node(android_root, class_name="android.widget.EditText")
    android_input = android_bounds(android_input_node)
    android_content = android_content_root(android_root, android_input_node)
    android_list = android_bounds(android_node(android_root, scrollable="true"))
    android_buttons = [android_button(android_root, label) for label in ("系列", "书籍")]
    android_card, android_cover = android_first_card_and_cover(android_root, android_list)
    android_title = android_bounds(android_node(android_root, text=args.title_anchor))

    harmony_content_node = harmony_node(harmony_root, node_id="search_page")
    harmony_content = harmony_bounds(harmony_content_node)
    harmony_input_rows = [
        node for node in harmony_descendants(harmony_content_node, "Row")
        if any(harmony_attributes(child).get("type") == "TextInput" for child in node.get("children", []))
    ]
    if not harmony_input_rows:
        raise ValueError("HarmonyOS search field container missing")
    harmony_input = harmony_bounds(harmony_input_rows[0])
    harmony_list_node = harmony_node(harmony_content_node, component_type="List")
    harmony_list = harmony_bounds(harmony_list_node)
    harmony_buttons = [harmony_bounds(harmony_node(harmony_content_node, component_type="Button", text=label))
                       for label in ("系列", "书籍")]
    harmony_card, harmony_cover = harmony_first_card_and_cover(harmony_list_node)
    harmony_title = harmony_bounds(harmony_node(harmony_content_node, component_type="Text", text=args.title_anchor))

    def scaled(bounds: Bounds, scale: float) -> Bounds:
        return bounds.scaled(scale)

    android_content = scaled(android_content, android_scale)
    harmony_content = scaled(harmony_content, harmony_scale)
    android_input = scaled(android_input, android_scale)
    harmony_input = scaled(harmony_input, harmony_scale)
    android_list = scaled(android_list, android_scale)
    harmony_list = scaled(harmony_list, harmony_scale)
    android_buttons = [scaled(value, android_scale) for value in android_buttons]
    harmony_buttons = [scaled(value, harmony_scale) for value in harmony_buttons]
    android_card, android_cover, android_title = (
        scaled(android_card, android_scale), scaled(android_cover, android_scale),
        scaled(android_title, android_scale))
    harmony_card, harmony_cover, harmony_title = (
        scaled(harmony_card, harmony_scale), scaled(harmony_cover, harmony_scale),
        scaled(harmony_title, harmony_scale))

    metrics = {
        "input_top": position_similarity(android_input.top - android_content.top,
                                         harmony_input.top - harmony_content.top, logical_width),
        "input_width_fraction": size_similarity(android_input.width / android_content.width,
                                                harmony_input.width / harmony_content.width),
        "input_height": size_similarity(android_input.height, harmony_input.height),
        "segment_top": position_similarity(android_buttons[0].top - android_content.top,
                                           harmony_buttons[0].top - harmony_content.top, logical_width),
        "segment_left": position_similarity(android_buttons[0].left - android_content.left,
                                            harmony_buttons[0].left - harmony_content.left, logical_width),
        "segment_width_fraction": size_similarity(android_buttons[0].width / android_content.width,
                                                  harmony_buttons[0].width / harmony_content.width),
        "segment_height": size_similarity(android_buttons[0].height, harmony_buttons[0].height),
        "segment_gap": token_similarity(android_buttons[1].left - android_buttons[0].right,
                                        harmony_buttons[1].left - harmony_buttons[0].right),
        "list_top": position_similarity(android_list.top - android_content.top,
                                        harmony_list.top - harmony_content.top, logical_width),
        "list_width_fraction": size_similarity(android_list.width / android_content.width,
                                               harmony_list.width / harmony_content.width),
        "card_left": position_similarity(android_card.left - android_content.left,
                                         harmony_card.left - harmony_content.left, logical_width),
        "card_width_fraction": size_similarity(android_card.width / android_content.width,
                                               harmony_card.width / harmony_content.width),
        "card_height": size_similarity(android_card.height, harmony_card.height),
        "cover_left_in_card": position_similarity(android_cover.left - android_card.left,
                                                   harmony_cover.left - harmony_card.left, logical_width),
        "cover_top_in_card": position_similarity(android_cover.top - android_card.top,
                                                  harmony_cover.top - harmony_card.top, logical_width),
        "cover_width": size_similarity(android_cover.width, harmony_cover.width),
        "cover_height": size_similarity(android_cover.height, harmony_cover.height),
        "title_left_in_card": position_similarity(android_title.left - android_card.left,
                                                  harmony_title.left - harmony_card.left, logical_width),
        "title_top_in_card": position_similarity(android_title.top - android_card.top,
                                                  harmony_title.top - harmony_card.top, logical_width),
    }
    try:
        harmony_rail = harmony_bounds(harmony_node(harmony_root, node_id="primary_navigation_rail"))
    except ValueError:
        harmony_rail = None
    if harmony_rail is not None:
        android_nav = [android_navigation_item(android_root, label).scaled(android_scale)
                       for label in ("书库", "首页")]
        harmony_nav = [harmony_bounds(harmony_node(harmony_root, node_id=component_id)).scaled(harmony_scale)
                       for component_id in ("nav_library", "nav_home")]
        harmony_rail = harmony_rail.scaled(harmony_scale)
        android_rail_width = android_content.left - min(value.left for value in android_nav)
        metrics.update({
            "rail_width": size_similarity(android_rail_width, harmony_rail.width),
            "nav_item_width": size_similarity(android_nav[0].width, harmony_nav[0].width),
            "nav_item_height": token_similarity(android_nav[0].height, harmony_nav[0].height),
            "nav_item_gap": token_similarity(android_nav[1].top - android_nav[0].bottom,
                                             harmony_nav[1].top - harmony_nav[0].bottom),
        })
    score = min(metrics.values())
    report = {
        "android_scale": round(android_scale, 4),
        "harmony_scale": round(harmony_scale, 4),
        "presentation": "rail" if harmony_rail is not None else "bottom_bar",
        "title_anchor": args.title_anchor,
        "metrics_percent": {name: round(value, 4) for name, value in metrics.items()},
        "layout_similarity_percent": round(score, 4),
    }
    print(json.dumps(report, ensure_ascii=False, indent=2))
    if not math.isfinite(score) or score < args.min_score:
        print(f"search layout similarity {score:.2f}% is below {args.min_score:.2f}%", file=sys.stderr)
        return 1
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
