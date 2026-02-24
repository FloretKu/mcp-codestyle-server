#!/usr/bin/env python3
"""
Template Validator - Validates Codestyle template format

Usage:
    python validate_template.py <version_directory>

Example:
    python validate_template.py CRUD/1.0.0

Validates:
    - meta.json exists in version directory
    - meta.json has all required fields (single-version format)
    - At least one .ftl template file exists
    - All inputVariables have required fields

Standard template structure:
    <artifactId>/
    └── <version>/
        ├── meta.json          # Required: template metadata
        ├── backend/
        │   └── *.ftl          # Template files in subdirectories
        └── frontend/
            └── *.ftl
"""

import json
import sys
from pathlib import Path

REQUIRED_META_FIELDS = ["groupId", "artifactId", "version", "files"]
REQUIRED_VARIABLE_FIELDS = ["variableName", "variableType", "variableComment", "example"]


def validate_meta_json(meta_path: Path) -> list[str]:
    """Validate meta.json structure (single-version format only)."""
    errors = []
    
    if not meta_path.exists():
        return ["meta.json not found in version directory"]
    
    try:
        with open(meta_path, "r", encoding="utf-8") as f:
            meta = json.load(f)
    except json.JSONDecodeError as e:
        return [f"Invalid JSON: {e}"]
    
    # Check required fields (single-version format)
    for field in REQUIRED_META_FIELDS:
        if field not in meta:
            errors.append(f"Missing required field: {field}")
    
    # Validate files array
    if "files" in meta:
        errors.extend(validate_files(meta["files"]))
    
    return errors


def validate_files(files) -> list[str]:
    """Validate files array."""
    errors = []
    
    if not isinstance(files, list):
        errors.append("'files' must be an array")
        return errors
    
    if len(files) == 0:
        errors.append("'files' array is empty")
        return errors
    
    for i, file_obj in enumerate(files):
        if "filename" not in file_obj:
            errors.append(f"File {i}: missing 'filename'")
        if "inputVariables" in file_obj:
            for j, var in enumerate(file_obj["inputVariables"]):
                for field in REQUIRED_VARIABLE_FIELDS:
                    if field not in var:
                        errors.append(f"File {i}, variable {j}: missing '{field}'")
    
    return errors


def validate_templates(version_dir: Path) -> list[str]:
    """Validate template files exist (recursive search in subdirectories)."""
    errors = []
    
    # Recursively search for .ftl files in version directory
    ftl_files = list(version_dir.rglob("*.ftl"))
    
    if not ftl_files:
        errors.append("No .ftl template files found in version directory")
    
    return errors


def validate_template_dir(directory: str) -> bool:
    """
    Validate a template version directory.
    
    Expected structure:
        <artifactId>/<version>/
        ├── meta.json          # Required
        ├── backend/
        │   └── *.ftl          # Template files
        └── frontend/
            └── *.ftl
    
    Args:
        directory: Path to version directory (e.g., "CRUD/1.0.0")
    
    Returns:
        True if valid, False otherwise
    """
    version_dir = Path(directory)
    
    if not version_dir.exists():
        print(f"❌ Error: Directory not found: {directory}")
        return False
    
    if not version_dir.is_dir():
        print(f"❌ Error: Not a directory: {directory}")
        return False
    
    print(f"Validating template: {directory}")
    print("-" * 60)
    
    all_errors = []
    
    # Validate meta.json
    meta_path = version_dir / "meta.json"
    meta_errors = validate_meta_json(meta_path)
    all_errors.extend(meta_errors)
    
    if not meta_errors:
        print("✓ meta.json structure valid (single-version)")
    else:
        print("✗ meta.json validation failed:")
        for error in meta_errors:
            print(f"  - {error}")
    
    # Validate template files
    template_errors = validate_templates(version_dir)
    all_errors.extend(template_errors)
    
    if not template_errors:
        # Count .ftl files
        ftl_count = len(list(version_dir.rglob("*.ftl")))
        print(f"✓ Template files exist ({ftl_count} .ftl files found)")
    else:
        print("✗ Template files validation failed:")
        for error in template_errors:
            print(f"  - {error}")
    
    # Summary
    print("-" * 60)
    if all_errors:
        print(f"✗ Validation failed with {len(all_errors)} error(s)")
        return False
    else:
        print("✓ Template is valid")
        return True


def main():
    if len(sys.argv) < 2:
        print("Usage: python validate_template.py <version_directory>")
        print("\nExample:")
        print("  python validate_template.py CRUD/1.0.0")
        print("\nExpected structure:")
        print("  <artifactId>/")
        print("  └── <version>/")
        print("      ├── meta.json          # Required")
        print("      ├── backend/")
        print("      │   └── *.ftl          # Template files")
        print("      └── frontend/")
        print("          └── *.ftl")
        sys.exit(1)
    
    directory = sys.argv[1]
    success = validate_template_dir(directory)
    sys.exit(0 if success else 1)


if __name__ == "__main__":
    main()
