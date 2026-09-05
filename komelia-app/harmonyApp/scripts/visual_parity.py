#!/usr/bin/env python3
"""Measure Android/HarmonyOS visual parity without committing screenshots.

The compared screenshots must show the same state at the same logical width.
Callers provide a stable, content-independent region from each screenshot. The
script normalizes both regions, reports the raw cross-rasterizer pixel score,
and separately gates the repeated horizontal layout rhythm. The latter avoids
treating different system-font rasterizers as a layout failure.

ImageMagick is the only external dependency. Screenshots and normalized crops
stay in a temporary directory unless --keep-artifacts is supplied.
"""

from __future__ import annotations

import argparse
import json
import os
import re
import shutil
import statistics
import subprocess
import sys
import tempfile
from dataclasses import dataclass
from pathlib import Path


@dataclass(frozen=True)
class Region:
    x: int
    y: int
    width: int
    height: int

    @classmethod
    def parse(cls, value: str) -> "Region":
        try:
            x, y, width, height = (int(part.strip()) for part in value.split(","))
        except (TypeError, ValueError) as error:
            raise argparse.ArgumentTypeError(
                "region must be x,y,width,height using integer pixels"
            ) from error
        if min(x, y) < 0 or min(width, height) <= 0:
            raise argparse.ArgumentTypeError("region coordinates must be non-negative and dimensions positive")
        return cls(x=x, y=y, width=width, height=height)

    def crop_geometry(self) -> str:
        return f"{self.width}x{self.height}+{self.x}+{self.y}"


def run(command: list[str], *, allow_difference: bool = False) -> subprocess.CompletedProcess[str]:
    result = subprocess.run(command, capture_output=True, text=True, check=False)
    allowed_codes = {0, 1} if allow_difference else {0}
    if result.returncode not in allowed_codes:
        detail = result.stderr.strip() or result.stdout.strip()
        raise RuntimeError(f"command failed ({result.returncode}): {' '.join(command)}\n{detail}")
    return result


def identify_dimensions(magick: str, image: Path) -> tuple[int, int]:
    result = run([magick, "identify", "-format", "%w %h", str(image)])
    width, height = (int(value) for value in result.stdout.split())
    return width, height


def assert_region_within(image: Path, image_size: tuple[int, int], region: Region) -> None:
    width, height = image_size
    if region.x + region.width > width or region.y + region.height > height:
        raise ValueError(
            f"region {region} exceeds {image} dimensions {width}x{height}"
        )


def normalize_viewport(
    magick: str,
    source: Path,
    output: Path,
    target_width: int,
) -> None:
    run([
        magick,
        str(source),
        "-alpha",
        "off",
        "-resize",
        f"{target_width}x",
        str(output),
    ])


def crop_region(
    magick: str,
    source: Path,
    region: Region,
    output: Path,
    target_size: tuple[int, int],
) -> None:
    target_width, target_height = target_size
    run([
        magick,
        str(source),
        "-crop",
        region.crop_geometry(),
        "+repage",
        "-resize",
        f"{target_width}x{target_height}!",
        str(output),
    ])


def image_similarity(compare: str, first: Path, second: Path) -> float:
    result = run(
        [compare, "-metric", "SSIM", str(first), str(second), "null:"],
        allow_difference=True,
    )
    metric = result.stderr.strip() or result.stdout.strip()
    match = re.search(r"\(([-+0-9.eE]+)\)\s*$", metric)
    if match is None:
        raise RuntimeError(f"could not parse ImageMagick SSIM metric: {metric!r}")
    normalized_error = float(match.group(1))
    return max(0.0, min(100.0, (1.0 - normalized_error) * 100.0))


def read_column(magick: str, image: Path, x: int, height: int) -> list[tuple[float, float, float]]:
    result = run([
        magick,
        str(image),
        "-alpha",
        "off",
        "-depth",
        "8",
        "-crop",
        f"1x{height}+{x}+0",
        "+repage",
        "txt:-",
    ])
    pixels: list[tuple[float, float, float]] = []
    pattern = re.compile(r"0,(\d+): \(([-+0-9.eE]+),([-+0-9.eE]+),([-+0-9.eE]+)")
    for line in result.stdout.splitlines():
        match = pattern.match(line)
        if match is None:
            continue
        y = int(match.group(1))
        while len(pixels) <= y:
            pixels.append((0.0, 0.0, 0.0))
        pixels[y] = tuple(float(match.group(index)) for index in range(2, 5))
    if len(pixels) != height:
        raise RuntimeError(f"expected {height} sampled pixels from {image}, got {len(pixels)}")
    return pixels


def read_row(magick: str, image: Path, y: int, width: int) -> list[tuple[float, float, float]]:
    result = run([
        magick,
        str(image),
        "-alpha",
        "off",
        "-depth",
        "8",
        "-crop",
        f"{width}x1+0+{y}",
        "+repage",
        "txt:-",
    ])
    pixels: list[tuple[float, float, float]] = []
    pattern = re.compile(r"(\d+),0: \(([-+0-9.eE]+),([-+0-9.eE]+),([-+0-9.eE]+)")
    for line in result.stdout.splitlines():
        match = pattern.match(line)
        if match is None:
            continue
        x = int(match.group(1))
        while len(pixels) <= x:
            pixels.append((0.0, 0.0, 0.0))
        pixels[x] = tuple(float(match.group(index)) for index in range(2, 5))
    if len(pixels) != width:
        raise RuntimeError(f"expected {width} sampled pixels from {image}, got {len(pixels)}")
    return pixels


def changed_runs(
    pixels: list[tuple[float, float, float]],
    threshold: float,
    background: tuple[float, float, float] | None = None,
) -> list[tuple[int, int]]:
    if background is None:
        background = tuple(statistics.median(channel) for channel in zip(*pixels))
    changed = [
        max(abs(pixel[channel] - background[channel]) for channel in range(3)) >= threshold
        for pixel in pixels
    ]
    runs: list[tuple[int, int]] = []
    start: int | None = None
    for index, is_changed in enumerate([*changed, False]):
        if is_changed and start is None:
            start = index
        elif not is_changed and start is not None:
            end = index - 1
            runs.append((start, end))
            start = None
    return runs


def line_centers(
    pixels: list[tuple[float, float, float]],
    threshold: float,
    max_thickness: int,
) -> list[float]:
    runs = changed_runs(pixels, threshold)
    runs = [run for run in runs if run[1] - run[0] + 1 <= max_thickness]
    return [(start + end) / 2.0 for start, end in runs]


def surface_boundaries(
    pixels: list[tuple[float, float, float]],
    threshold: float,
    min_thickness: int,
    gap_tolerance: int,
) -> list[float]:
    """Return the leading and trailing edges of stable container surfaces.

    Text and icons create short runs at a quiet sample line. Filtering those
    out makes the same harness useful for rounded settings cards whose edges
    are broad color transitions instead of one-pixel dividers.
    """
    edge_size = max(1, min(20, len(pixels) // 4))
    edge_pixels = pixels[:edge_size] + pixels[-edge_size:]
    background = tuple(statistics.median(channel) for channel in zip(*edge_pixels))
    runs = changed_runs(pixels, threshold, background)
    merged: list[tuple[int, int]] = []
    for start, end in runs:
        if merged and start - merged[-1][1] - 1 <= gap_tolerance:
            merged[-1] = (merged[-1][0], end)
        else:
            merged.append((start, end))
    surfaces = [run for run in merged if run[1] - run[0] + 1 >= min_thickness]
    return [boundary for start, end in surfaces for boundary in (float(start), float(end))]


def rhythm_score(first_lines: list[float], second_lines: list[float]) -> tuple[float, float]:
    if len(first_lines) < 2 or len(second_lines) < 2:
        raise ValueError("at least two layout boundaries are required")
    if len(first_lines) != len(second_lines):
        raise ValueError(
            f"layout boundary count differs: Android={len(first_lines)}, HarmonyOS={len(second_lines)}"
        )
    first_intervals = [second - first for first, second in zip(first_lines, first_lines[1:])]
    second_intervals = [second - first for first, second in zip(second_lines, second_lines[1:])]
    relative_errors = [
        abs(first - second) / max((first + second) / 2.0, 1.0)
        for first, second in zip(first_intervals, second_intervals)
    ]
    interval_score = max(0.0, (1.0 - statistics.mean(relative_errors)) * 100.0)

    first_relative = [line - first_lines[0] for line in first_lines]
    second_relative = [line - second_lines[0] for line in second_lines]
    total_height = max(first_relative[-1], second_relative[-1], 1.0)
    boundary_score = max(
        0.0,
        (1.0 - statistics.mean(
            abs(first - second) / total_height
            for first, second in zip(first_relative, second_relative)
        )) * 100.0,
    )
    return interval_score, boundary_score


def parser() -> argparse.ArgumentParser:
    result = argparse.ArgumentParser(description=__doc__)
    result.add_argument("--android", required=True, type=Path)
    result.add_argument("--harmony", required=True, type=Path)
    result.add_argument("--android-region", required=True, type=Region.parse)
    result.add_argument("--harmony-region", required=True, type=Region.parse)
    result.add_argument("--target-width", type=int,
                        help="pixel width used to normalize both full screenshots; defaults to Android width")
    result.add_argument("--android-logical-width", type=float)
    result.add_argument("--harmony-logical-width", type=float)
    result.add_argument("--max-logical-width-delta-percent", type=float, default=1.0)
    result.add_argument("--sample-x", type=float, default=0.5,
                        help="quiet sample-line position within the normalized region (0..1)")
    result.add_argument("--scan-axis", choices=("vertical", "horizontal"), default="vertical",
                        help="vertical scans a column; horizontal scans a row")
    result.add_argument("--line-threshold", type=float, default=8.0)
    result.add_argument("--max-line-thickness", type=int, default=8)
    result.add_argument("--surface-min-thickness", type=int, default=0,
                        help="compare long container-surface edges instead of thin divider lines")
    result.add_argument("--surface-gap-tolerance", type=int, default=24,
                        help="merge short raster gaps within a detected container surface")
    result.add_argument("--expected-lines", type=int)
    result.add_argument("--min-layout-score", type=float, default=98.0)
    result.add_argument("--min-pixel-score", type=float, default=90.0,
                        help="cross-rasterizer guard only; layout parity is gated separately")
    result.add_argument("--keep-artifacts", type=Path)
    return result


def main() -> int:
    args = parser().parse_args()
    magick = os.environ.get("MAGICK") or shutil.which("magick")
    compare = os.environ.get("COMPARE") or shutil.which("compare")
    if magick is None or compare is None:
        raise RuntimeError("ImageMagick magick and compare executables are required")
    for image in (args.android, args.harmony):
        if not image.is_file():
            raise FileNotFoundError(image)
    if not 0.0 < args.sample_x < 1.0:
        raise ValueError("--sample-x must be between 0 and 1")

    android_size = identify_dimensions(magick, args.android)
    harmony_size = identify_dimensions(magick, args.harmony)
    if (args.android_logical_width is None) != (args.harmony_logical_width is None):
        raise ValueError("provide both logical widths or neither")
    logical_width_delta_percent: float | None = None
    if args.android_logical_width is not None and args.harmony_logical_width is not None:
        average_width = (args.android_logical_width + args.harmony_logical_width) / 2.0
        logical_width_delta_percent = abs(
            args.android_logical_width - args.harmony_logical_width
        ) / max(average_width, 1.0) * 100.0
        if logical_width_delta_percent > args.max_logical_width_delta_percent:
            raise ValueError(
                f"logical viewport widths differ by {logical_width_delta_percent:.3f}%, "
                f"above {args.max_logical_width_delta_percent:.3f}%"
            )

    temporary = tempfile.TemporaryDirectory(prefix="komelia-visual-parity-")
    work_dir = Path(temporary.name)
    if args.keep_artifacts is not None:
        work_dir = args.keep_artifacts
        work_dir.mkdir(parents=True, exist_ok=True)

    target_width = args.target_width or android_size[0]
    android_viewport = work_dir / "android-viewport.png"
    harmony_viewport = work_dir / "harmony-viewport.png"
    normalize_viewport(magick, args.android, android_viewport, target_width)
    normalize_viewport(magick, args.harmony, harmony_viewport, target_width)
    android_viewport_size = identify_dimensions(magick, android_viewport)
    harmony_viewport_size = identify_dimensions(magick, harmony_viewport)
    assert_region_within(android_viewport, android_viewport_size, args.android_region)
    assert_region_within(harmony_viewport, harmony_viewport_size, args.harmony_region)

    normalized_size = (args.android_region.width, args.android_region.height)
    android_normalized = work_dir / "android-normalized.png"
    harmony_normalized = work_dir / "harmony-normalized.png"
    crop_region(magick, android_viewport, args.android_region, android_normalized, normalized_size)
    crop_region(magick, harmony_viewport, args.harmony_region, harmony_normalized, normalized_size)

    pixel_score = image_similarity(compare, android_normalized, harmony_normalized)
    if not 0.0 <= args.sample_x <= 1.0:
        raise ValueError("sample position must be between 0 and 1")
    if args.scan_axis == "vertical":
        sample_line = round((normalized_size[0] - 1) * args.sample_x)
        android_pixels = read_column(magick, android_normalized, sample_line, normalized_size[1])
        harmony_pixels = read_column(magick, harmony_normalized, sample_line, normalized_size[1])
    else:
        sample_line = round((normalized_size[1] - 1) * args.sample_x)
        android_pixels = read_row(magick, android_normalized, sample_line, normalized_size[0])
        harmony_pixels = read_row(magick, harmony_normalized, sample_line, normalized_size[0])
    if args.surface_min_thickness > 0:
        android_lines = surface_boundaries(android_pixels, args.line_threshold, args.surface_min_thickness,
                                           args.surface_gap_tolerance)
        harmony_lines = surface_boundaries(harmony_pixels, args.line_threshold, args.surface_min_thickness,
                                           args.surface_gap_tolerance)
        layout_mode = "surface"
    else:
        android_lines = line_centers(android_pixels, args.line_threshold, args.max_line_thickness)
        harmony_lines = line_centers(harmony_pixels, args.line_threshold, args.max_line_thickness)
        layout_mode = "divider"
    if args.expected_lines is not None:
        if len(android_lines) != args.expected_lines or len(harmony_lines) != args.expected_lines:
            raise ValueError(
                f"expected {args.expected_lines} {args.scan_axis} scan boundaries, got "
                f"Android={len(android_lines)} and HarmonyOS={len(harmony_lines)}"
            )
    interval_score, boundary_score = rhythm_score(android_lines, harmony_lines)
    layout_score = min(interval_score, boundary_score)

    report = {
        "android_image_size": android_size,
        "harmony_image_size": harmony_size,
        "target_viewport_width": target_width,
        "android_normalized_viewport_size": android_viewport_size,
        "harmony_normalized_viewport_size": harmony_viewport_size,
        "logical_width_delta_percent": (
            round(logical_width_delta_percent, 4) if logical_width_delta_percent is not None else None
        ),
        "normalized_region_size": normalized_size,
        "scan_axis": args.scan_axis,
        "sample_line": sample_line,
        "layout_mode": layout_mode,
        "pixel_similarity_percent": round(pixel_score, 4),
        "layout_interval_similarity_percent": round(interval_score, 4),
        "layout_boundary_similarity_percent": round(boundary_score, 4),
        "layout_similarity_percent": round(layout_score, 4),
        "android_boundaries": [round(value, 2) for value in android_lines],
        "harmony_boundaries": [round(value, 2) for value in harmony_lines],
        "artifacts": str(work_dir) if args.keep_artifacts is not None else None,
    }
    print(json.dumps(report, ensure_ascii=False, indent=2))

    failed = False
    if layout_score < args.min_layout_score:
        print(
            f"layout similarity {layout_score:.2f}% is below {args.min_layout_score:.2f}%",
            file=sys.stderr,
        )
        failed = True
    if pixel_score < args.min_pixel_score:
        print(
            f"pixel similarity {pixel_score:.2f}% is below {args.min_pixel_score:.2f}%",
            file=sys.stderr,
        )
        failed = True
    temporary.cleanup()
    return 1 if failed else 0


if __name__ == "__main__":
    raise SystemExit(main())
