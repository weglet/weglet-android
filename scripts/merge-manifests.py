#!/usr/bin/env python3
"""Merge the small subset of Android library manifests needed by Weglet."""

from __future__ import annotations

import copy
import sys
import xml.etree.ElementTree as ET
from pathlib import Path


ANDROID = "http://schemas.android.com/apk/res/android"
TOOLS = "http://schemas.android.com/tools"
A = f"{{{ANDROID}}}"
T = f"{{{TOOLS}}}"
APP_ID = "app.weglet.browser.v2"
COMPONENTS = {"activity", "activity-alias", "service", "receiver", "provider"}
TOP_LEVEL = {"uses-permission", "uses-permission-sdk-23", "permission", "uses-feature", "queries"}

ET.register_namespace("android", ANDROID)


def local(tag: str) -> str:
    return tag.rsplit("}", 1)[-1]


def expand(value: str, package: str) -> str:
    value = value.replace("${applicationId}", APP_ID).replace("${packageName}", package)
    if value.startswith("."):
        return package + value
    return value


def clean(element: ET.Element, package: str) -> ET.Element:
    item = copy.deepcopy(element)
    for node in item.iter():
        for key in list(node.attrib):
            if key.startswith(T):
                del node.attrib[key]
                continue
            node.attrib[key] = expand(node.attrib[key], package)
        if local(node.tag) in COMPONENTS and A + "name" in node.attrib:
            name = node.attrib[A + "name"]
            if "." not in name:
                node.attrib[A + "name"] = f"{package}.{name}"
    return item


def identity(element: ET.Element) -> tuple[str, str]:
    tag = local(element.tag)
    if tag == "queries":
        return tag, ET.tostring(element, encoding="unicode")
    return tag, element.attrib.get(A + "name", ET.tostring(element, encoding="unicode"))


def merge_children(target: ET.Element, source: ET.Element) -> None:
    existing = {identity(item): item for item in target}
    for incoming in source:
        key = identity(incoming)
        current = existing.get(key)
        if current is None:
            target.append(copy.deepcopy(incoming))
            existing[key] = target[-1]
            continue
        for attr, value in incoming.attrib.items():
            current.attrib.setdefault(attr, value)
        nested = {identity(item) for item in current}
        for item in incoming:
            if identity(item) not in nested:
                current.append(copy.deepcopy(item))
                nested.add(identity(item))


def main() -> int:
    if len(sys.argv) < 4:
        print("usage: merge-manifests.py BASE OUTPUT LIBRARY...", file=sys.stderr)
        return 2
    base_path, output_path, *library_paths = map(Path, sys.argv[1:])
    tree = ET.parse(base_path)
    root = tree.getroot()
    base_application = next(item for item in root if local(item.tag) == "application")

    top_keys = {identity(item) for item in root if local(item.tag) in TOP_LEVEL}
    packages: set[str] = set()
    for path in library_paths:
        library_root = ET.parse(path).getroot()
        package = library_root.attrib.get("package", "").strip()
        if package:
            packages.add(package)
        for element in library_root:
            tag = local(element.tag)
            if tag in TOP_LEVEL:
                cleaned = clean(element, package)
                key = identity(cleaned)
                if key not in top_keys:
                    root.insert(list(root).index(base_application), cleaned)
                    top_keys.add(key)
            elif tag == "application":
                cleaned = clean(element, package)
                for attr, value in cleaned.attrib.items():
                    if attr not in {A + "allowBackup", A + "icon", A + "label", A + "theme"}:
                        base_application.attrib.setdefault(attr, value)
                merge_children(base_application, cleaned)

    output_path.parent.mkdir(parents=True, exist_ok=True)
    tree.write(output_path, encoding="utf-8", xml_declaration=True)
    print(":".join(sorted(packages)))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
