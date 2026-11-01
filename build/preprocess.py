"""Tiny line preprocessor for the Skywyrms source tree.

Directives (on their own line, any indentation):  //#if <expr>   //#elif <expr>   //#else   //#endif
<expr>: MC comparisons (MC >= 12102), the flags FABRIC FORGE NEOFORGE AUTOTEST, && || ! ( ) true false.
Inactive lines are dropped; directive lines are dropped too.
"""
import os, re, sys

TOKEN = re.compile(r"\s*(>=|<=|==|!=|&&|\|\||[()<>!]|\d+|[A-Za-z_][A-Za-z_0-9]*)")


def evaluate(expr, env):
    py = []
    pos = 0
    expr = expr.strip()
    while pos < len(expr):
        m = TOKEN.match(expr, pos)
        if not m:
            raise ValueError(f"bad expression: {expr!r} at {pos}")
        tok = m.group(1)
        pos = m.end()
        if tok == "&&":
            py.append(" and ")
        elif tok == "||":
            py.append(" or ")
        elif tok == "!":
            py.append(" not ")
        elif tok in ("true", "false"):
            py.append(" True " if tok == "true" else " False ")
        elif tok[0].isalpha() or tok[0] == "_":
            py.append(f" {env.get(tok, 0)!r} ")
        else:
            py.append(f" {tok} ")
    return bool(eval("".join(py), {"__builtins__": {}}, {}))


def process(lines, env, name="?"):
    out = []
    stack = []  # (parent_active, branch_taken, active)
    active = True
    for no, line in enumerate(lines, 1):
        s = line.strip()
        if s.startswith("//#if "):
            v = evaluate(s[6:], env)
            stack.append((active, v, active and v))
            active = active and v
        elif s.startswith("//#elif "):
            parent, taken, _ = stack.pop()
            v = (not taken) and evaluate(s[8:], env)
            stack.append((parent, taken or v, parent and v))
            active = parent and v
        elif s == "//#else":
            parent, taken, _ = stack.pop()
            stack.append((parent, True, parent and not taken))
            active = parent and not taken
        elif s == "//#endif":
            parent, _, _ = stack.pop()
            active = parent
        elif active:
            out.append(line)
    if stack:
        raise ValueError(f"{name}: unterminated //#if")
    return out


def env_for(loader, mc):
    p = [int(x) for x in mc.split(".")] + [0, 0]
    return {"MC": p[0] * 10000 + p[1] * 100 + p[2], "FABRIC": loader == "fabric", "FORGE": loader == "forge",
            "NEOFORGE": loader == "neoforge"}


def tree(src_root, loader, mc, out_root, extra=()):
    env = env_for(loader, mc)
    for flag in extra:
        env[flag] = True
    n = 0
    for part in ("common", loader):
        base = os.path.join(src_root, part, "java")
        for root, _, fs in os.walk(base):
            for f in fs:
                if not f.endswith(".java"):
                    continue
                p = os.path.join(root, f)
                rel = os.path.relpath(p, base)
                lines = open(p, encoding="utf-8").read().split("\n")
                res = process(lines, env, rel)
                if not env.get("AUTOTEST") and any("//#autotest-only" in l for l in res[:3]):
                    continue
                q = os.path.join(out_root, rel)
                os.makedirs(os.path.dirname(q), exist_ok=True)
                open(q, "w", encoding="utf-8").write("\n".join(res))
                n += 1
    return n


if __name__ == "__main__":
    src, loader, mc, out = sys.argv[1:5]
    print(tree(src, loader, mc, out, sys.argv[5:]))
