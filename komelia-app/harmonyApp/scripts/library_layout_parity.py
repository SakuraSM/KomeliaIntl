#!/usr/bin/env python3
"""Gate Android/HarmonyOS responsive library-page geometry."""

from __future__ import annotations

import argparse
import json
import math
import re
import sys
import xml.etree.ElementTree as ElementTree
from dataclasses import dataclass
from pathlib import Path
from typing import Any, Callable, Iterable


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


def harmony_bounds(node: dict[str, Any]) -> Bounds:
    return Bounds.parse(str(harmony_attributes(node)["bounds"]))


def harmony_node(root: dict[str, Any], predicate: Callable[[dict[str, str]], bool], label: str) -> dict[str, Any]:
    for node in walk_harmony(root):
        if predicate(harmony_attributes(node)):
            return node
    raise ValueError(f"HarmonyOS node missing: {label}")


def harmony_parent_map(root: dict[str, Any]) -> dict[int, dict[str, Any]]:
    parents: dict[int, dict[str, Any]] = {}
    for node in walk_harmony(root):
        for child in node.get("children", []):
            parents[id(child)] = node
    return parents


def harmony_ancestor(node: dict[str, Any], parents: dict[int, dict[str, Any]],
                     predicate: Callable[[dict[str, str]], bool], label: str) -> dict[str, Any]:
    current: dict[str, Any] | None = node
    while current is not None:
        if predicate(harmony_attributes(current)):
            return current
        current = parents.get(id(current))
    raise ValueError(f"HarmonyOS ancestor missing: {label}")


def android_bounds(node: ElementTree.Element) -> Bounds:
    return Bounds.parse(node.attrib["bounds"])


def android_descendant_values(node: ElementTree.Element, attribute: str) -> set[str]:
    return {child.attrib.get(attribute, "") for child in node.iter() if child.attrib.get(attribute, "")}


def android_parent_map(root: ElementTree.Element) -> dict[ElementTree.Element, ElementTree.Element]:
    return {child: node for node in root.iter() for child in node}


def android_common_ancestor(first: ElementTree.Element, second: ElementTree.Element,
                            parents: dict[ElementTree.Element, ElementTree.Element],
                            label: str) -> ElementTree.Element:
    second_ancestors: set[ElementTree.Element] = {second}
    current = second
    while current in parents:
        current = parents[current]
        second_ancestors.add(current)
    current = first
    while current not in second_ancestors and current in parents:
        current = parents[current]
    if current not in second_ancestors:
        raise ValueError(f"Android common ancestor missing: {label}")
    return current


def android_clickable(root: ElementTree.Element, *, text: str | None = None,
                      description: str | None = None, long_clickable: bool | None = None) -> ElementTree.Element:
    matches: list[ElementTree.Element] = []
    for node in root.iter("node"):
        if node.attrib.get("clickable") != "true":
            continue
        if long_clickable is not None and node.attrib.get("long-clickable") != str(long_clickable).lower():
            continue
        if text is not None and text not in android_descendant_values(node, "text"):
            continue
        if description is not None and description not in android_descendant_values(node, "content-desc"):
            continue
        matches.append(node)
    if not matches:
        raise ValueError(f"Android clickable missing: text={text!r}, description={description!r}")
    return min(matches, key=lambda node: android_bounds(node).width * android_bounds(node).height)


def android_series_count(root: ElementTree.Element, preferred_label: str) -> tuple[ElementTree.Element, str]:
    try:
        return android_clickable(root, text=preferred_label), preferred_label
    except ValueError:
        pass
    pattern = re.compile(r"^\s*\d+\s*(?:个系列|series(?:es)?)\s*$", re.IGNORECASE)
    matches: list[tuple[ElementTree.Element, str]] = []
    for node in root.iter("node"):
        if node.attrib.get("clickable") != "true":
            continue
        for value in android_descendant_values(node, "text"):
            if pattern.fullmatch(value):
                matches.append((node, value))
                break
    if not matches:
        raise ValueError("Android series count missing")
    return min(matches, key=lambda value: android_bounds(value[0]).width * android_bounds(value[0]).height)


def android_content_root(root: ElementTree.Element, scope_node: ElementTree.Element) -> Bounds:
    parents = {child: node for node in root.iter() for child in node}
    scope = android_bounds(scope_node)
    current = scope_node
    while current in parents:
        current = parents[current]
        if "bounds" not in current.attrib:
            continue
        bounds = android_bounds(current)
        if bounds.width >= scope.width and bounds.height > scope.height * 2:
            return bounds
    raise ValueError("Android library content root missing")


def android_full_regions(root: ElementTree.Element, scope_node: ElementTree.Element,
                         count_node: ElementTree.Element) -> tuple[Bounds, Bounds, Bounds]:
    parents = android_parent_map(root)
    page_node = android_common_ancestor(scope_node, count_node, parents, "library page")
    page = android_bounds(page_node)
    current = scope_node
    pane: Bounds | None = None
    while current in parents:
        current = parents[current]
        if "bounds" not in current.attrib:
            continue
        bounds = android_bounds(current)
        if (abs(bounds.top - page.top) <= 1 and abs(bounds.bottom - page.bottom) <= 1
                and bounds.right < page.right):
            pane = bounds
            break
    if pane is None:
        raise ValueError("Android Full library supporting pane missing")
    content = Bounds(pane.right, page.top, page.right, page.bottom)
    return page, pane, content


def android_series_cards(root: ElementTree.Element, content: Bounds) -> list[Bounds]:
    cards = [
        android_bounds(node)
        for node in root.iter("node")
        if node.attrib.get("clickable") == "true"
        and node.attrib.get("long-clickable") == "true"
        and android_bounds(node).left >= content.left
        and android_bounds(node).top >= content.top
    ]
    if len(cards) < 2:
        raise ValueError("Android library series cards missing")
    return sorted(cards, key=lambda value: (value.top, value.left))


def harmony_series_cards(content_node: dict[str, Any]) -> list[Bounds]:
    stable_cards: list[tuple[int, Bounds]] = []
    for node in walk_harmony(content_node):
        component_id = harmony_attributes(node).get("id", "")
        match = re.fullmatch(r"library_series_card_(\d+)", component_id)
        if match is not None:
            stable_cards.append((int(match.group(1)), harmony_bounds(node)))
    if len(stable_cards) >= 2:
        return [bounds for _, bounds in sorted(stable_cards, key=lambda value: value[0])]
    cards = [
        harmony_bounds(node)
        for node in walk_harmony(content_node)
        if harmony_attributes(node).get("type") == "Column"
        and harmony_attributes(node).get("clickable") == "true"
    ]
    if len(cards) < 2:
        raise ValueError("HarmonyOS library series cards missing")
    return sorted(cards, key=lambda value: (value.top, value.left))


def android_navigation_item(root: ElementTree.Element, label: str) -> Bounds:
    text_nodes = [node for node in root.iter("node") if node.attrib.get("text") == label]
    if not text_nodes:
        raise ValueError(f"Android navigation label missing for {label!r}")
    text_bounds = min((android_bounds(node) for node in text_nodes), key=lambda value: value.height)
    candidates = [
        android_bounds(node)
        for node in root.iter("node")
        if label in android_descendant_values(node, "text")
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
    parser.add_argument("--series-count-label", default="11 个系列")
    parser.add_argument("--first-title", default="media")
    parser.add_argument("--second-title", default="七龙珠")
    parser.add_argument("--min-score", default=98.0, type=float)
    return parser.parse_args()


def main() -> int:
    args = parse_args()
    android_root = ElementTree.parse(args.android_layout).getroot()
    harmony_root = json.loads(args.harmony_layout.read_text(encoding="utf-8"))
    harmony_parents = harmony_parent_map(harmony_root)

    android_screen = android_bounds(next(android_root.iter("node")))
    harmony_screen = harmony_bounds(harmony_root)
    android_scale = android_screen.width / args.android_logical_width
    harmony_scale = harmony_screen.width / args.harmony_logical_width
    logical_width = (args.android_logical_width + args.harmony_logical_width) / 2.0

    android_scope_node = android_clickable(android_root, text="全部书库")
    android_scope = android_bounds(android_scope_node)
    android_count_node, android_count_label = android_series_count(android_root, args.series_count_label)
    android_count = android_bounds(android_count_node)
    android_filter = android_bounds(android_clickable(android_root, description="筛选"))
    android_page_size = android_bounds(android_clickable(android_root, text="20"))

    harmony_page_node = harmony_node(
        harmony_root, lambda attributes: attributes.get("id") == "library_page", "library_page")
    harmony_page = harmony_bounds(harmony_page_node)
    try:
        harmony_pane_node = harmony_node(
            harmony_page_node,
            lambda attributes: attributes.get("id") == "library_scope_supporting_pane",
            "library_scope_supporting_pane")
    except ValueError:
        harmony_pane_node = None
    if harmony_pane_node is not None:
        android_page, android_pane, android_content = android_full_regions(
            android_root, android_scope_node, android_count_node)
        harmony_pane = harmony_bounds(harmony_pane_node)
        harmony_content_node = harmony_node(
            harmony_page_node,
            lambda attributes: attributes.get("id") == "library_content_column",
            "library_content_column")
        harmony_content = harmony_bounds(harmony_content_node)
        layout_mode = "full"
    else:
        android_page = android_content_root(android_root, android_scope_node)
        android_pane = None
        android_content = android_page
        harmony_pane = None
        harmony_content_node = harmony_page_node
        harmony_content = harmony_page
        layout_mode = "standard"

    android_cards = android_series_cards(android_root, android_content)
    android_first_card, android_second_card = android_cards[:2]
    harmony_scope_text = harmony_node(
        harmony_page_node, lambda attributes: attributes.get("text") == "全部书库", "scope label")
    harmony_scope = harmony_bounds(harmony_ancestor(
        harmony_scope_text, harmony_parents,
        lambda attributes: attributes.get("clickable") == "true", "scope control"))
    try:
        harmony_count_node = harmony_node(
            harmony_content_node,
            lambda attributes: attributes.get("id") == "library_series_count",
            "library_series_count")
    except ValueError:
        harmony_count_node = harmony_node(
            harmony_content_node,
            lambda attributes: attributes.get("type") == "Button" and
            attributes.get("text") == args.series_count_label,
            "series count")
    harmony_count = harmony_bounds(harmony_count_node)
    harmony_count_label = harmony_attributes(harmony_count_node).get("text", args.series_count_label)
    harmony_page_size = harmony_bounds(harmony_node(
        harmony_content_node,
        lambda attributes: attributes.get("id") == "library_page_size" or
        (attributes.get("type") == "Select" and attributes.get("text") == "20"),
        "page size"))
    try:
        harmony_filter_node = harmony_node(
            harmony_content_node,
            lambda attributes: attributes.get("id") == "library_filter_button",
            "filter button")
    except ValueError:
        harmony_filter_node = harmony_node(
            harmony_content_node,
            lambda attributes: attributes.get("type") == "Button" and
            attributes.get("clickable") == "true" and attributes.get("text") == "" and
            harmony_bounds({"attributes": attributes}).top == harmony_page_size.top and
            harmony_bounds({"attributes": attributes}).right < harmony_page_size.left,
            "filter button")
    harmony_filter = harmony_bounds(harmony_filter_node)

    harmony_cards = harmony_series_cards(harmony_content_node)
    harmony_first_card, harmony_second_card = harmony_cards[:2]

    android_page = android_page.scaled(android_scale)
    harmony_page = harmony_page.scaled(harmony_scale)
    android_pane = android_pane.scaled(android_scale) if android_pane is not None else None
    harmony_pane = harmony_pane.scaled(harmony_scale) if harmony_pane is not None else None
    android_content = android_content.scaled(android_scale)
    harmony_content = harmony_content.scaled(harmony_scale)
    android_values = [android_scope, android_count, android_filter, android_page_size,
                      android_first_card, android_second_card]
    harmony_values = [harmony_scope, harmony_count, harmony_filter, harmony_page_size,
                      harmony_first_card, harmony_second_card]
    android_scope, android_count, android_filter, android_page_size, android_first_card, android_second_card = [
        value.scaled(android_scale) for value in android_values]
    harmony_scope, harmony_count, harmony_filter, harmony_page_size, harmony_first_card, harmony_second_card = [
        value.scaled(harmony_scale) for value in harmony_values]

    metrics: dict[str, float] = {}

    def add_box(prefix: str, android: Bounds, harmony: Bounds,
                android_region: Bounds, harmony_region: Bounds,
                trailing: bool = False) -> None:
        metrics[f"{prefix}_top"] = position_similarity(
            android.top - android_region.top, harmony.top - harmony_region.top, logical_width)
        if trailing:
            metrics[f"{prefix}_right"] = position_similarity(
                android_region.right - android.right, harmony_region.right - harmony.right, logical_width)
        else:
            metrics[f"{prefix}_left"] = position_similarity(
                android.left - android_region.left, harmony.left - harmony_region.left, logical_width)
        metrics[f"{prefix}_width"] = size_similarity(android.width, harmony.width)
        metrics[f"{prefix}_height"] = size_similarity(android.height, harmony.height)

    android_scope_region = android_pane if android_pane is not None else android_content
    harmony_scope_region = harmony_pane if harmony_pane is not None else harmony_content
    add_box("scope", android_scope, harmony_scope, android_scope_region, harmony_scope_region)
    add_box("count", android_count, harmony_count, android_content, harmony_content)
    if android_count_label != harmony_count_label:
        # Count controls are content-sized. Different live datasets legitimately produce
        # different widths, while their placement, height and padding remain gated.
        metrics.pop("count_width")
    add_box("filter", android_filter, harmony_filter, android_content, harmony_content, trailing=True)
    add_box("page_size", android_page_size, harmony_page_size,
            android_content, harmony_content, trailing=True)
    if android_pane is not None and harmony_pane is not None:
        metrics["supporting_pane_width"] = size_similarity(android_pane.width, harmony_pane.width)
    metrics["first_card_top"] = position_similarity(
        android_first_card.top - android_content.top,
        harmony_first_card.top - harmony_content.top, logical_width)
    metrics["first_card_left"] = position_similarity(
        android_first_card.left - android_content.left,
        harmony_first_card.left - harmony_content.left, logical_width)
    metrics["first_card_width_fraction"] = size_similarity(
        android_first_card.width / android_content.width,
        harmony_first_card.width / harmony_content.width)
    metrics["card_gap"] = token_similarity(
        android_second_card.left - android_first_card.right,
        harmony_second_card.left - harmony_first_card.right)
    metrics["second_card_width_fraction"] = size_similarity(
        android_second_card.width / android_content.width,
        harmony_second_card.width / harmony_content.width)
    cards_are_complete = (android_first_card.bottom < android_content.bottom - 1 and
                          harmony_first_card.bottom < harmony_content.bottom - 1)
    if cards_are_complete:
        metrics["first_card_height_to_width"] = size_similarity(
            android_first_card.height / android_first_card.width,
            harmony_first_card.height / harmony_first_card.width)
        metrics["second_card_height_to_width"] = size_similarity(
            android_second_card.height / android_second_card.width,
            harmony_second_card.height / harmony_second_card.width)

    try:
        harmony_rail = harmony_bounds(harmony_node(
            harmony_root, lambda attributes: attributes.get("id") == "primary_navigation_rail",
            "primary_navigation_rail"))
    except ValueError:
        harmony_rail = None
    if harmony_rail is not None:
        android_nav = [android_navigation_item(android_root, label).scaled(android_scale)
                       for label in ("书库", "首页")]
        harmony_nav = [harmony_bounds(harmony_node(
            harmony_root, lambda attributes, component_id=component_id:
            attributes.get("id") == component_id, component_id)).scaled(harmony_scale)
            for component_id in ("nav_library", "nav_home")]
        harmony_rail = harmony_rail.scaled(harmony_scale)
        android_rail_width = android_page.left - min(value.left for value in android_nav)
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
        "layout_mode": layout_mode,
        "cards_complete": cards_are_complete,
        "anchors": {
            "android_series_count": android_count_label,
            "harmony_series_count": harmony_count_label,
            "first_title": args.first_title,
            "second_title": args.second_title,
        },
        "metrics_percent": {name: round(value, 4) for name, value in metrics.items()},
        "layout_similarity_percent": round(score, 4),
    }
    print(json.dumps(report, ensure_ascii=False, indent=2))
    if not math.isfinite(score) or score < args.min_score:
        print(f"library layout similarity {score:.2f}% is below {args.min_score:.2f}%", file=sys.stderr)
        return 1
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
