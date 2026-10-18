#!/usr/bin/env python3
"""Fetch GeckoView and its Maven runtime graph into a deterministic local cache."""

from __future__ import annotations

import hashlib
import os
import re
import sys
import urllib.error
import urllib.request
import xml.etree.ElementTree as ET
from dataclasses import dataclass, field
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
M2 = ROOT / ".deps" / "m2"
MANIFEST = ROOT / ".deps" / "gecko-runtime.txt"
REPOSITORIES = (
    "https://maven.mozilla.org/maven2",
    "https://dl.google.com/dl/android/maven2",
    "https://repo.maven.apache.org/maven2",
)
ROOT_COORDINATE = ("org.mozilla.geckoview", "geckoview-arm64-v8a", "153.0.20260803132010")
ROOT_SHA256 = "cff75ca40aa02029b2061128355e30f8a51c698209f9fe484a93b96ea73f4ff6"
PROPERTY = re.compile(r"\$\{([^}]+)}")


def local_name(tag: str) -> str:
    return tag.rsplit("}", 1)[-1]


def child(element: ET.Element | None, name: str) -> ET.Element | None:
    if element is None:
        return None
    return next((item for item in element if local_name(item.tag) == name), None)


def children(element: ET.Element | None, name: str) -> list[ET.Element]:
    if element is None:
        return []
    return [item for item in element if local_name(item.tag) == name]


def text(element: ET.Element | None, name: str, default: str = "") -> str:
    value = child(element, name)
    return (value.text or "").strip() if value is not None else default


def expand(value: str, properties: dict[str, str]) -> str:
    previous = None
    while previous != value:
        previous = value
        value = PROPERTY.sub(lambda match: properties.get(match.group(1), match.group(0)), value)
    return value


def normalize_version(value: str) -> str:
    value = value.strip()
    if value.startswith("[") and value.endswith("]") and "," not in value:
        return value[1:-1].strip()
    return value


def version_key(value: str) -> tuple[tuple[int, ...], int, str]:
    numbers = tuple(int(item) for item in re.findall(r"\d+", value))
    stable = 0 if re.search(r"(?i)(alpha|beta|rc|snapshot|dev)", value) else 1
    return numbers, stable, value


@dataclass(frozen=True)
class Coordinate:
    group: str
    artifact: str
    version: str

    @property
    def key(self) -> str:
        return f"{self.group}:{self.artifact}:{self.version}"

    @property
    def directory(self) -> Path:
        return M2 / Path(*self.group.split(".")) / self.artifact / self.version

    @property
    def remote_directory(self) -> str:
        return f"{'/'.join(self.group.split('.'))}/{self.artifact}/{self.version}"


@dataclass
class Model:
    coordinate: Coordinate
    packaging: str = "jar"
    properties: dict[str, str] = field(default_factory=dict)
    managed: dict[tuple[str, str], tuple[str, str]] = field(default_factory=dict)
    dependencies: list[tuple[Coordinate, str]] = field(default_factory=list)


class Resolver:
    def __init__(self) -> None:
        self.models: dict[str, Model] = {}
        self.resolved: dict[str, tuple[Path, str]] = {}
        self.active: set[str] = set()

    def fetch(self, coordinate: Coordinate, extension: str) -> Path:
        destination = coordinate.directory / f"{coordinate.artifact}-{coordinate.version}.{extension}"
        if destination.is_file() and destination.stat().st_size:
            return destination
        destination.parent.mkdir(parents=True, exist_ok=True)
        relative = f"{coordinate.remote_directory}/{destination.name}"
        last_error: Exception | None = None
        if coordinate.group.startswith("org.mozilla"):
            repositories = REPOSITORIES
        elif coordinate.group.startswith(("androidx.", "com.android.", "com.google.android.")):
            repositories = (REPOSITORIES[1], REPOSITORIES[2], REPOSITORIES[0])
        else:
            repositories = (REPOSITORIES[2], REPOSITORIES[1], REPOSITORIES[0])
        for repository in repositories:
            try:
                request = urllib.request.Request(
                    f"{repository}/{relative}",
                    headers={"User-Agent": "Weglet dependency fetcher/1.2"},
                )
                with urllib.request.urlopen(request, timeout=45) as response:
                    data = response.read()
                destination.write_bytes(data)
                return destination
            except (urllib.error.URLError, TimeoutError) as error:
                last_error = error
        raise RuntimeError(f"Cannot fetch {relative}: {last_error}")

    def model(self, coordinate: Coordinate) -> Model:
        if coordinate.key in self.models:
            return self.models[coordinate.key]
        pom = self.fetch(coordinate, "pom")
        root = ET.parse(pom).getroot()

        parent_element = child(root, "parent")
        parent_model: Model | None = None
        if parent_element is not None:
            parent = Coordinate(
                text(parent_element, "groupId"),
                text(parent_element, "artifactId"),
                text(parent_element, "version"),
            )
            if all((parent.group, parent.artifact, parent.version)):
                parent_model = self.model(parent)

        properties = dict(parent_model.properties) if parent_model else {}
        resolved_group = text(root, "groupId") or (parent_model.coordinate.group if parent_model else coordinate.group)
        resolved_version = text(root, "version") or (parent_model.coordinate.version if parent_model else coordinate.version)
        builtins = {
            "project.groupId": resolved_group,
            "pom.groupId": resolved_group,
            "project.artifactId": coordinate.artifact,
            "pom.artifactId": coordinate.artifact,
            "project.version": resolved_version,
            "pom.version": resolved_version,
        }
        properties.update(builtins)
        properties_element = child(root, "properties")
        if properties_element is not None:
            for item in properties_element:
                properties[local_name(item.tag)] = expand((item.text or "").strip(), properties)

        managed = dict(parent_model.managed) if parent_model else {}
        dependency_management = child(child(root, "dependencyManagement"), "dependencies")
        imports: list[Coordinate] = []
        for dependency in children(dependency_management, "dependency"):
            group = expand(text(dependency, "groupId"), properties)
            artifact = expand(text(dependency, "artifactId"), properties)
            version = normalize_version(expand(text(dependency, "version"), properties))
            dep_type = expand(text(dependency, "type", "jar"), properties)
            scope = expand(text(dependency, "scope"), properties)
            if group and artifact and version:
                if dep_type == "pom" and scope == "import":
                    imports.append(Coordinate(group, artifact, version))
                else:
                    managed[(group, artifact)] = (version, dep_type)
        for imported in imports:
            managed.update(self.model(imported).managed)

        model = Model(
            coordinate=coordinate,
            packaging=expand(text(root, "packaging", "jar"), properties),
            properties=properties,
            managed=managed,
        )
        self.models[coordinate.key] = model

        dependencies = child(root, "dependencies")
        for dependency in children(dependencies, "dependency"):
            if expand(text(dependency, "optional"), properties).lower() == "true":
                continue
            scope = expand(text(dependency, "scope", "compile"), properties)
            if scope not in ("compile", "runtime", ""):
                continue
            group = expand(text(dependency, "groupId"), properties)
            artifact = expand(text(dependency, "artifactId"), properties)
            version = normalize_version(expand(text(dependency, "version"), properties))
            dep_type = expand(text(dependency, "type"), properties)
            if not version and (group, artifact) in managed:
                version = normalize_version(managed[(group, artifact)][0])
            if not dep_type and (group, artifact) in managed:
                dep_type = managed[(group, artifact)][1]
            if group and artifact and version and "${" not in version:
                model.dependencies.append((Coordinate(group, artifact, version), dep_type or ""))
        return model

    def resolve(self, coordinate: Coordinate, declared_type: str = "") -> None:
        if coordinate.key in self.resolved or coordinate.key in self.active:
            return
        self.active.add(coordinate.key)
        model = self.model(coordinate)
        extension = declared_type or model.packaging or "jar"
        if extension in ("bundle", "maven-plugin", "test-jar"):
            extension = "jar"
        artifact: Path | None = None
        if extension != "pom":
            try:
                artifact = self.fetch(coordinate, extension)
            except RuntimeError:
                if extension == "jar":
                    artifact = self.fetch(coordinate, "aar")
                    extension = "aar"
                else:
                    raise
        if artifact is not None:
            self.resolved[coordinate.key] = (artifact, extension)
        for dependency, dep_type in model.dependencies:
            self.resolve(dependency, dep_type)
        self.active.remove(coordinate.key)


def main() -> int:
    resolver = Resolver()
    root = Coordinate(*ROOT_COORDINATE)
    resolver.resolve(root, "aar")
    root_artifact, _ = resolver.resolved[root.key]
    actual = hashlib.sha256(root_artifact.read_bytes()).hexdigest()
    if actual != ROOT_SHA256:
        raise RuntimeError(f"GeckoView SHA-256 mismatch: {actual}")

    selected: dict[tuple[str, str], tuple[Coordinate, Path, str]] = {}
    for key, (artifact, extension) in resolver.resolved.items():
        group, module, version = key.split(":", 2)
        coordinate = Coordinate(group, module, version)
        previous = selected.get((group, module))
        if previous is None or version_key(version) > version_key(previous[0].version):
            selected[(group, module)] = (coordinate, artifact, extension)
    rows = []
    for coordinate, artifact, extension in sorted(selected.values(), key=lambda item: item[0].key):
        rows.append(f"{artifact.relative_to(ROOT)}|{extension}|{coordinate.key}")
    MANIFEST.parent.mkdir(parents=True, exist_ok=True)
    MANIFEST.write_text("\n".join(rows) + "\n", encoding="utf-8")
    print(f"Selected {len(rows)} GeckoView runtime artifacts")
    return 0


if __name__ == "__main__":
    try:
        sys.exit(main())
    except Exception as error:
        print(f"error: {error}", file=sys.stderr)
        sys.exit(1)
