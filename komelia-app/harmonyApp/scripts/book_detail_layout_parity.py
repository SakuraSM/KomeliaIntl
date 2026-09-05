#!/usr/bin/env python3
"""Gate stable Android/HarmonyOS book-detail geometry from same-book dumps.

Read progress and download state are deliberately excluded: those states can
change independently between captures and legitimately move the action row.
The gate compares the shared toolbar geometry, cover, parent-series control,
volume summary, split read control dimensions, file-column alignment and rail.
Permission-dependent toolbar action counts are deliberately excluded; the
trailing action slot remains a stable adaptive-layout anchor.
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

    @staticmethod
    def union(values: Iterable["Bounds"]) -> "Bounds":
        items = list(values)
        if not items:
            raise ValueError("cannot union empty bounds")
        return Bounds(min(value.left for value in items), min(value.top for value in items),
                      max(value.right for value in items), max(value.bottom for value in items))


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


def android_descendant_texts(node: ElementTree.Element) -> set[str]:
    return {child.attrib.get("text", "") for child in node.iter() if child.attrib.get("text", "")}


def android_text(root: ElementTree.Element, text: str) -> ElementTree.Element:
    for node in root.iter("node"):
        if node.attrib.get("text") == text:
            return node
    raise ValueError(f"Android text missing: {text!r}")


def android_clickable(root: ElementTree.Element, text: str) -> ElementTree.Element:
    matches = [node for node in root.iter("node") if node.attrib.get("clickable") == "true"
               and text in android_descendant_texts(node)]
    if not matches:
        raise ValueError(f"Android clickable missing: {text!r}")
    return min(matches, key=lambda node: android_bounds(node).width * android_bounds(node).height)


def android_content_root(root: ElementTree.Element, title_node: ElementTree.Element) -> Bounds:
    parents = {child: node for node in root.iter() for child in node}
    title = android_bounds(title_node)
    current = title_node
    while current in parents:
        current = parents[current]
        if "bounds" not in current.attrib:
            continue
        bounds = android_bounds(current)
        if bounds.width >= title.width + 80 and bounds.height >= title.height * 5:
            return bounds
    raise ValueError("Android book detail content root missing")


def android_toolbar_button(root: ElementTree.Element, content: Bounds, logical_scale: float,
                           description: str | None = None) -> Bounds:
    matches: list[Bounds] = []
    for node in root.iter("node"):
        if node.attrib.get("clickable") != "true" or "bounds" not in node.attrib:
            continue
        bounds = android_bounds(node)
        if bounds.left < content.left or bounds.top < content.top:
            continue
        if (bounds.top - content.top) / logical_scale > 4 or bounds.height / logical_scale < 40:
            continue
        if description is not None and not any(
                child.attrib.get("content-desc") == description for child in node.iter("node")):
            continue
        matches.append(bounds.scaled(logical_scale))
    if not matches:
        raise ValueError(f"Android toolbar button missing: {description or 'action'}")
    return min(matches, key=lambda value: value.left) if description is not None else max(matches, key=lambda value: value.left)


def android_navigation_item(root: ElementTree.Element, label: str) -> Bounds:
    text = android_text(root, label)
    text_bounds = android_bounds(text)
    candidates = [android_bounds(node) for node in root.iter("node")
                  if label in android_descendant_texts(node)
                  and "bounds" in node.attrib
                  and android_bounds(node).width >= text_bounds.width + 80
                  and android_bounds(node).height >= text_bounds.height + 60]
    if not candidates:
        raise ValueError(f"Android navigation item missing: {label!r}")
    return min(candidates, key=lambda value: value.width * value.height)


def android_cover(root: ElementTree.Element, scale: float, content: Bounds) -> Bounds:
    candidates: list[Bounds] = []
    for node in root.iter("node"):
        if "bounds" not in node.attrib:
            continue
        bounds = android_bounds(node)
        if bounds.left < content.left or bounds.top < content.top:
            continue
        logical = bounds.scaled(scale)
        if logical.height <= 0 or logical.width < 100:
            continue
        if 0.68 <= logical.width / logical.height <= 0.73:
            candidates.append(logical)
    if not candidates:
        raise ValueError("Android book cover missing")
    return min(candidates, key=lambda value: (value.top, value.left, value.width))


def android_visual_button(clickable: ElementTree.Element) -> Bounds:
    buttons = [android_bounds(child) for child in clickable.iter("node")
               if child.attrib.get("class") == "android.widget.Button" and "bounds" in child.attrib]
    return min(buttons, key=lambda value: value.width * value.height) if buttons else android_bounds(clickable)


def size_similarity(first: float, second: float) -> float:
    average = max((abs(first) + abs(second)) / 2.0, 1e-9)
    return max(0.0, (1.0 - abs(first - second) / average) * 100.0)


def token_similarity(first: float, second: float, tolerance: float = 1.0) -> float:
    average = max((abs(first) + abs(second)) / 2.0, 1.0)
    difference = max(0.0, abs(first - second) - tolerance)
    return max(0.0, (1.0 - difference / average) * 100.0)


def position_similarity(first: float, second: float, logical_width: float) -> float:
    return max(0.0, (1.0 - abs(first - second) / max(logical_width, 1e-9)) * 100.0)


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser()
    parser.add_argument("--android-layout", required=True, type=Path)
    parser.add_argument("--harmony-layout", required=True, type=Path)
    parser.add_argument("--android-logical-width", required=True, type=float)
    parser.add_argument("--harmony-logical-width", required=True, type=float)
    parser.add_argument("--title", required=True)
    parser.add_argument("--series", required=True)
    parser.add_argument("--volume", required=True)
    parser.add_argument("--size-value", required=True)
    parser.add_argument("--format-value", required=True)
    parser.add_argument("--summary-prefix", default="")
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

    android_title_node = android_text(android_root, args.title)
    android_content_physical = android_content_root(android_root, android_title_node)
    android_content = android_content_physical.scaled(android_scale)
    android_body_width = min(android_content.width, 1200.0)
    android_body_left = android_content.left + (android_content.width - android_body_width) / 2.0
    android_body = Bounds(android_body_left, android_content.top,
                          android_body_left + android_body_width, android_content.bottom)
    android_title = android_bounds(android_title_node).scaled(android_scale)
    android_back = android_toolbar_button(android_root, android_content_physical, android_scale, "返回")
    android_toolbar_trailing = android_toolbar_button(
        android_root, android_content_physical, android_scale)
    android_cover_bounds = android_cover(android_root, android_scale, android_content_physical)
    android_series_clickable = android_clickable(android_root, args.series)
    android_series = android_visual_button(android_series_clickable).scaled(android_scale)
    android_volume = android_bounds(android_text(android_root, args.volume)).scaled(android_scale)
    android_read = android_clickable(android_root, "阅读")
    android_spinner = min(
        (node for node in android_root.iter("node") if node.attrib.get("class") == "android.widget.Spinner"),
        key=lambda node: abs(android_bounds(node).top - android_bounds(android_read).top))
    android_read_control = Bounds.union((android_bounds(android_read), android_bounds(android_spinner))).scaled(android_scale)
    android_size_label = android_bounds(android_text(android_root, "大小")).scaled(android_scale)
    android_size_value = android_bounds(android_text(android_root, args.size_value)).scaled(android_scale)
    android_format_label = android_bounds(android_text(android_root, "格式")).scaled(android_scale)
    android_format_value = android_bounds(android_text(android_root, args.format_value)).scaled(android_scale)

    harmony_page = harmony_node(harmony_root,
        lambda attributes: attributes.get("id") == "book_detail_page", "book_detail_page")
    harmony_content = harmony_bounds(harmony_page).scaled(harmony_scale)
    try:
        harmony_body = harmony_bounds(harmony_node(harmony_page,
            lambda attributes: attributes.get("id") == "book_detail_content",
            "book_detail_content")).scaled(harmony_scale)
    except ValueError:
        harmony_body = harmony_content
    harmony_title_node = harmony_node(harmony_page,
        lambda attributes: attributes.get("type") == "Text" and attributes.get("text") == args.title,
        "book title")
    harmony_title = harmony_bounds(harmony_title_node).scaled(harmony_scale)
    harmony_toolbar = harmony_ancestor(harmony_title_node, harmony_parents,
        lambda attributes: attributes.get("type") == "Row", "book toolbar")
    harmony_toolbar_buttons = sorted(
        (harmony_bounds(node).scaled(harmony_scale) for node in harmony_toolbar.get("children", [])
         if harmony_attributes(node).get("type") == "Button"),
        key=lambda value: value.left)
    if not harmony_toolbar_buttons:
        raise ValueError("HarmonyOS toolbar buttons missing")
    harmony_back = harmony_toolbar_buttons[0]
    harmony_toolbar_trailing = harmony_toolbar_buttons[-1]
    harmony_cover_bounds = harmony_bounds(harmony_node(harmony_page,
        lambda attributes: attributes.get("id") == "book_detail_cover", "book cover")).scaled(harmony_scale)
    harmony_series_text = harmony_node(harmony_page,
        lambda attributes: attributes.get("type") == "Text" and attributes.get("text") == args.series,
        "series label")
    harmony_series = harmony_bounds(harmony_ancestor(harmony_series_text, harmony_parents,
        lambda attributes: attributes.get("type") == "Button", "series button")).scaled(harmony_scale)
    harmony_volume = harmony_bounds(harmony_node(harmony_page,
        lambda attributes: attributes.get("type") == "Text" and attributes.get("text") == args.volume,
        "volume summary")).scaled(harmony_scale)
    harmony_read_button = harmony_node(harmony_page,
        lambda attributes: attributes.get("id") == "book_detail_read", "read button")
    harmony_read_control = harmony_bounds(harmony_ancestor(harmony_read_button, harmony_parents,
        lambda attributes: attributes.get("type") == "Row" and attributes.get("bounds") is not None,
        "split read control")).scaled(harmony_scale)

    def h_text(text: str) -> Bounds:
        return harmony_bounds(harmony_node(harmony_page,
            lambda attributes: attributes.get("type") == "Text" and attributes.get("text") == text,
            text)).scaled(harmony_scale)

    harmony_size_label = h_text("大小")
    harmony_size_value = h_text(args.size_value)
    harmony_format_label = h_text("格式")
    harmony_format_value = h_text(args.format_value)

    android_summary: Bounds | None = None
    harmony_summary: Bounds | None = None
    if args.summary_prefix:
        android_summary_node = next((node for node in android_root.iter("node")
            if node.attrib.get("text", "").startswith(args.summary_prefix)), None)
        if android_summary_node is None:
            raise ValueError(f"Android summary missing: {args.summary_prefix!r}")
        android_summary = android_bounds(android_summary_node).scaled(android_scale)
        harmony_summary_node = harmony_node(harmony_page,
            lambda attributes: attributes.get("id") == "book_detail_summary" and
            attributes.get("text", "").startswith(args.summary_prefix), "book summary")
        harmony_summary = harmony_bounds(harmony_summary_node).scaled(harmony_scale)

    metrics: dict[str, float] = {}

    def leading(value: Bounds, content: Bounds) -> float:
        return value.left - content.left

    def top(value: Bounds, content: Bounds) -> float:
        return value.top - content.top

    def position(name: str, android: float, harmony: float) -> None:
        metrics[name] = position_similarity(android, harmony, logical_width)

    def size(name: str, android: float, harmony: float) -> None:
        metrics[name] = size_similarity(android, harmony)

    for label, android_value, harmony_value in (
        ("title", android_title, harmony_title),
        ("back", android_back, harmony_back),
    ):
        position(f"{label}_left", leading(android_value, android_content),
                 leading(harmony_value, harmony_content))
    metrics["toolbar_trailing_end"] = token_similarity(
        android_content.right - android_toolbar_trailing.right,
        harmony_content.right - harmony_toolbar_trailing.right)

    for label, android_value, harmony_value in (
        ("cover", android_cover_bounds, harmony_cover_bounds),
        ("series", android_series, harmony_series),
        ("volume", android_volume, harmony_volume),
        ("read", android_read_control, harmony_read_control),
        ("size_label", android_size_label, harmony_size_label),
        ("size_value", android_size_value, harmony_size_value),
        ("format_label", android_format_label, harmony_format_label),
        ("format_value", android_format_value, harmony_format_value),
    ):
        position(f"{label}_left", leading(android_value, android_body),
                 leading(harmony_value, harmony_body))

    for label, android_value, harmony_value in (
        ("title", android_title, harmony_title),
        ("back", android_back, harmony_back),
        ("toolbar_trailing", android_toolbar_trailing, harmony_toolbar_trailing),
        ("cover", android_cover_bounds, harmony_cover_bounds),
        ("series", android_series, harmony_series),
        ("volume", android_volume, harmony_volume),
    ):
        position(f"{label}_top", top(android_value, android_content), top(harmony_value, harmony_content))

    for label, android_value, harmony_value in (
        ("back", android_back, harmony_back),
        ("toolbar_trailing", android_toolbar_trailing, harmony_toolbar_trailing),
        ("cover", android_cover_bounds, harmony_cover_bounds),
        ("series", android_series, harmony_series),
        ("read", android_read_control, harmony_read_control),
    ):
        size(f"{label}_width", android_value.width, harmony_value.width)
        size(f"{label}_height", android_value.height, harmony_value.height)

    size("cover_ratio", android_cover_bounds.width / android_cover_bounds.height,
         harmony_cover_bounds.width / harmony_cover_bounds.height)
    size("file_label_width", android_size_label.width, harmony_size_label.width)
    metrics["file_row_stride"] = token_similarity(
        android_format_label.top - android_size_label.top,
        harmony_format_label.top - harmony_size_label.top)
    metrics["file_value_alignment"] = token_similarity(
        android_size_value.left - android_size_label.left,
        harmony_size_value.left - harmony_size_label.left)
    metrics["file_format_value_alignment"] = token_similarity(
        android_format_value.left - android_format_label.left,
        harmony_format_value.left - harmony_format_label.left)
    if android_summary is not None and harmony_summary is not None:
        position("summary_left", leading(android_summary, android_body),
                 leading(harmony_summary, harmony_body))
        position("summary_top", top(android_summary, android_content),
                 top(harmony_summary, harmony_content))
        position("summary_trailing", android_body.right - android_summary.right,
                 harmony_body.right - harmony_summary.right)
        metrics["summary_height"] = token_similarity(android_summary.height, harmony_summary.height)

    harmony_rail = harmony_bounds(harmony_node(harmony_root,
        lambda attributes: attributes.get("id") == "primary_navigation_rail", "primary_navigation_rail")
    ).scaled(harmony_scale)
    android_navigation = [android_navigation_item(android_root, label).scaled(android_scale)
                          for label in ("书库", "首页")]
    harmony_navigation = [harmony_bounds(harmony_node(harmony_root,
        lambda attributes, item_id=item_id: attributes.get("id") == item_id, item_id)).scaled(harmony_scale)
        for item_id in ("nav_library", "nav_home")]
    size("rail_width", android_content.left - min(value.left for value in android_navigation), harmony_rail.width)
    size("navigation_width", android_navigation[0].width, harmony_navigation[0].width)
    metrics["navigation_height"] = token_similarity(android_navigation[0].height, harmony_navigation[0].height)

    score = min(metrics.values())
    payload = {
        "score": round(score, 4),
        "minimum": args.min_score,
        "passed": score >= args.min_score,
        "scope": ("stable same-book adaptive anchors; permission-dependent toolbar action counts and "
                  "progress/download-dependent vertical positions excluded"),
        "metrics": {name: round(value, 4) for name, value in sorted(metrics.items())},
    }
    print(json.dumps(payload, ensure_ascii=False, indent=2))
    return 0 if math.isfinite(score) and score >= args.min_score else 1


if __name__ == "__main__":
    try:
        sys.exit(main())
    except (KeyError, ValueError, ElementTree.ParseError, json.JSONDecodeError, StopIteration) as error:
        print(f"book detail parity gate failed: {error}", file=sys.stderr)
        sys.exit(2)
