#!/usr/bin/env python3
"""
explore_codebase.py — dump ODK Collect modules / classes / functions to JSON.

A dependency-free, heuristic static scan of the Kotlin + Java production sources
(`src/main`) of every Gradle module declared in settings.gradle.

What it does
------------
* Discovers modules from `settings.gradle` (`include ':name'` lines).
* Skips test source sets (`src/test`, `src/androidTest`, ...) — only `src/main`.
* Skips test-support modules (`*-test`, `test-shared`, `shadows`, ...).
* For every .kt / .java file it extracts: package, class-like declarations
  (class / interface / object / enum / annotation / record) with nesting, and
  their functions / methods.
* Writes the whole thing to JSON.

It is an *outline* generator, not a compiler. Parsing is comment/string aware
and brace-scope aware, which makes it accurate for the vast majority of real
code, but a few exotic constructs (inline annotations before a Java return
type, backtick edge cases, etc.) may be missed. Good enough for exploration.

Usage
-----
    python3 explore_codebase.py                     # -> codebase-map.json
    python3 explore_codebase.py --out map.json --pretty
    python3 explore_codebase.py --include-test-modules
"""

from __future__ import annotations

import argparse
import bisect
import json
import os
import re
import sys
from datetime import datetime, timezone

# --------------------------------------------------------------------------- #
# Module selection
# --------------------------------------------------------------------------- #

# Modules that only exist to support tests. Their code lives in src/main but is
# test infrastructure, so we exclude them by default. Any module whose name
# ends in "-test" is also excluded (forms-test, fragments-test, service-test).
TEST_SUPPORT_MODULES = {
    "test-shared",
    "test-forms",
    "shadows",
    "androidtest",
    "nbistubs",
}

INCLUDE_RE = re.compile(r"""include\s*\(?\s*['"]:([^'"]+)['"]""")


def discover_modules(root: str) -> list[tuple[str, str]]:
    """Return [(module_name, module_dir_abs)] from settings.gradle[.kts]."""
    settings = None
    for candidate in ("settings.gradle", "settings.gradle.kts"):
        p = os.path.join(root, candidate)
        if os.path.isfile(p):
            settings = p
            break
    if settings is None:
        raise SystemExit("Could not find settings.gradle in %s" % root)

    with open(settings, encoding="utf-8", errors="replace") as fh:
        text = fh.read()

    modules = []
    seen = set()
    for m in INCLUDE_RE.finditer(text):
        name = m.group(1)
        if name in seen:
            continue
        seen.add(name)
        rel_dir = name.replace(":", "/")
        modules.append((name, os.path.join(root, rel_dir)))
    return modules


def is_test_module(name: str) -> bool:
    return name.endswith("-test") or name in TEST_SUPPORT_MODULES


# --------------------------------------------------------------------------- #
# Lexer: blank out comments, strings and char literals (offsets preserved)
# --------------------------------------------------------------------------- #

def clean_code(s: str) -> str:
    """
    Replace comment / string / char-literal content with spaces (newlines kept)
    so that braces, slashes and quotes inside them never confuse the scanner.

    The output has EXACTLY the same length as the input, so byte offsets and
    line numbers map 1:1 to the original file.
    """
    out: list[str] = []
    i, n = 0, len(s)
    while i < n:
        c = s[i]

        # line comment //
        if c == "/" and i + 1 < n and s[i + 1] == "/":
            while i < n and s[i] != "\n":
                out.append(" ")
                i += 1
            continue

        # block comment /* ... */
        if c == "/" and i + 1 < n and s[i + 1] == "*":
            out.append(" ")
            out.append(" ")
            i += 2
            while i < n and not (s[i] == "*" and i + 1 < n and s[i + 1] == "/"):
                out.append("\n" if s[i] == "\n" else " ")
                i += 1
            if i < n:
                out.append(" ")
                out.append(" ")
                i += 2
            continue

        # triple-quoted string (Kotlin raw string / Java text block)
        if s.startswith('"""', i):
            out.append(" ")
            out.append(" ")
            out.append(" ")
            i += 3
            while i < n and not s.startswith('"""', i):
                out.append("\n" if s[i] == "\n" else " ")
                i += 1
            if i < n:
                out.append(" ")
                out.append(" ")
                out.append(" ")
                i += 3
            continue

        # normal string "..."
        if c == '"':
            out.append(" ")
            i += 1
            while i < n and s[i] != '"':
                if s[i] == "\\" and i + 1 < n:
                    out.append(" ")
                    out.append(" ")
                    i += 2
                    continue
                out.append("\n" if s[i] == "\n" else " ")
                i += 1
            if i < n:
                out.append(" ")
                i += 1
            continue

        # char literal '...'
        if c == "'":
            out.append(" ")
            i += 1
            while i < n and s[i] != "'":
                if s[i] == "\\" and i + 1 < n:
                    out.append(" ")
                    out.append(" ")
                    i += 2
                    continue
                out.append(" ")
                i += 1
            if i < n:
                out.append(" ")
                i += 1
            continue

        out.append(c)
        i += 1

    return "".join(out)


# --------------------------------------------------------------------------- #
# Offset -> line helper
# --------------------------------------------------------------------------- #

class LineIndex:
    def __init__(self, text: str):
        self._starts = [0]
        for i, ch in enumerate(text):
            if ch == "\n":
                self._starts.append(i + 1)

    def line(self, offset: int) -> int:
        return bisect.bisect_right(self._starts, offset)


# --------------------------------------------------------------------------- #
# Declaration regexes
# --------------------------------------------------------------------------- #

IDENT = r"`[^`]+`|[A-Za-z_$][\w$]*"

KCLASS_RE = re.compile(r"\b(?P<kw>class|interface|object)\b(?:\s+(?P<name>" + IDENT + r"))?")
KFUN_RE = re.compile(
    r"\bfun\b\s*(?:<[^>]*>\s*)?(?:[\w.<>?, ]+?\.)?(?P<name>" + IDENT + r")\s*\("
)
KCTOR_RE = re.compile(r"\bconstructor\b\s*\(")

KMOD_WORDS = ("data", "enum", "sealed", "annotation", "value", "abstract", "inner", "open", "companion", "fun")

JCLASS_RE = re.compile(
    r"\b(?P<kw>class|interface|enum|record)\b\s+(?P<name>[A-Za-z_$][\w$]*)"
    r"|(?P<annkw>@interface)\s+(?P<annname>[A-Za-z_$][\w$]*)"
)

# Java method: line-anchored, a return type followed by name(...) then { or ;
JMETHOD_RE = re.compile(
    r"""(?m)^[ \t]*
        (?P<mods>(?:(?:public|protected|private|static|final|abstract|
                     synchronized|native|default|strictfp)\s+)*)
        (?:<[^>]+>\s*)?
        (?P<type>[A-Za-z_$][\w.$]*(?:\s*<[^;{()]*>)?(?:\s*\[\s*\])*)
        \s+
        (?P<name>[A-Za-z_$][\w$]*)
        \s*\([^;{}]*\)
        \s*(?:throws\s[\w.,\s]+?)?
        \s*[{;]
    """,
    re.VERBOSE,
)

# Java constructor: name(...) { , accepted only when name == an enclosing class
JCTOR_RE = re.compile(
    r"(?m)^[ \t]*(?:(?:public|protected|private)\s+)?"
    r"(?P<name>[A-Za-z_$][\w$]*)\s*\([^;{}]*\)\s*(?:throws\s[\w.,\s]+?)?\s*\{"
)

JAVA_KEYWORDS = {
    "if", "for", "while", "switch", "return", "new", "throw", "else", "do",
    "assert", "case", "break", "continue", "yield", "synchronized", "catch",
    "super", "this", "instanceof", "try", "finally", "default",
}

PACKAGE_RE = re.compile(r"(?m)^\s*package\s+([\w.]+)")

# Dependency analysis: import statements + bare identifier tokens
IMPORT_LINE_RE = re.compile(r"(?m)^[ \t]*import\b[^\n;]*;?")
IDENT_TOKEN_RE = re.compile(r"[A-Za-z_$][\w$]*")


def parse_imports(clean: str):
    """Return (import_map, wildcard_pkgs) from a cleaned source file.

    import_map maps a usable simple name (or Kotlin `as` alias) to the imported
    fully-qualified path. Wildcard imports (`a.b.*`) are collected as packages.
    Java `import static ...` is ignored (it brings in members, not a type name).
    """
    import_map: dict[str, str] = {}
    wildcards: list[str] = []
    for m in IMPORT_LINE_RE.finditer(clean):
        body = m.group()[m.group().index("import") + len("import"):].strip()
        body = body.rstrip(";").strip()
        if body.startswith("static "):
            continue
        if body.endswith(".*"):
            pkg = body[:-2].strip().rstrip(".")
            if pkg:
                wildcards.append(pkg)
            continue
        parts = body.split()
        if len(parts) >= 3 and parts[1] == "as":      # Kotlin: import a.b.C as D
            path, simple = parts[0], parts[2]
        elif parts:
            path = parts[0]
            simple = path.rsplit(".", 1)[-1]
        else:
            continue
        if path:
            import_map[simple] = path
    return import_map, wildcards


# --------------------------------------------------------------------------- #
# Class-scope discovery (with body brace matching + nesting)
# --------------------------------------------------------------------------- #

def _find_body_open(clean: str, start: int) -> int | None:
    """From `start` (just past a class name) find the class body '{'.

    Skips balanced <...> and (...) (generics / primary constructor / supertype
    ctor calls). Returns the '{' offset, or None for a brace-less declaration.
    """
    i, n = start, len(clean)
    angle = paren = 0
    while i < n:
        c = clean[i]
        if c == "<":
            angle += 1
        elif c == ">":
            if angle > 0:
                angle -= 1
        elif c == "(":
            paren += 1
        elif c == ")":
            if paren > 0:
                paren -= 1
        elif paren == 0 and angle == 0:
            if c == "{":
                return i
            if c in "};":
                return None
            if c.isalpha() or c == "_":
                j = i
                while j < n and (clean[j].isalnum() or clean[j] in "_$"):
                    j += 1
                word = clean[i:j]
                if word in ("fun", "class", "interface", "object", "enum", "val", "var"):
                    return None
                i = j
                continue
        i += 1
    return None


def _match_brace(clean: str, open_idx: int) -> int:
    depth, i, n = 0, open_idx, len(clean)
    while i < n:
        c = clean[i]
        if c == "{":
            depth += 1
        elif c == "}":
            depth -= 1
            if depth == 0:
                return i
        i += 1
    return n - 1


def find_class_scopes(clean: str, language: str) -> list[dict]:
    """Return class-like scopes: {name, kind, decl_offset, line-ready, open, close}."""
    scopes = []

    if language == "kotlin":
        for m in KCLASS_RE.finditer(clean):
            kw = m.group("kw")
            name = m.group("name")
            if name:
                name = name.strip("`")
            back = clean[max(0, m.start() - 60):m.start()]
            mods = [w for w in KMOD_WORDS if re.search(r"\b" + w + r"\b", back)]

            if not name:
                # unnamed: only a companion object is meaningful here
                if kw == "object" and "companion" in mods:
                    name = "Companion"
                else:
                    continue  # anonymous object expression / malformed

            kind = kw
            if kw == "class":
                if "enum" in mods:
                    kind = "enum class"
                elif "annotation" in mods:
                    kind = "annotation class"
                elif "data" in mods:
                    kind = "data class"
                elif "sealed" in mods:
                    kind = "sealed class"
                elif "value" in mods:
                    kind = "value class"
            elif kw == "interface" and "fun" in mods:
                kind = "fun interface"

            open_idx = _find_body_open(clean, m.end())
            close_idx = _match_brace(clean, open_idx) if open_idx is not None else m.end()
            scopes.append({
                "name": name,
                "kind": kind,
                "decl": m.start(),
                "open": open_idx if open_idx is not None else m.end(),
                "close": close_idx,
            })
    else:  # java
        for m in JCLASS_RE.finditer(clean):
            if m.group("kw"):
                kw = m.group("kw")
                name = m.group("name")
                kind = kw
            else:
                kw = "@interface"
                name = m.group("annname")
                kind = "annotation"
            open_idx = _find_body_open(clean, m.end())
            close_idx = _match_brace(clean, open_idx) if open_idx is not None else m.end()
            scopes.append({
                "name": name,
                "kind": kind,
                "decl": m.start(),
                "open": open_idx if open_idx is not None else m.end(),
                "close": close_idx,
            })

    scopes.sort(key=lambda s: s["decl"])

    # nesting: parent = innermost scope whose (open, close) strictly contains decl
    for s in scopes:
        parent = None
        for cand in scopes:
            if cand is s:
                continue
            if cand["open"] < s["decl"] < cand["close"]:
                if parent is None or (cand["close"] - cand["open"]) < (parent["close"] - parent["open"]):
                    parent = cand
        s["parent"] = parent
    return scopes


def innermost_scope(scopes: list[dict], offset: int) -> dict | None:
    best = None
    for s in scopes:
        if s["open"] < offset < s["close"]:
            if best is None or (s["close"] - s["open"]) < (best["close"] - best["open"]):
                best = s
    return best


# --------------------------------------------------------------------------- #
# Per-file parsing
# --------------------------------------------------------------------------- #

def parse_file(path: str, rel_path: str):
    with open(path, encoding="utf-8", errors="replace") as fh:
        raw = fh.read()
    clean = clean_code(raw)
    lines = LineIndex(clean)
    language = "kotlin" if path.endswith(".kt") else "java"

    pkg_m = PACKAGE_RE.search(clean)
    package = pkg_m.group(1) if pkg_m else ""
    import_map, wildcards = parse_imports(clean)

    scopes = find_class_scopes(clean, language)

    # qualified names, respecting nesting
    def qualified(scope: dict) -> str:
        chain = []
        cur = scope
        while cur is not None:
            chain.append(cur["name"])
            cur = cur["parent"]
        chain.reverse()
        prefix = package + "." if package else ""
        return prefix + ".".join(chain)

    for s in scopes:
        s["functions"] = []

    top_level_functions: list[dict] = []

    def add_function(name: str, offset: int):
        name = name.strip("`")
        host = innermost_scope(scopes, offset)
        entry = {"name": name, "line": lines.line(offset)}
        if host is None:
            top_level_functions.append(entry)
        else:
            host["functions"].append(entry)

    if language == "kotlin":
        for m in KFUN_RE.finditer(clean):
            add_function(m.group("name"), m.start())
        for m in KCTOR_RE.finditer(clean):
            add_function("constructor", m.start())
    else:
        class_names = {s["name"] for s in scopes}
        for m in JMETHOD_RE.finditer(clean):
            if m.group("type") in JAVA_KEYWORDS or m.group("name") in JAVA_KEYWORDS:
                continue
            if innermost_scope(scopes, m.start()) is None:
                continue
            add_function(m.group("name"), m.start("name"))
        for m in JCTOR_RE.finditer(clean):
            name = m.group("name")
            if name in JAVA_KEYWORDS or name not in class_names:
                continue
            host = innermost_scope(scopes, m.start())
            if host is not None and host["name"] == name:
                add_function(name, m.start("name"))

    # dedupe functions per scope by (name, line)
    def dedupe(funcs):
        seen = set()
        out = []
        for f in sorted(funcs, key=lambda x: x["line"]):
            key = (f["name"], f["line"])
            if key in seen:
                continue
            seen.add(key)
            out.append(f)
        return out

    classes_json = []
    for s in scopes:
        classes_json.append({
            "name": s["name"],
            "qualified_name": qualified(s),
            "kind": s["kind"],
            "line": lines.line(s["decl"]),
            "parent": qualified(s["parent"]) if s["parent"] else None,
            "depends_on": [],  # filled in the second (whole-repo) pass
            "functions": dedupe(s["functions"]),
        })

    file_record = {
        "path": rel_path,
        "language": language,
        "package": package,
        "classes": classes_json,
        "top_level_functions": dedupe(top_level_functions),
        "top_level_dependencies": [],  # filled in the second pass
    }
    aux = {
        "clean": clean,
        "scopes": scopes,
        "class_json": classes_json,  # same objects as file_record["classes"]
        "file_record": file_record,
        "package": package,
        "imports": import_map,
        "wildcards": wildcards,
    }
    return file_record, aux


# --------------------------------------------------------------------------- #
# Dependency resolution (second pass, needs the whole-repo class registry)
# --------------------------------------------------------------------------- #

def build_registry(included: list[dict]):
    """Return (all_qnames, simple_index) over every internal (Collect) class."""
    all_qnames: set[str] = set()
    simple_index: dict[str, set[str]] = {}
    for module in included:
        for f in module["files"]:
            for c in f["classes"]:
                q = c["qualified_name"]
                all_qnames.add(q)
                simple_index.setdefault(q.rsplit(".", 1)[-1], set()).add(q)
    return all_qnames, simple_index


def resolve_simple(simple, ctx, all_qnames):
    """Resolve a bare identifier to an internal class FQN, or None.

    Precedence mirrors how the compiler would resolve the name:
      1. explicit import  -> that class (an EXTERNAL import suppresses the name,
         which is what stops e.g. `kotlin.Result` matching an internal `Result`)
      2. a class declared in the same file
      3. a class in the same package
      4. a wildcard-imported package that contains the class
    A name that a real compiler could only reach via one of these is the only
    kind we count, so ambiguous / unresolved names are dropped rather than guessed.
    """
    import_map = ctx["imports"]
    if simple in import_map:
        fqn = import_map[simple]
        return fqn if fqn in all_qnames else None
    local = ctx["local_index"]
    if simple in local:
        return local[simple]
    package = ctx["package"]
    if package:
        cand = package + "." + simple
        if cand in all_qnames:
            return cand
    hits = [pkg + "." + simple for pkg in ctx["wildcards"]
            if (pkg + "." + simple) in all_qnames]
    return hits[0] if len(hits) == 1 else None


def _resolve_tokens(text, ctx, all_qnames, simple_index, exclude):
    deps = set()
    for tm in IDENT_TOKEN_RE.finditer(text):
        simple = tm.group()
        if simple not in simple_index:
            continue
        fqn = resolve_simple(simple, ctx, all_qnames)
        if fqn and fqn != exclude:
            deps.add(fqn)
    return deps


def compute_dependencies(aux: dict, all_qnames, simple_index):
    """Fill `depends_on` for every class in one file (mutates the JSON dicts)."""
    clean = aux["clean"]
    scopes = aux["scopes"]
    class_json = aux["class_json"]

    local_index = {cj["name"]: cj["qualified_name"] for cj in class_json}
    ctx = {
        "imports": aux["imports"],
        "wildcards": aux["wildcards"],
        "package": aux["package"],
        "local_index": local_index,
    }

    children: dict[int, list] = {}
    for s in scopes:
        if s["parent"] is not None:
            children.setdefault(id(s["parent"]), []).append(s)

    for s, cj in zip(scopes, class_json):
        # own region = declaration header + body, with nested classes blanked so
        # their references are attributed to them, not to this class.
        start, end = s["decl"], s["close"] + 1
        buf = list(clean[start:end])
        for ch in children.get(id(s), []):
            a = max(0, ch["decl"] - start)
            b = min(len(buf), ch["close"] + 1 - start)
            if b > a:
                buf[a:b] = " " * (b - a)
        deps = _resolve_tokens("".join(buf), ctx, all_qnames, simple_index,
                               cj["qualified_name"])
        cj["depends_on"] = sorted(deps)

    # file-level references (top-level Kotlin funcs/props), excluding the class
    # bodies (already covered) and the import/package lines themselves.
    buf = list(clean)
    for s in scopes:
        if s["parent"] is None:
            a, b = s["decl"], s["close"] + 1
            buf[a:b] = " " * (b - a)
    for rex in (IMPORT_LINE_RE, PACKAGE_RE):
        for m in rex.finditer(clean):
            buf[m.start():m.end()] = " " * (m.end() - m.start())
    deps = _resolve_tokens("".join(buf), ctx, all_qnames, simple_index, None)
    aux["file_record"]["top_level_dependencies"] = sorted(deps)


# --------------------------------------------------------------------------- #
# Module scanning
# --------------------------------------------------------------------------- #

def scan_module(name: str, module_dir: str, root: str):
    main_dir = os.path.join(module_dir, "src", "main")
    if not os.path.isdir(main_dir):
        return None, []

    files = []
    for dirpath, _dirnames, filenames in os.walk(main_dir):
        for fn in filenames:
            if fn.endswith(".kt") or fn.endswith(".java"):
                full = os.path.join(dirpath, fn)
                rel = os.path.relpath(full, root)
                files.append((full, rel))
    files.sort(key=lambda t: t[1])

    if not files:
        return None, []

    parsed = [parse_file(full, rel) for full, rel in files]
    file_records = [p[0] for p in parsed]
    aux_list = [p[1] for p in parsed]

    n_classes = sum(len(f["classes"]) for f in file_records)
    n_functions = sum(
        len(c["functions"]) for f in file_records for c in f["classes"]
    ) + sum(len(f["top_level_functions"]) for f in file_records)
    n_kt = sum(1 for f in file_records if f["language"] == "kotlin")
    n_java = sum(1 for f in file_records if f["language"] == "java")

    record = {
        "name": name,
        "path": os.path.relpath(module_dir, root),
        "counts": {
            "files": len(file_records),
            "kotlin_files": n_kt,
            "java_files": n_java,
            "classes": n_classes,
            "functions": n_functions,
        },
        "files": file_records,
    }
    return record, aux_list


# --------------------------------------------------------------------------- #
# Main
# --------------------------------------------------------------------------- #

def main(argv=None):
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("--root", default=".", help="Repo root (default: current dir)")
    ap.add_argument("--out", default="codebase-map.json", help="Output JSON path")
    ap.add_argument("--pretty", action="store_true", help="Indent the JSON output")
    ap.add_argument("--include-test-modules", action="store_true",
                    help="Do not skip test-support modules")
    args = ap.parse_args(argv)

    root = os.path.abspath(args.root)
    modules = discover_modules(root)

    included, excluded, all_aux = [], [], []
    for name, module_dir in modules:
        if not args.include_test_modules and is_test_module(name):
            excluded.append(name)
            continue
        rec, aux_list = scan_module(name, module_dir, root)
        if rec is None:
            excluded.append(name)  # no src/main sources
        else:
            included.append(rec)
            all_aux.extend(aux_list)

    included.sort(key=lambda m: m["name"])

    # Second pass: resolve intra-Collect dependencies now that every class is known.
    all_qnames, simple_index = build_registry(included)
    for aux in all_aux:
        compute_dependencies(aux, all_qnames, simple_index)

    dependency_edges = sum(
        len(c["depends_on"]) for m in included for f in m["files"] for c in f["classes"]
    )

    totals = {
        "modules": len(included),
        "files": sum(m["counts"]["files"] for m in included),
        "kotlin_files": sum(m["counts"]["kotlin_files"] for m in included),
        "java_files": sum(m["counts"]["java_files"] for m in included),
        "classes": sum(m["counts"]["classes"] for m in included),
        "functions": sum(m["counts"]["functions"] for m in included),
        "dependency_edges": dependency_edges,
    }

    result = {
        "meta": {
            "generator": "explore_codebase.py",
            "generated_utc": datetime.now(timezone.utc).isoformat(timespec="seconds"),
            "root": root,
            "source_set": "src/main",
            "note": "Heuristic static scan of Kotlin+Java production sources; "
                    "tests and test-support modules excluded. `depends_on` lists "
                    "only internal (Collect) classes referenced by each class; "
                    "external types (Android/JavaRosa/stdlib/3rd-party) are omitted.",
            "excluded_modules": sorted(excluded),
            "totals": totals,
        },
        "modules": included,
    }

    out_path = os.path.abspath(args.out)
    with open(out_path, "w", encoding="utf-8") as fh:
        json.dump(result, fh, indent=2 if args.pretty else None, ensure_ascii=False)
        fh.write("\n")

    print("Wrote %s" % out_path)
    print("Modules: %d   Files: %d (%d kt / %d java)   Classes: %d   Functions: %d" % (
        totals["modules"], totals["files"], totals["kotlin_files"],
        totals["java_files"], totals["classes"], totals["functions"]))
    print("Internal dependency edges: %d" % totals["dependency_edges"])
    print("Excluded modules: %s" % ", ".join(sorted(excluded)))
    return 0


if __name__ == "__main__":
    sys.exit(main())
