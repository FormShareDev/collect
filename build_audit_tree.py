#!/usr/bin/env python3
"""
build_audit_tree.py — turn codebase-map.json into a class-level dependency tree
that drives the Collect -> iOS migration audit.

Inputs
------
    codebase-map.json   (produced by explore_codebase.py)

Outputs
-------
    migration-audit-tree.json   structured, navigable graph (source of truth)
    migration-audit-tree.md     human/LLM-readable audit checklist + tree

Audit model
-----------
Edges are "depends_on" (A -> B means A uses B). The auditor works **bottom-up**:
audit the deepest leaves first (classes that depend on nothing internal), then
climb to the roots (entry points nothing depends on). Because the real graph has
cycles (e.g. a 300+ class tangle inside collect_app), we:

  1. collapse each strongly-connected component (SCC = a dependency cycle) into a
     single super-node  (Tarjan);
  2. topologically LEVEL the resulting DAG by longest path from the leaves
     (level 0 = deepest / audit first);
  3. emit every class in that order, tagging the ones that live inside a cycle so
     the auditor treats each cycle as one group.

The tree carries only classes + their edges + the audit order. Function-level
detail lives in codebase-map.json; look a class up there by `qualified_name`.

Usage
-----
    python3 build_audit_tree.py
    python3 build_audit_tree.py --map codebase-map.json --nested-tree tree.txt
"""

from __future__ import annotations

import argparse
import json
import os
import sys


# --------------------------------------------------------------------------- #
# Load + build graph
# --------------------------------------------------------------------------- #

def load_graph(map_path: str):
    with open(map_path, encoding="utf-8") as fh:
        data = json.load(fh)

    nodes: dict[str, dict] = {}
    for module in data["modules"]:
        for f in module["files"]:
            for c in f["classes"]:
                q = c["qualified_name"]
                nodes[q] = {
                    "module": module["name"],
                    "kind": c["kind"],
                    "file": f["path"],
                    "line": c["line"],
                    "depends_on": [],   # filled below (internal only)
                    "depended_by": [],
                }
    # keep only edges whose target is a known internal class
    for module in data["modules"]:
        for f in module["files"]:
            for c in f["classes"]:
                q = c["qualified_name"]
                deps = sorted({t for t in c["depends_on"] if t in nodes and t != q})
                nodes[q]["depends_on"] = deps
    for q, n in nodes.items():
        for t in n["depends_on"]:
            nodes[t]["depended_by"].append(q)
    for n in nodes.values():
        n["depended_by"].sort()
    return nodes


# --------------------------------------------------------------------------- #
# Tarjan SCC (iterative) + condensation leveling
# --------------------------------------------------------------------------- #

def tarjan_scc(nodes: dict[str, dict]) -> list[list[str]]:
    index: dict[str, int] = {}
    low: dict[str, int] = {}
    on_stack: dict[str, bool] = {}
    stack: list[str] = []
    counter = [0]
    sccs: list[list[str]] = []

    for root in nodes:
        if root in index:
            continue
        work = [(root, iter(nodes[root]["depends_on"]))]
        index[root] = low[root] = counter[0]
        counter[0] += 1
        stack.append(root)
        on_stack[root] = True

        while work:
            node, it = work[-1]
            advanced = False
            for w in it:
                if w not in index:
                    index[w] = low[w] = counter[0]
                    counter[0] += 1
                    stack.append(w)
                    on_stack[w] = True
                    work.append((w, iter(nodes[w]["depends_on"])))
                    advanced = True
                    break
                elif on_stack.get(w):
                    low[node] = min(low[node], index[w])
            if advanced:
                continue
            if low[node] == index[node]:
                comp = []
                while True:
                    w = stack.pop()
                    on_stack[w] = False
                    comp.append(w)
                    if w == node:
                        break
                sccs.append(comp)
            work.pop()
            if work:
                parent = work[-1][0]
                low[parent] = min(low[parent], low[node])
    return sccs


def scc_suborder(members: list[str], nodes: dict[str, dict]) -> list[str]:
    """Deepest-first order INSIDE one cycle: DFS post-order (dependency before
    dependent, ignoring back-edges). For a singleton this is just [member]."""
    if len(members) == 1:
        return list(members)
    memberset = set(members)
    visited: set[str] = set()
    order: list[str] = []
    for s in sorted(members):
        if s in visited:
            continue
        st = [(s, False)]
        while st:
            node, processed = st.pop()
            if processed:
                order.append(node)
                continue
            if node in visited:
                continue
            visited.add(node)
            st.append((node, True))
            for t in sorted(nodes[node]["depends_on"]):
                if t in memberset and t not in visited:
                    st.append((t, False))
    return order


def compute_levels(sccs, scc_of, nodes):
    """Longest-path level on the condensation DAG (0 = leaf / audit first)."""
    n = len(sccs)
    succ = [set() for _ in range(n)]
    for q, node in nodes.items():
        a = scc_of[q]
        for t in node["depends_on"]:
            b = scc_of[t]
            if a != b:
                succ[a].add(b)

    level = [None] * n
    color = [0] * n  # 0=unseen 1=open 2=done
    for start in range(n):
        if level[start] is not None:
            continue
        st = [(start, False)]
        while st:
            i, processed = st.pop()
            if processed:
                level[i] = 0 if not succ[i] else 1 + max(level[j] for j in succ[i])
                color[i] = 2
                continue
            if color[i] == 2:
                continue
            color[i] = 1
            st.append((i, True))
            for j in succ[i]:
                if color[j] != 2:
                    st.append((j, False))
    return level, succ


# --------------------------------------------------------------------------- #
# Assemble the ordered tree
# --------------------------------------------------------------------------- #

def build_tree(nodes: dict[str, dict]):
    sccs = tarjan_scc(nodes)
    scc_of = {q: i for i, comp in enumerate(sccs) for q in comp}
    level, _succ = compute_levels(sccs, scc_of, nodes)

    # cycle labels for multi-node SCCs, numbered by size (largest first)
    multi = sorted([i for i, c in enumerate(sccs) if len(c) > 1],
                   key=lambda i: (-len(sccs[i]), level[i]))
    cycle_label = {}
    cycles = []
    for n, i in enumerate(multi, start=1):
        label = f"C{n}"
        members = scc_suborder(sccs[i], nodes)
        mods = sorted({nodes[q]["module"] for q in members})
        for q in members:
            cycle_label[q] = label
        cycles.append({
            "id": label,
            "size": len(members),
            "level": level[i],
            "modules": mods,
            "classes": members,
        })

    # global deepest-first audit order: SCCs by level, members by sub-order
    order_sccs = sorted(range(len(sccs)),
                        key=lambda i: (level[i],
                                       nodes[sorted(sccs[i])[0]]["module"],
                                       sorted(sccs[i])[0]))
    audit_order = []
    for i in order_sccs:
        audit_order.extend(scc_suborder(sccs[i], nodes))

    for idx, q in enumerate(audit_order, start=1):
        nodes[q]["audit_index"] = idx
        nodes[q]["level"] = level[scc_of[q]]
        nodes[q]["cycle"] = cycle_label.get(q)

    max_level = max((level[i] for i in range(len(sccs))), default=0)
    levels = []
    for lv in range(max_level + 1):
        members = [q for q in audit_order if nodes[q]["level"] == lv]
        levels.append({"level": lv, "count": len(members), "classes": members})

    roots = sorted(q for q, n in nodes.items() if not n["depended_by"])
    leaves = sorted(q for q, n in nodes.items() if not n["depends_on"])
    return audit_order, levels, cycles, roots, leaves, max_level


# --------------------------------------------------------------------------- #
# Nested ASCII tree (optional)
# --------------------------------------------------------------------------- #

def render_nested_tree(nodes, roots) -> str:
    lines = ["Collect class dependency tree (root -> dependencies; audit bottom-up)",
             "  ↑seen = expanded earlier   ⟲cycle = back-edge into an ancestor", ""]
    expanded: set[str] = set()

    def walk(q, depth, path):
        indent = "  " * depth
        n = nodes[q]
        tag = f" [{n['cycle']}]" if n.get("cycle") else ""
        if q in path:
            lines.append(f"{indent}- {q} ⟲cycle")
            return
        if q in expanded and n["depends_on"]:
            lines.append(f"{indent}- {q} ↑seen")
            return
        lines.append(f"{indent}- {q} · {n['module']}{tag}")
        expanded.add(q)
        newpath = path | {q}
        for d in n["depends_on"]:
            walk(d, depth + 1, newpath)

    for r in sorted(roots, key=lambda q: (nodes[q]["module"], q)):
        walk(r, 0, frozenset())
    # completeness: nodes only reachable through cycles never touched from a root
    for q in sorted(nodes, key=lambda q: (nodes[q]["module"], q)):
        if q not in expanded:
            walk(q, 0, frozenset())
    return "\n".join(lines) + "\n"


# --------------------------------------------------------------------------- #
# Markdown
# --------------------------------------------------------------------------- #

def render_markdown(nodes, audit_order, levels, cycles, roots, leaves, max_level, map_name):
    L = []
    w = L.append
    total = len(nodes)
    edges = sum(len(n["depends_on"]) for n in nodes.values())
    biggest = cycles[0] if cycles else None

    w("# Collect → iOS Migration — Class Audit Tree\n")
    w("Two files drive this audit:\n")
    w(f"- **`{map_name}`** — every class's functions & metadata. Look a class up by `qualified_name`.")
    w("- **this file** — the class dependency tree and the order to audit in. Classes only; no function detail.\n")

    w("## How to audit\n")
    w("Work **bottom-up** — deepest dependencies first, entry points last:\n")
    w("1. Go down the **Audit checklist** from Level 0. For each class, find it by `qualified_name` in "
      f"`{map_name}` to get its function list.")
    w("2. For that class ask: **Where is it in the iOS app?** and **Are its functions implemented?** Tick the box.")
    w("3. A class's dependencies (`needs ↓`) are at lower levels and are already audited by the time you reach it; "
      "its dependents come later.")
    w("4. A class tagged **`[C#]`** lives in a dependency **cycle** — it can't be linearised, so audit the whole "
      "cluster (see *Dependency clusters*) as one unit; a suggested inner order is provided.\n")

    w("## Overview\n")
    w(f"- Classes: **{total}** · dependency edges: **{edges}** · audit levels: **{max_level + 1}**")
    w(f"- Roots (nothing depends on them — audit last): **{len(roots)}**")
    w(f"- Leaves (depend on nothing internal — audit first): **{len(leaves)}**")
    w(f"- Dependency cycles: **{len(cycles)}**"
      + (f" — largest is **{biggest['size']} classes** in `{', '.join(biggest['modules'])}` "
         f"(cluster `{biggest['id']}`)" if biggest else ""))
    w("")

    if cycles:
        w("## ⟲ Dependency clusters (audit each as one group)\n")
        w("These classes are mutually recursive; dependency order can't separate them. "
          "Audit all members of a cluster together (order below is a heuristic, not a guarantee).\n")
        for c in cycles:
            w(f"### `{c['id']}` — {c['size']} classes · {', '.join(c['modules'])} · level {c['level']}\n")
            for q in c["classes"]:
                n = nodes[q]
                w(f"- [ ] `{q}` · {n['kind']} · used-by {len(n['depended_by'])}")
            w("")

    w("## Audit checklist — deepest first\n")
    for lv in levels:
        if not lv["count"]:
            continue
        tag = " — foundational (no internal dependencies)" if lv["level"] == 0 else ""
        w(f"### Level {lv['level']} — {lv['count']} classes{tag}\n")
        for q in lv["classes"]:
            n = nodes[q]
            cyc = f" `[{n['cycle']}]`" if n.get("cycle") else ""
            w(f"- [ ] `{q}` · {n['module']} · {n['kind']} · used-by {len(n['depended_by'])}{cyc}")
            deps = n["depends_on"]
            if deps:
                shown = ", ".join(f"`{d}`" for d in deps[:12])
                more = f" _(+{len(deps) - 12} more)_" if len(deps) > 12 else ""
                w(f"  - needs ↓: {shown}{more}")
        w("")

    w("## Roots — entry points, audit last\n")
    w("Nothing internal depends on these; each is the top of a dependency branch.\n")
    for q in sorted(roots, key=lambda q: (nodes[q]["module"], q)):
        n = nodes[q]
        w(f"- `{q}` · {n['module']} · {n['kind']} · needs {len(n['depends_on'])}")
    w("")
    return "\n".join(L)


# --------------------------------------------------------------------------- #
# Main
# --------------------------------------------------------------------------- #

def main(argv=None):
    ap = argparse.ArgumentParser(description=__doc__,
                                 formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("--map", default="codebase-map.json", help="Input map JSON")
    ap.add_argument("--out-json", default="migration-audit-tree.json")
    ap.add_argument("--out-md", default="migration-audit-tree.md")
    ap.add_argument("--nested-tree", metavar="PATH", default=None,
                    help="Also write an indented ASCII dependency tree to PATH")
    args = ap.parse_args(argv)

    sys.setrecursionlimit(1_000_000)
    nodes = load_graph(args.map)
    audit_order, levels, cycles, roots, leaves, max_level = build_tree(nodes)

    node_json = {
        q: {
            "module": n["module"],
            "kind": n["kind"],
            "file": n["file"],
            "line": n["line"],
            "audit_index": n["audit_index"],
            "level": n["level"],
            "cycle": n["cycle"],
            "depends_on": n["depends_on"],
            "depended_by": n["depended_by"],
        }
        for q, n in nodes.items()
    }
    result = {
        "meta": {
            "generator": "build_audit_tree.py",
            "source": os.path.basename(args.map),
            "purpose": "Class dependency tree for the Collect->iOS migration audit. "
                       "Audit bottom-up: level 0 (deepest) first, roots last. Look up "
                       "each class's functions in the source map by qualified_name.",
            "edge_meaning": "depends_on: A->B means class A uses internal class B",
            "totals": {
                "classes": len(nodes),
                "edges": sum(len(n["depends_on"]) for n in nodes.values()),
                "levels": max_level + 1,
                "roots": len(roots),
                "leaves": len(leaves),
                "cycles": len(cycles),
                "largest_cycle": cycles[0]["size"] if cycles else 0,
            },
        },
        "audit_order": audit_order,
        "levels": levels,
        "cycles": cycles,
        "roots": roots,
        "leaves": leaves,
        "nodes": node_json,
    }

    with open(args.out_json, "w", encoding="utf-8") as fh:
        json.dump(result, fh, indent=2, ensure_ascii=False)
        fh.write("\n")

    md = render_markdown(nodes, audit_order, levels, cycles, roots, leaves,
                         max_level, os.path.basename(args.map))
    with open(args.out_md, "w", encoding="utf-8") as fh:
        fh.write(md)

    if args.nested_tree:
        with open(args.nested_tree, "w", encoding="utf-8") as fh:
            fh.write(render_nested_tree(nodes, roots))

    print("Wrote %s and %s" % (args.out_json, args.out_md))
    print("Classes: %d  Levels: %d  Roots: %d  Leaves: %d  Cycles: %d (largest %d)" % (
        len(nodes), max_level + 1, len(roots), len(leaves), len(cycles),
        cycles[0]["size"] if cycles else 0))
    if args.nested_tree:
        print("Wrote nested tree %s" % args.nested_tree)
    return 0


if __name__ == "__main__":
    sys.exit(main())
