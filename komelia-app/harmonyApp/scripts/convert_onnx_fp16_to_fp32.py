#!/usr/bin/env python3
"""Normalize an FP16 ONNX graph to FP32 for MindSpore Lite conversion.

This is a build-time utility only. HarmonyOS does not ship a Python runtime.
"""

from __future__ import annotations

import argparse
from pathlib import Path

import numpy as np
import onnx
from onnx import TensorProto, helper, numpy_helper


def convert_tensor(tensor: TensorProto) -> TensorProto:
    if tensor.data_type != TensorProto.FLOAT16:
        return tensor
    converted = numpy_helper.from_array(
        numpy_helper.to_array(tensor).astype(np.float32),
        name=tensor.name,
    )
    converted.doc_string = tensor.doc_string
    return converted


def convert_value_info(value_info: onnx.ValueInfoProto) -> None:
    tensor_type = value_info.type.tensor_type
    if tensor_type.elem_type == TensorProto.FLOAT16:
        tensor_type.elem_type = TensorProto.FLOAT


def replace_repeated(container, values) -> None:
    del container[:]
    container.extend(values)


def normalize_graph(graph: onnx.GraphProto) -> None:
    replace_repeated(graph.initializer, [convert_tensor(value) for value in graph.initializer])
    for sparse in graph.sparse_initializer:
        converted = convert_tensor(sparse.values)
        if converted is not sparse.values:
            sparse.values.CopyFrom(converted)

    for value_info in [*graph.input, *graph.output, *graph.value_info]:
        convert_value_info(value_info)

    for node in graph.node:
        for attribute in node.attribute:
            if attribute.type == onnx.AttributeProto.TENSOR:
                attribute.t.CopyFrom(convert_tensor(attribute.t))
            elif attribute.type == onnx.AttributeProto.TENSORS:
                replace_repeated(attribute.tensors, [convert_tensor(value) for value in attribute.tensors])
            elif node.op_type == "Cast" and attribute.name == "to" and attribute.i == TensorProto.FLOAT16:
                attribute.i = TensorProto.FLOAT
            elif attribute.type == onnx.AttributeProto.GRAPH:
                normalize_graph(attribute.g)
            elif attribute.type == onnx.AttributeProto.GRAPHS:
                for child in attribute.graphs:
                    normalize_graph(child)


def set_static_input_shape(model: onnx.ModelProto, input_name: str, shape: list[int]) -> None:
    selected = next((value for value in model.graph.input if value.name == input_name), None)
    if selected is None:
        raise ValueError(f"Input {input_name!r} was not found")
    dimensions = selected.type.tensor_type.shape.dim
    if len(dimensions) != len(shape):
        raise ValueError(f"Input {input_name!r} has {len(dimensions)} dimensions, expected {len(shape)}")
    for dimension, size in zip(dimensions, shape):
        dimension.ClearField("dim_param")
        dimension.dim_value = size


def replace_leading_pixel_unshuffle(model: onnx.ModelProto, input_name: str) -> None:
    """Replace PyTorch's six-dimensional pixel-unshuffle export with SpaceToDepth."""
    nodes = list(model.graph.node)
    simplified_ops = ["Unsqueeze", "Reshape", "Transpose", "Reshape", "Squeeze"]
    dynamic_ops = [
        "Shape", "Constant", "Gather", "Shape", "Constant", "Gather", "Constant",
        "Unsqueeze", "Constant", "Reshape", "Constant", "Reshape", "Transpose",
        "Constant", "Reshape", "Constant", "Squeeze",
    ]
    if [node.op_type for node in nodes[:5]] == simplified_ops:
        preserved = 0
        consumed = 5
    elif [node.op_type for node in nodes[:17]] == dynamic_ops:
        # Height/width Gather outputs are reused by the final resize branch.
        preserved = 6
        consumed = 17
    else:
        raise ValueError("The model does not start with the expected pixel-unshuffle subgraph")
    unsqueeze = next(node for node in nodes[:consumed] if node.op_type == "Unsqueeze")
    squeeze = nodes[consumed - 1]
    if list(unsqueeze.input)[:1] != [input_name]:
        raise ValueError("The pixel-unshuffle subgraph is not connected to the selected input")
    replacement = helper.make_node(
        "SpaceToDepth",
        inputs=[input_name],
        outputs=list(squeeze.output),
        blocksize=2,
        name="/SpaceToDepth",
    )
    replace_repeated(model.graph.node, [*nodes[:preserved], replacement, *nodes[consumed:]])
    reorder_first_conv_for_space_to_depth(model, input_name, 2)


def reorder_first_conv_for_space_to_depth(model: onnx.ModelProto, input_name: str,
                                           block_size: int) -> None:
    """Map ONNX SpaceToDepth's depth-major channels to PyTorch PixelUnshuffle order."""
    input_info = next(value for value in model.graph.input if value.name == input_name)
    input_channels = input_info.type.tensor_type.shape.dim[1].dim_value
    first_conv = next(node for node in model.graph.node if node.op_type == "Conv")
    weight_name = first_conv.input[1]
    weight_node = next(
        node for node in model.graph.node
        if node.op_type == "Constant" and weight_name in node.output
    )
    weight_attribute = next(attribute for attribute in weight_node.attribute if attribute.name == "value")
    weights = numpy_helper.to_array(weight_attribute.t)
    expected_channels = input_channels * block_size * block_size
    if weights.ndim != 4 or weights.shape[1] != expected_channels:
        raise ValueError("The first convolution does not match the pixel-unshuffle channel count")
    reordered = np.empty_like(weights)
    for channel in range(input_channels):
        for offset in range(block_size * block_size):
            pixel_unshuffle_index = channel * block_size * block_size + offset
            space_to_depth_index = offset * input_channels + channel
            reordered[:, space_to_depth_index, :, :] = weights[:, pixel_unshuffle_index, :, :]
    weight_attribute.t.CopyFrom(numpy_helper.from_array(reordered, name=weight_attribute.t.name))


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("source", type=Path)
    parser.add_argument("output", type=Path)
    parser.add_argument("--input", default="input")
    parser.add_argument("--shape", default="1,3,64,64")
    parser.add_argument("--replace-leading-pixel-unshuffle", action="store_true")
    args = parser.parse_args()

    model = onnx.load(args.source)
    normalize_graph(model.graph)
    set_static_input_shape(model, args.input, [int(value) for value in args.shape.split(",")])
    if args.replace_leading_pixel_unshuffle:
        replace_leading_pixel_unshuffle(model, args.input)
    model = onnx.shape_inference.infer_shapes(model)
    onnx.checker.check_model(model)
    args.output.parent.mkdir(parents=True, exist_ok=True)
    onnx.save(model, args.output)


if __name__ == "__main__":
    main()
