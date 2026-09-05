#!/usr/bin/env python3
"""Gate Android/HarmonyOS series-detail geometry from same-state layout dumps.

The gate deliberately compares stable structure instead of glyph pixels or
content-dependent wrapped metadata. Both captures must use the same logical
viewport width, language, theme, route, account permissions, and series.
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


def android_text_prefix(root: ElementTree.Element, prefix: str) -> ElementTree.Element:
    for node in root.iter("node"):
        if node.attrib.get("text", "").startswith(prefix):
            return node
    raise ValueError(f"Android text prefix missing: {prefix!r}")


def android_clickable(root: ElementTree.Element, text: str) -> ElementTree.Element:
    matches = [
        node for node in root.iter("node")
        if node.attrib.get("clickable") == "true" and text in android_descendant_texts(node)
    ]
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
    raise ValueError("Android series detail content root missing")


def android_toolbar_buttons(root: ElementTree.Element, logical_scale: float,
                            content: Bounds) -> list[Bounds]:
    candidates = [
        android_bounds(node).scaled(logical_scale)
        for node in root.iter("node")
        if node.attrib.get("clickable") == "true"
        and android_bounds(node).left >= content.left
        and android_bounds(node).top >= content.top
        and (android_bounds(node).top - content.top) / logical_scale < 70
        and android_bounds(node).height / logical_scale >= 40
    ]
    if candidates:
        toolbar_top = min(value.top for value in candidates)
        candidates = [value for value in candidates if abs(value.top - toolbar_top) <= 1.0]
    if len(candidates) < 3:
        raise ValueError("Android toolbar buttons missing")
    return sorted(candidates, key=lambda value: value.left)[:3]


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


def android_cover(root: ElementTree.Element, logical_scale: float, content: Bounds) -> Bounds:
    candidates: list[Bounds] = []
    for node in root.iter("node"):
        if "bounds" not in node.attrib:
            continue
        bounds = android_bounds(node)
        if bounds.left < content.left or bounds.top < content.top:
            continue
        scaled = bounds.scaled(logical_scale)
        if scaled.height <= 0 or scaled.width < 100:
            continue
        ratio = scaled.width / scaled.height
        if 0.68 <= ratio <= 0.73:
            candidates.append(scaled)
    if not candidates:
        raise ValueError("Android series cover missing")
    return min(candidates, key=lambda value: (value.top, value.left, value.width))


def size_similarity(first: float, second: float) -> float:
    average = max((abs(first) + abs(second)) / 2.0, 1e-9)
    return max(0.0, (1.0 - abs(first - second) / average) * 100.0)


def token_similarity(first: float, second: float, rounding_tolerance: float = 1.0) -> float:
    average = max((abs(first) + abs(second)) / 2.0, 1.0)
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
    parser.add_argument("--title", default="从零开始的异世界生活")
    parser.add_argument("--release", default="发行年份 2018")
    parser.add_argument("--library", default="feiniu")
    parser.add_argument("--status", default="连载中")
    parser.add_argument("--language", default="zh-CN")
    parser.add_argument("--summary-prefix", default="来自第 12 本书的简介：")
    parser.add_argument("--publisher", default="青文出版社")
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
    android_page = android_content_root(android_root, android_title_node).scaled(android_scale)
    android_title = android_bounds(android_title_node).scaled(android_scale)
    android_release = android_bounds(android_text(android_root, args.release)).scaled(android_scale)
    android_library = android_bounds(android_clickable(android_root, args.library)).scaled(android_scale)
    android_status = android_bounds(android_clickable(android_root, args.status)).scaled(android_scale)
    android_language = android_bounds(android_clickable(android_root, args.language)).scaled(android_scale)
    android_summary = android_bounds(android_text_prefix(android_root, args.summary_prefix)).scaled(android_scale)
    android_buttons = android_toolbar_buttons(
        android_root, android_scale, android_page.scaled(1.0 / android_scale))
    android_cover_bounds = android_cover(
        android_root, android_scale, android_page.scaled(1.0 / android_scale))

    harmony_page = harmony_node(
        harmony_root, lambda attributes: attributes.get("id") == "series_detail_page", "series_detail_page")
    harmony_page_bounds = harmony_bounds(harmony_page).scaled(harmony_scale)
    try:
        harmony_rail_node = harmony_node(
            harmony_root, lambda attributes: attributes.get("id") == "primary_navigation_rail",
            "primary_navigation_rail")
    except ValueError:
        harmony_rail_node = None
    presentation = "rail" if harmony_rail_node is not None else "bottom_bar"
    harmony_title_node = harmony_node(
        harmony_page,
        lambda attributes: attributes.get("type") == "Text" and attributes.get("text") == args.title,
        "series title")
    harmony_title = harmony_bounds(harmony_title_node).scaled(harmony_scale)
    toolbar = harmony_ancestor(
        harmony_title_node, harmony_parents,
        lambda attributes: attributes.get("type") == "Row", "series toolbar")
    harmony_buttons = sorted([
        harmony_bounds(node).scaled(harmony_scale) for node in toolbar.get("children", [])
        if harmony_attributes(node).get("type") == "Button"
    ], key=lambda value: value.left)
    if len(harmony_buttons) != 3:
        raise ValueError(f"expected three HarmonyOS toolbar buttons, found {len(harmony_buttons)}")

    def h_text(text: str) -> dict[str, Any]:
        return harmony_node(
            harmony_page,
            lambda attributes: attributes.get("type") in {"Text", "Button"} and attributes.get("text") == text,
            text)

    harmony_release = harmony_bounds(h_text(args.release)).scaled(harmony_scale)
    library_text = h_text(args.library)
    harmony_library = harmony_bounds(harmony_ancestor(
        library_text, harmony_parents,
        lambda attributes: attributes.get("type") == "Button", "library chip")).scaled(harmony_scale)
    harmony_status = harmony_bounds(h_text(args.status)).scaled(harmony_scale)
    harmony_language = harmony_bounds(h_text(args.language)).scaled(harmony_scale)
    harmony_summary = harmony_bounds(harmony_node(
        harmony_page,
        lambda attributes: attributes.get("type") == "Text" and
        attributes.get("text", "").startswith(args.summary_prefix), "series summary")).scaled(harmony_scale)
    harmony_cover = harmony_bounds(harmony_node(
        harmony_page, lambda attributes: attributes.get("id") == "series_detail_cover", "series cover"
    )).scaled(harmony_scale)

    # Detail bodies are capped and centered independently from the destination
    # page in Full layouts. Compare body content against that semantic container
    # so platform-specific landscape safe insets do not look like UI drift.
    try:
        harmony_body = harmony_bounds(harmony_node(
            harmony_page, lambda attributes: attributes.get("id") == "series_detail_content",
            "series_detail_content")).scaled(harmony_scale)
    except ValueError:
        harmony_body = harmony_page_bounds
    horizontal_padding = 12.0 if logical_width < 600 else 16.0 if logical_width < 840 else 24.0
    android_body = Bounds(
        left=min(android_cover_bounds.left, android_summary.left) - horizontal_padding,
        top=min(android_cover_bounds.top, android_release.top),
        right=max(android_summary.right, android_release.right) + horizontal_padding,
        bottom=max(android_summary.bottom, android_cover_bounds.bottom),
    )

    metrics: dict[str, float] = {}

    def position(name: str, android: float, harmony: float) -> None:
        metrics[name] = position_similarity(android, harmony, logical_width)

    def size(name: str, android: float, harmony: float) -> None:
        metrics[name] = size_similarity(android, harmony)

    def leading(value: Bounds, content: Bounds) -> float:
        return value.left - content.left if presentation == "rail" else value.left

    def top(value: Bounds, content: Bounds) -> float:
        return value.top - content.top if presentation == "rail" else value.top

    def trailing(value: Bounds, content: Bounds) -> float:
        return content.right - value.right

    position("title_left", leading(android_title, android_page),
             leading(harmony_title, harmony_page_bounds))
    position("title_top", top(android_title, android_page),
             top(harmony_title, harmony_page_bounds))
    if presentation == "rail":
        position("title_trailing", trailing(android_title, android_page),
                 trailing(harmony_title, harmony_page_bounds))
    else:
        position("title_right", android_title.right, harmony_title.right)

    for index, label in enumerate(("back", "more", "download")):
        android_button = android_buttons[index]
        harmony_button = harmony_buttons[index]
        position(f"{label}_top", top(android_button, android_page),
                 top(harmony_button, harmony_page_bounds))
        size(f"{label}_width", android_button.width, harmony_button.width)
        size(f"{label}_height", android_button.height, harmony_button.height)
    position("back_left", leading(android_buttons[0], android_page),
             leading(harmony_buttons[0], harmony_page_bounds))
    if presentation == "rail":
        position("download_trailing", trailing(android_buttons[2], android_page),
                 trailing(harmony_buttons[2], harmony_page_bounds))
    else:
        position("download_right", android_buttons[2].right, harmony_buttons[2].right)
    metrics["action_gap"] = token_similarity(
        android_buttons[2].left - android_buttons[1].right,
        harmony_buttons[2].left - harmony_buttons[1].right)

    for label, android_value, harmony_value in (
        ("release", android_release, harmony_release),
        ("library", android_library, harmony_library),
        ("status", android_status, harmony_status),
        ("language", android_language, harmony_language),
    ):
        position(f"{label}_left", leading(android_value, android_body),
                 leading(harmony_value, harmony_body))
        position(f"{label}_top", top(android_value, android_body),
                 top(harmony_value, harmony_body))

    # Android exposes the release value as glyph bounds while ArkUI exposes the
    # full-width Text container. Status and language have the inverse mismatch:
    # Android reports the clickable chip, but ArkUI reports its Text container.
    # Their anchors are stable; only compare dimensions where both trees expose
    # the same semantic container.
    size("library_width", android_library.width, harmony_library.width)
    size("library_height", android_library.height, harmony_library.height)
    size("status_height", android_status.height, harmony_status.height)
    size("language_height", android_language.height, harmony_language.height)

    position("cover_left", leading(android_cover_bounds, android_body),
             leading(harmony_cover, harmony_body))
    position("cover_top", top(android_cover_bounds, android_body),
             top(harmony_cover, harmony_body))
    size("cover_width", android_cover_bounds.width, harmony_cover.width)
    size("cover_height", android_cover_bounds.height, harmony_cover.height)
    size("cover_ratio", android_cover_bounds.width / android_cover_bounds.height,
         harmony_cover.width / harmony_cover.height)
    expected_cover_width = 116.0 if logical_width < 840 else 220.0
    size("cover_width_spec", expected_cover_width, harmony_cover.width)
    size("cover_ratio_spec", 0.703, harmony_cover.width / harmony_cover.height)

    position("summary_left", leading(android_summary, android_body),
             leading(harmony_summary, harmony_body))
    position("summary_top", top(android_summary, android_body),
             top(harmony_summary, harmony_body))
    position("summary_trailing", trailing(android_summary, android_body),
             trailing(harmony_summary, harmony_body))

    if presentation == "bottom_bar":
        android_expand = android_bounds(android_clickable(android_root, "展开")).scaled(android_scale)
        android_publisher_label = android_bounds(android_text(android_root, "出版商")).scaled(android_scale)
        android_publisher = android_bounds(android_clickable(android_root, args.publisher)).scaled(android_scale)
        android_type_label = android_bounds(android_text(android_root, "类型")).scaled(android_scale)
        harmony_expand = harmony_bounds(h_text("展开")).scaled(harmony_scale)
        harmony_publisher_label = harmony_bounds(h_text("出版商")).scaled(harmony_scale)
        harmony_publisher = harmony_bounds(h_text(args.publisher)).scaled(harmony_scale)
        harmony_type_label = harmony_bounds(h_text("类型")).scaled(harmony_scale)

        size("summary_nine_line_height", android_summary.height, harmony_summary.height)
        position("expand_top", top(android_expand, android_body),
                 top(harmony_expand, harmony_body))
        size("expand_height", android_expand.height, harmony_expand.height)
        position("publisher_label_left", leading(android_publisher_label, android_body),
                 leading(harmony_publisher_label, harmony_body))
        position("publisher_label_top", top(android_publisher_label, android_body),
                 top(harmony_publisher_label, harmony_body))
        size("publisher_label_height", android_publisher_label.height, harmony_publisher_label.height)
        position("publisher_chip_left", leading(android_publisher, android_body),
                 leading(harmony_publisher, harmony_body))
        position("type_label_top", top(android_type_label, android_body),
                 top(harmony_type_label, harmony_body))
        # The Android accessibility tree expands clickable chip bounds vertically,
        # while ArkUI reports the visual Text node. Compare the row stride as a
        # viewport-relative position rather than as a fixed token dimension.
        metrics["metadata_row_stride"] = position_similarity(
            android_type_label.top - android_publisher_label.top,
            harmony_type_label.top - harmony_publisher_label.top,
            logical_width)
    else:
        assert harmony_rail_node is not None
        harmony_rail = harmony_bounds(harmony_rail_node).scaled(harmony_scale)
        android_nav = [android_navigation_item(android_root, label).scaled(android_scale)
                       for label in ("书库", "首页")]
        harmony_nav = [harmony_bounds(harmony_node(
            harmony_root, lambda attributes, component_id=component_id:
            attributes.get("id") == component_id, component_id)).scaled(harmony_scale)
            for component_id in ("nav_library", "nav_home")]
        android_rail_width = android_page.left - min(value.left for value in android_nav)
        size("rail_width", android_rail_width, harmony_rail.width)
        size("nav_item_width", android_nav[0].width, harmony_nav[0].width)
        metrics["nav_item_height"] = token_similarity(android_nav[0].height, harmony_nav[0].height)
        metrics["nav_item_gap"] = token_similarity(
            android_nav[1].top - android_nav[0].bottom,
            harmony_nav[1].top - harmony_nav[0].bottom)

    score = min(metrics.values())
    payload = {
        "score": round(score, 4),
        "minimum": args.min_score,
        "passed": score >= args.min_score,
        "presentation": presentation,
        "metrics": {name: round(value, 4) for name, value in sorted(metrics.items())},
    }
    print(json.dumps(payload, ensure_ascii=False, indent=2))
    if not math.isfinite(score) or score < args.min_score:
        return 1
    return 0


if __name__ == "__main__":
    try:
        sys.exit(main())
    except (KeyError, ValueError, ElementTree.ParseError, json.JSONDecodeError) as error:
        print(f"series detail parity gate failed: {error}", file=sys.stderr)
        sys.exit(2)
