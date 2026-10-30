#!/usr/bin/env python3
"""Codemap: a lexical index of this repository's Kotlin, for finding what already exists.

Repo-local and unshipped (AGENTS.md -> Harness; docs/HARNESS_LOCAL.md). Stdlib only, Python 3.11+.

Scope: tracked `*.kt` files under {app,domain,platform}/src/{main,debug,test}/kotlin, listed by
`git ls-files -z` (a new file is invisible until it is staged) and read from the working tree.

What it is not: every answer is lexical. The tokenizer and declaration reader understand enough
Kotlin to find top-level declarations; they are not a Kotlin parser and resolve nothing
semantically. An empty answer means "none found within the scanned scope", never "none exist".

Failure is loud: a failing git command, an unreadable tracked file, invalid UTF-8, lexical input
the tokenizer cannot close, and depth-0 syntax the declaration reader does not support are each
an error naming the file and position (exit 3). The tool never prints a smaller result that looks
complete. One limit: past a declaration's first token its tail is free-form up to the next line
that starts a declaration, so a top-level statement there is read as part of that declaration.
Only code the Kotlin compiler rejects can hold one, and the ladder's Gradle rung compiles it.

References are lexical candidates: an identifier is a candidate on a top-level symbol its file can
see through its package, an import or a qualified name (Index has the rules). Approximations:
- a local name, parameter or member that shadows a top-level one gives a false positive;
- so does a same-named member of another type, such as x.compute() beside a top-level compute;
- references from XML, the manifest, Gradle, reflection or generated code are invisible.

Exit statuses: 0 success (with or without results), 2 usage, 3 malformed input or a failed scan,
4 a path outside the scanned scope, 5 an unknown symbol, 6 an ambiguous symbol, 7 an unknown
feature.
"""

from __future__ import annotations

import argparse
import json
import re
import subprocess
import sys
from dataclasses import dataclass, field
from pathlib import Path

if sys.version_info < (3, 11):
    sys.exit("codemap.py needs Python 3.11 or later (tomllib)")

EXIT_OK = 0
EXIT_USAGE = 2
EXIT_MALFORMED = 3
EXIT_OUT_OF_SCOPE = 4
EXIT_UNKNOWN_SYMBOL = 5
EXIT_AMBIGUOUS_SYMBOL = 6
EXIT_UNKNOWN_FEATURE = 7

SCHEMA = 1
MODULES = ("app", "domain", "platform")
SOURCE_SETS = ("main", "debug", "test")
SCOPE_RE = re.compile(r"^(app|domain|platform)/src/(main|debug|test)/kotlin/.+\.kt$")
SCOPE_TEXT = "tracked *.kt under {app,domain,platform}/src/{main,debug,test}/kotlin"
APPROXIMATIONS = (
    "a local name, parameter or member that shadows a top-level one gives a false positive",
    "so does a same-named member of another type, such as x.compute() beside a top-level compute",
    "references from XML, the manifest, Gradle, reflection or generated code are invisible",
)
EMPTY = "no references found within scanned scope"
CITATION_RE = re.compile(r"(?<!\w)D[A-Z]*-\d+(?!\w)")  # the ladder's citation shape, whole words

DECL_KEYWORDS = {"class", "interface", "object", "typealias", "fun", "val", "var"}
CLASS_MODIFIERS = ("data", "enum", "sealed", "value", "annotation", "inner")
MODIFIERS = {
    "public", "private", "internal", "protected",
    "data", "enum", "sealed", "value", "annotation", "inner",
    "abstract", "open", "final", "override", "const", "lateinit",
    "inline", "noinline", "crossinline", "suspend", "operator", "infix",
    "tailrec", "external", "expect", "actual",
}
VISIBILITIES = ("private", "internal", "protected", "public")


class CodemapError(Exception):
    def __init__(self, message: str, code: int = EXIT_MALFORMED):
        super().__init__(message)
        self.code = code


def fail_at(path: str, line: int, col: int, message: str) -> CodemapError:
    return CodemapError(f"{path}:{line}:{col}: {message}")


# ---------------------------------------------------------------- tokenizer


@dataclass(frozen=True)
class Token:
    kind: str  # ident, op, num, str (a string's opening quote) or char
    text: str
    line: int
    col: int
    end_line: int
    template: bool = False  # an identifier from a `$name` string template


IDENT_RE = re.compile(r"[^\W\d]\w*")
NUMBER_RE = re.compile(
    r"0[xX][0-9a-fA-F_]+[uUlL]*|0[bB][01_]+[uUlL]*"
    r"|\d[\d_]*(?:\.\d[\d_]*)?(?:[eE][+-]?\d[\d_]*)?[fFdDuUlL]*"
)
MULTI_OPS = ("::", "->", "?.", "?:", "..<", "..", "!!", "==", "!=", "<=", ">=", "&&", "||",
             "+=", "-=", "*=", "/=", "%=", "++", "--")
OPEN = {"(": ")", "[": "]", "{": "}"}
CLOSE = {")", "]", "}"}


def normalise_newlines(text: str) -> str:
    return text.replace("\r\n", "\n").replace("\r", "\n")


class Tokenizer:
    """Kotlin tokens with positions. Comments and string text are dropped; template code stays.

    Bracket balance across (), [] and {} is checked here, `${` counting as an opening brace.
    """

    def __init__(self, path: str, text: str):
        self.path = path
        self.s = normalise_newlines(text)
        if self.s.startswith("﻿"):
            self.s = self.s[1:]
        self.i = 0
        self.line = 1
        self.col = 1
        self.tokens: list[Token] = []
        self.stack: list[tuple[str, int, int]] = []

    def err(self, message: str, line: int | None = None, col: int | None = None) -> CodemapError:
        return fail_at(self.path, line or self.line, col or self.col, message)

    def advance(self, n: int) -> None:
        for ch in self.s[self.i:self.i + n]:
            if ch == "\n":
                self.line += 1
                self.col = 1
            else:
                self.col += 1
        self.i += n

    def emit(self, kind: str, text: str, line: int, col: int, template: bool = False) -> None:
        self.tokens.append(Token(kind, text, line, col, self.line, template))

    def run(self) -> list[Token]:
        if self.s.startswith("#!"):
            nl = self.s.find("\n")
            self.advance(len(self.s) if nl < 0 else nl)
        self.code(until_brace=False)
        if self.stack:
            ch, line, col = self.stack[-1]
            raise self.err(f"unbalanced bracket: '{ch}' is never closed", line, col)
        return self.tokens

    def code(self, until_brace: bool) -> None:
        """Tokenize code. With until_brace, stop after the `}` that closes a `${` template."""
        base = len(self.stack)
        s = self.s
        while self.i < len(s):
            ch = s[self.i]
            if ch in " \t\n\f﻿":
                self.advance(1)
                continue
            if s.startswith("//", self.i):
                nl = s.find("\n", self.i)
                self.advance((len(s) if nl < 0 else nl) - self.i)
                continue
            if s.startswith("/*", self.i):
                self.block_comment()
                continue
            line, col = self.line, self.col
            if ch == '"':
                if s.startswith('"""', self.i):
                    self.raw_string()
                else:
                    self.string()
                continue
            if ch == "'":
                self.char_literal()
                continue
            if ch == "`":
                end = s.find("`", self.i + 1)
                nl = s.find("\n", self.i + 1)
                if end < 0 or (0 <= nl < end) or end == self.i + 1:
                    raise self.err("unterminated backtick identifier")
                name = s[self.i + 1:end]
                self.advance(end + 1 - self.i)
                self.emit("qident", name, line, col)
                continue
            m = IDENT_RE.match(s, self.i)
            if m:
                self.advance(m.end() - self.i)
                self.emit("ident", m.group(), line, col)
                continue
            m = NUMBER_RE.match(s, self.i)
            if m:
                self.advance(m.end() - self.i)
                self.emit("num", m.group(), line, col)
                continue
            if ch in OPEN:
                self.stack.append((ch, line, col))
                self.advance(1)
                self.emit("op", ch, line, col)
                continue
            if ch in CLOSE:
                if len(self.stack) == base:
                    if until_brace and ch == "}":
                        self.advance(1)
                        self.emit("op", "}", line, col)
                        return
                    raise self.err(f"unbalanced bracket: '{ch}' closes nothing")
                opener, oline, ocol = self.stack.pop()
                if OPEN[opener] != ch:
                    raise self.err(
                        f"unbalanced bracket: '{ch}' does not match '{opener}' at {oline}:{ocol}"
                    )
                self.advance(1)
                self.emit("op", ch, line, col)
                continue
            for op in MULTI_OPS:
                if s.startswith(op, self.i):
                    self.advance(len(op))
                    self.emit("op", op, line, col)
                    break
            else:
                self.advance(1)
                self.emit("op", ch, line, col)
        if until_brace:
            raise self.err("unterminated string template '${'")

    def block_comment(self) -> None:
        line, col = self.line, self.col
        depth = 0
        s = self.s
        while self.i < len(s):
            if s.startswith("/*", self.i):
                depth += 1
                self.advance(2)
            elif s.startswith("*/", self.i):
                depth -= 1
                self.advance(2)
                if depth == 0:
                    return
            else:
                self.advance(1)
        raise self.err("unterminated block comment", line, col)

    def template_at(self) -> bool:
        """At a `$` inside a string: consume a template if one starts here."""
        s = self.s
        if s.startswith("${", self.i):
            line, col = self.line, self.col
            self.advance(2)
            self.emit("op", "{", line, col)
            saved = self.stack
            self.stack = []
            self.code(until_brace=True)
            self.stack = saved
            return True
        m = IDENT_RE.match(s, self.i + 1)
        if m:
            line, col = self.line, self.col + 1
            self.advance(m.end() - self.i)
            self.emit("ident", m.group(), line, col, template=True)
            return True
        return False

    def string(self) -> None:
        line, col = self.line, self.col
        self.emit("str", '"', line, col)
        self.advance(1)
        s = self.s
        while self.i < len(s):
            ch = s[self.i]
            if ch == "\n":
                break
            if ch == "\\":
                self.advance(2)
            elif ch == '"':
                self.advance(1)
                return
            elif ch == "$" and self.template_at():
                continue
            else:
                self.advance(1)
        raise self.err("unterminated string", line, col)

    def raw_string(self) -> None:
        line, col = self.line, self.col
        self.emit("str", '"""', line, col)
        self.advance(3)
        s = self.s
        while self.i < len(s):
            ch = s[self.i]
            if ch == '"':
                run = 0
                while self.i + run < len(s) and s[self.i + run] == '"':
                    run += 1
                self.advance(run)
                if run >= 3:  # a longer run closes at its last quote; the extras are content
                    return
            elif ch == "$" and self.template_at():
                continue
            else:
                self.advance(1)
        raise self.err("unterminated raw string", line, col)

    def char_literal(self) -> None:
        line, col = self.line, self.col
        s = self.s
        j = self.i + 1
        if j < len(s) and s[j] == "\\":
            j += 2
            if s[j - 1:j] == "u":
                j += 4
        else:
            j += 1
        if j >= len(s) or s[j] != "'" or "\n" in s[self.i:j]:
            raise self.err("unterminated character literal", line, col)
        text = s[self.i:j + 1]
        self.advance(j + 1 - self.i)
        self.emit("char", text, line, col)


def tokenize(path: str, text: str) -> list[Token]:
    return Tokenizer(path, text).run()


def is_name(tok: Token | None) -> bool:
    return tok is not None and tok.kind in ("ident", "qident") and not tok.template


def is_word(tok: Token | None, *words: str) -> bool:
    return tok is not None and tok.kind == "ident" and not tok.template and tok.text in words


def is_op(tok: Token | None, *ops: str) -> bool:
    return tok is not None and tok.kind == "op" and tok.text in ops


# ---------------------------------------------------------------- declarations


@dataclass(frozen=True)
class Declaration:
    path: str
    package: str
    name: str
    kind: str
    visibility: str
    line: int
    col: int


@dataclass(frozen=True)
class Import:
    line: int
    col: int
    target: str  # dotted, without `.*`
    star: bool
    alias: str | None


@dataclass
class FileIndex:
    path: str
    module: str
    source_set: str
    package: str = ""
    imports: list[Import] = field(default_factory=list)
    declarations: list[Declaration] = field(default_factory=list)
    tokens: list[Token] = field(default_factory=list)
    header_start: int = 0  # the package header's first token, after any file annotations
    code_start: int = 0  # first token after the package header and imports
    name_tokens: set[int] = field(default_factory=set)  # top-level declaration names
    citations: list[tuple[str, int]] = field(default_factory=list)  # (ledger ID, line)

    @property
    def role(self) -> str:
        return "test" if self.source_set == "test" else "production"


class DeclarationReader:
    """Top-level declarations: a declaration keyword at nesting depth 0 across (), [] and {}.

    Between declarations only a package header, imports, annotations, modifiers and declaration
    keywords are supported; anything else at depth 0 there is an error. A declaration's tail (its
    type, supertypes, body, initializer or expression body) runs to the next line or `;` at depth
    0 that starts another declaration.
    """

    def __init__(self, path: str, tokens: list[Token]):
        self.path = path
        self.t = tokens
        self.n = len(tokens)
        self.depth: list[int] = []
        self.match: dict[int, int] = {}
        self.boundary: list[bool] = []
        d = 0
        stack: list[int] = []
        prev_end = 0
        for k, tok in enumerate(tokens):
            if is_op(tok, ")", "]", "}"):
                d -= 1
                self.match[stack.pop()] = k
            self.depth.append(d)
            if is_op(tok, "(", "[", "{"):
                d += 1
                stack.append(k)
            self.boundary.append(not tok.template and tok.line > prev_end)
            prev_end = tok.end_line

    def tok(self, k: int) -> Token | None:
        return self.t[k] if 0 <= k < self.n else None

    def err(self, k: int, message: str) -> CodemapError:
        tok = self.tok(k)
        if tok is None:
            last = self.t[-1] if self.t else None
            return fail_at(self.path, last.end_line if last else 1, 1, message)
        return fail_at(self.path, tok.line, tok.col, message)

    def describe(self, k: int) -> str:
        tok = self.tok(k)
        return "end of file" if tok is None else f"'{tok.text}'"

    def skip_bracket(self, k: int) -> int:
        return self.match[k] + 1

    def skip_angle(self, k: int) -> int:
        """From a `<`, past its matching `>`; brackets inside are skipped whole."""
        level = 0
        while k < self.n:
            tok = self.t[k]
            if is_op(tok, "(", "[", "{"):
                k = self.skip_bracket(k)
                continue
            if is_op(tok, "<"):
                level += 1
            elif is_op(tok, ">"):
                level -= 1
                if level == 0:
                    return k + 1
            elif is_op(tok, "=", ";") or is_word(tok, *DECL_KEYWORDS):
                break
            k += 1
        raise self.err(k, "unsupported depth-0 syntax: unclosed '<'")

    def skip_annotation(self, k: int) -> int:
        """From an `@`: `@[...]`, or `@[target:]Name[.Name]*[<...>][(...)]`."""
        k += 1
        if is_op(self.tok(k), "["):
            return self.skip_bracket(k)
        if is_name(self.tok(k)) and is_op(self.tok(k + 1), ":") and is_name(self.tok(k + 2)):
            k += 2
        if not is_name(self.tok(k)):
            raise self.err(k, f"unsupported depth-0 syntax: annotation name expected, got {self.describe(k)}")
        name_tok = self.t[k]
        k += 1
        while is_op(self.tok(k), ".") and is_name(self.tok(k + 1)):
            name_tok = self.t[k + 1]
            k += 2
        if is_op(self.tok(k), "<"):
            k = self.skip_angle(k)
        nxt = self.tok(k)
        if is_op(nxt, "(") and nxt.line == name_tok.end_line:
            k = self.skip_bracket(k)
        return k

    def name_chain(self, k: int) -> tuple[int, int]:
        """A receiver-or-name chain `A[<..>][?].B...`: returns (index of the last name, next)."""
        if not is_name(self.tok(k)):
            raise self.err(k, f"unsupported depth-0 syntax: name expected, got {self.describe(k)}")
        last = k
        k += 1
        while True:
            if is_op(self.tok(k), "<"):
                k = self.skip_angle(k)
            elif is_op(self.tok(k), "?"):
                k += 1
            elif is_op(self.tok(k), ".", "?.") and is_name(self.tok(k + 1)):
                last = k + 1
                k += 2
            else:
                return last, k

    def fun_is_named(self, k: int) -> bool:
        """At `fun`: false for an anonymous function, `fun(...)` or `fun A.(...)` (an expression)."""
        j = k + 1
        if is_op(self.tok(j), "<"):
            j = self.skip_angle(j)
        if is_op(self.tok(j), "("):
            j = self.skip_bracket(j)
            if not is_op(self.tok(j), "."):
                return False
            j += 1
            if not is_name(self.tok(j)):
                return False
        if not is_name(self.tok(j)):
            return True  # malformed: header() reports it
        _, j = self.name_chain(j)
        return not is_op(self.tok(j), ".", "?.")

    def lone_modifier(self, k: int) -> bool:
        """A modifier word with nothing after it on its line: an expression, or a split header."""
        tok, nxt = self.t[k], self.tok(k + 1)
        return is_word(tok, *MODIFIERS) and (nxt is None or nxt.line > tok.end_line)

    def decl_start(self, k: int) -> tuple[int, list[str]] | None:
        """Annotations and modifiers, then a declaration keyword: (keyword index, modifiers)."""
        mods: list[str] = []
        while k < self.n:
            tok = self.t[k]
            if is_op(tok, "@"):
                k = self.skip_annotation(k)
            elif is_word(tok, "fun") and is_word(self.tok(k + 1), "interface"):
                mods.append("fun")
                k += 1
            elif is_word(tok, *DECL_KEYWORDS):
                if tok.text == "object" and not is_name(self.tok(k + 1)):
                    return None
                if tok.text == "fun" and not self.fun_is_named(k):
                    return None
                return k, mods
            elif is_word(tok, *MODIFIERS) and not self.lone_modifier(k):
                mods.append(tok.text)
                k += 1
            else:
                return None
        return None

    def header(self, k: int, mods: list[str]) -> tuple[int, str, int]:
        """At a declaration keyword: (name index, kind, index after the name)."""
        kw = self.t[k].text
        if kw in ("class", "interface", "object", "typealias"):
            if not is_name(self.tok(k + 1)):
                raise self.err(k + 1, f"unsupported depth-0 syntax: {kw} name expected, got {self.describe(k + 1)}")
            prefix = [m for m in ("fun",) + CLASS_MODIFIERS if m in mods]
            return k + 1, " ".join(prefix + [kw]), k + 2
        j = k + 1
        if is_op(self.tok(j), "<"):
            j = self.skip_angle(j)
        if is_op(self.tok(j), "("):  # a parenthesised receiver type, `fun (A).b()`
            j = self.skip_bracket(j) + 1
        last, j = self.name_chain(j)
        return last, kw, j

    def tail(self, k: int) -> int:
        while k < self.n:
            tok = self.t[k]
            if self.depth[k] != 0:
                k += 1
                continue
            if is_op(tok, ";"):
                return k + 1
            if self.boundary[k] and self.decl_start(k) is not None:
                return k
            if self.boundary[k] and self.lone_modifier(k) and self.decl_start(k + 1) is not None:
                raise self.err(k, f"unsupported depth-0 syntax: '{tok.text}' alone on its line "
                                  "before a declaration (a split header, or an expression)")
            if is_word(tok, *DECL_KEYWORDS):
                prev = self.tok(k - 1)
                anonymous = (
                    (tok.text == "class" and is_op(prev, "::"))
                    or (tok.text == "object" and not is_name(self.tok(k + 1)))
                    or (tok.text == "fun" and not self.fun_is_named(k))
                )
                if not anonymous:
                    raise self.err(k, f"unsupported depth-0 syntax: '{tok.text}' inside a declaration")
            k += 1
        return k

    def qualified(self, k: int, what: str) -> tuple[list[str], int]:
        if not is_name(self.tok(k)):
            raise self.err(k, f"unsupported depth-0 syntax: {what} name expected, got {self.describe(k)}")
        parts = [self.t[k].text]
        k += 1
        while is_op(self.tok(k), ".") and is_name(self.tok(k + 1)):
            parts.append(self.t[k + 1].text)
            k += 2
        return parts, k

    def read(self, index: FileIndex) -> None:
        k = 0
        while is_op(self.tok(k), "@"):
            k = self.skip_annotation(k)
        index.header_start = k
        if is_word(self.tok(k), "package"):
            parts, k = self.qualified(k + 1, "package")
            index.package = ".".join(parts)
            if is_op(self.tok(k), ";"):
                k += 1
        while is_word(self.tok(k), "import"):
            start = self.t[k]
            parts, k = self.qualified(k + 1, "import")
            star = False
            alias = None
            if is_op(self.tok(k), ".") and is_op(self.tok(k + 1), "*"):
                star = True
                k += 2
            elif is_word(self.tok(k), "as") and is_name(self.tok(k + 1)):
                alias = self.t[k + 1].text
                k += 2
            index.imports.append(Import(start.line, start.col, ".".join(parts), star, alias))
            if is_op(self.tok(k), ";"):
                k += 1
        index.code_start = k
        while k < self.n:
            if is_op(self.t[k], ";"):
                k += 1
                continue
            found = self.decl_start(k)
            if found is None:
                raise self.err(k, f"unsupported depth-0 syntax: {self.describe(k)} where a declaration should start")
            kw, mods = found
            name_at, kind, after = self.header(kw, mods)
            name = self.t[name_at]
            index.name_tokens.add(name_at)
            visibility = next((m for m in VISIBILITIES if m in mods), "public")
            index.declarations.append(
                Declaration(self.path, index.package, name.text, kind, visibility, name.line, name.col)
            )
            k = self.tail(after)


def read_source(path: str, data: bytes) -> str:
    try:
        return data.decode("utf-8")
    except UnicodeDecodeError as exc:
        prefix = normalise_newlines(data[:exc.start].decode("utf-8"))
        line = prefix.count("\n") + 1
        col = len(prefix) - (prefix.rfind("\n") + 1) + 1
        raise fail_at(path, line, col, f"invalid UTF-8 byte 0x{data[exc.start]:02x}") from None


def index_file(path: str, data: bytes) -> FileIndex:
    m = SCOPE_RE.match(path)
    if not m:
        raise CodemapError(f"{path}: outside the scanned scope ({SCOPE_TEXT})", EXIT_OUT_OF_SCOPE)
    index = FileIndex(path, m.group(1), m.group(2))
    text = read_source(path, data)
    index.tokens = tokenize(path, text)
    DeclarationReader(path, index.tokens).read(index)
    for line_no, line in enumerate(normalise_newlines(text).split("\n"), 1):
        index.citations.extend((cid, line_no) for cid in CITATION_RE.findall(line))
    return index


# ---------------------------------------------------------------- repository input


def git(root: Path, *args: str) -> bytes:
    try:
        proc = subprocess.run(["git", "-C", str(root), *args], capture_output=True, check=False)
    except OSError as exc:
        raise CodemapError(f"git could not run: {exc}") from None
    if proc.returncode != 0:
        detail = proc.stderr.decode("utf-8", "replace").strip()
        raise CodemapError(f"git {' '.join(args)} failed ({proc.returncode}): {detail}")
    return proc.stdout


def find_root(explicit: str | None) -> Path:
    if explicit:
        base = Path(explicit)
    else:
        base = Path(__file__).resolve().parent
    return Path(git(base, "rev-parse", "--show-toplevel").decode("utf-8").strip())


def scanned_paths(root: Path) -> list[str]:
    listed = git(root, "ls-files", "-z", "--", "*.kt").decode("utf-8").split("\0")
    return sorted(p for p in listed if p and SCOPE_RE.match(p))


def build_index(root: Path) -> list[FileIndex]:
    files = []
    for path in scanned_paths(root):
        try:
            data = (root / path).read_bytes()
        except OSError as exc:
            raise CodemapError(f"{path}: unreadable tracked file: {exc.strerror}") from None
        files.append(index_file(path, data))
    return files


# ---------------------------------------------------------------- reference candidates


@dataclass(frozen=True, order=True)
class Symbol:
    package: str
    name: str

    @property
    def qualified(self) -> str:
        return f"{self.package}.{self.name}" if self.package else self.name


@dataclass(frozen=True, order=True)
class Reference:
    path: str
    line: int
    col: int
    symbol: Symbol
    kind: str  # same-package, import, member-import, star, qualified or import-only
    ambiguous: bool


class Index:
    """Symbols (package plus name; overloads are one symbol) and their reference candidates.

    A bare name resolves in Kotlin's scope order: an explicit import (an `as` alias or a member
    import included), then the file's own package, then star imports. An explicit import of a
    name outside the scanned scope still shadows the levels below it. Several symbols at the
    winning level each get a candidate marked ambiguous; nothing picks one.
    """

    def __init__(self, files: list[FileIndex]):
        self.files = files
        self.by_path = {f.path: f for f in files}
        self.decls: dict[Symbol, list[Declaration]] = {}
        self.by_name: dict[str, list[Symbol]] = {}
        modules: dict[str, set[str]] = {}
        for f in files:
            modules.setdefault(f.package, set()).add(f.module)
            for d in f.declarations:
                sym = Symbol(d.package, d.name)
                if sym not in self.decls:
                    self.decls[sym] = []
                    self.by_name.setdefault(d.name, []).append(sym)
                self.decls[sym].append(d)
        for package, mods in sorted(modules.items()):
            if len(mods) > 1:  # `internal` would then need a module rule
                raise CodemapError(f"package {package or '<default>'} spans modules "
                                   f"{', '.join(sorted(mods))}; the resolver assumes one module per package")
        self.packages = set(modules)
        self._refs: list[Reference] | None = None

    def visible(self, sym: Symbol, f: FileIndex) -> bool:
        for d in self.decls.get(sym, ()):
            decl_set = self.by_path[d.path].source_set
            if d.visibility == "private" and d.path != f.path:
                continue
            if decl_set == "main" or decl_set == f.source_set:
                return True
        return False

    def own(self, sym: Symbol, f: FileIndex) -> bool:
        return any(d.path == f.path for d in self.decls[sym])

    def references(self) -> list[Reference]:
        if self._refs is None:
            refs: list[Reference] = []
            for f in self.files:
                refs.extend(self.resolve_file(f))
            self._refs = sorted(set(refs))
        return self._refs

    def resolve_file(self, f: FileIndex) -> list[Reference]:
        bound: dict[str, list[tuple[Symbol, str, Import]]] = {}  # name -> (symbol, kind, import)
        stars: list[str] = []
        refs: list[Reference] = []
        for imp in f.imports:
            owner = None if imp.star and imp.target in self.packages else self.import_owner(imp.target)
            if imp.star:
                if owner is None:
                    stars.append(imp.target)
                elif self.visible(owner, f) and not self.own(owner, f):  # `import p.Outer.*`
                    refs.append(Reference(f.path, imp.line, imp.col, owner, "member-import", False))
                continue
            entries = bound.setdefault(imp.alias or imp.target.rpartition(".")[2], [])
            if owner is not None and self.visible(owner, f) and not self.own(owner, f):
                kind = "import" if owner.qualified == imp.target else "member-import"
                entries.append((owner, kind, imp))

        used: set[str] = set()
        toks = f.tokens
        n = len(toks)
        i = 0
        while i < n:
            if f.header_start <= i < f.code_start:  # the package header and imports
                i = f.code_start
                continue
            tok = toks[i]
            if tok.kind not in ("ident", "qident") or i in f.name_tokens:
                i += 1
                continue
            after_dot = i > 0 and is_op(toks[i - 1], ".", "?.")
            if not after_dot and tok.text not in bound:
                hit = self.qualified_at(toks, i)
                if hit is not None:
                    sym, at = hit
                    if self.visible(sym, f) and not self.own(sym, f):
                        refs.append(Reference(f.path, toks[at].line, toks[at].col, sym, "qualified", False))
                    i = at + 1
                    continue
            name = tok.text
            if name in bound:
                used.add(name)
                found = {sym: kind for sym, kind, _ in bound[name]}
            else:
                # The file's own package wins over star imports, even when the file itself
                # declares the name, which then hides the candidate.
                same = [s for s in self.by_name.get(name, ())
                        if s.package == f.package and self.visible(s, f)]
                if same:
                    found = {s: "same-package" for s in same if not self.own(s, f)}
                else:
                    found = {Symbol(p, name): "star" for p in stars
                             if Symbol(p, name) in self.decls and self.visible(Symbol(p, name), f)
                             and not self.own(Symbol(p, name), f)}
            for sym, kind in found.items():
                refs.append(Reference(f.path, tok.line, tok.col, sym, kind, len(found) > 1))
            i += 1
        for name, entries in bound.items():
            if name not in used:
                for sym, _, imp in entries:
                    refs.append(Reference(f.path, imp.line, imp.col, sym, "import-only", False))
        return refs

    def import_owner(self, target: str) -> Symbol | None:
        """The top-level symbol an import names or reaches into (`p.Outer.Middle.Leaf`)."""
        parts = target.split(".")
        for cut in range(len(parts) - 1, -1, -1):
            sym = Symbol(".".join(parts[:cut]), parts[cut])
            if sym in self.decls:
                return sym
        return None

    def qualified_at(self, toks: list[Token], i: int) -> tuple[Symbol, int] | None:
        """`a.b.C` from token i, where `a.b` is a scanned package and `C` its symbol."""
        parts = [i]
        j = i
        while j + 2 < len(toks) and is_op(toks[j + 1], ".") and toks[j + 2].kind in ("ident", "qident"):
            j += 2
            parts.append(j)
        for cut in range(len(parts) - 1, 0, -1):
            package = ".".join(toks[p].text for p in parts[:cut])
            sym = Symbol(package, toks[parts[cut]].text)
            if package in self.packages and sym in self.decls:
                return sym, parts[cut]
        return None

    def symbol_arg(self, arg: str) -> Symbol:
        if "." in arg:
            pkg, _, name = arg.rpartition(".")
            if Symbol(pkg, name) in self.decls:
                return Symbol(pkg, name)
            raise CodemapError(f"{arg}: no top-level symbol of that name in the scanned scope",
                               EXIT_UNKNOWN_SYMBOL)
        syms = sorted(self.by_name.get(arg, ()))
        if not syms:
            raise CodemapError(f"{arg}: no top-level symbol of that name in the scanned scope",
                               EXIT_UNKNOWN_SYMBOL)
        if len(syms) > 1:
            listed = "; ".join(
                f"{s.qualified} ({', '.join(sorted({d.path for d in self.decls[s]}))})" for s in syms)
            raise CodemapError(f"{arg} is ambiguous, qualify it: {listed}", EXIT_AMBIGUOUS_SYMBOL)
        return syms[0]


def resolve_path_arg(root: Path, arg: str, known: set[str]) -> str:
    """A repo-relative path first; else absolute or relative to the working directory."""
    candidate = Path(arg)
    if not candidate.is_absolute() and candidate.as_posix() in known:
        return candidate.as_posix()
    if not candidate.is_absolute():
        candidate = Path.cwd() / candidate
    if candidate.is_absolute():
        try:
            rel = candidate.resolve().relative_to(root.resolve()).as_posix()
        except ValueError:
            rel = None
    else:
        rel = candidate.as_posix()
    if rel not in known:
        raise CodemapError(f"{arg}: not a file in the scanned scope ({SCOPE_TEXT})", EXIT_OUT_OF_SCOPE)
    return rel


# ---------------------------------------------------------------- output


def write_json(payload: dict) -> None:
    text = json.dumps(payload, indent=2, sort_keys=True, ensure_ascii=False) + "\n"
    sys.stdout.buffer.write(text.encode("utf-8"))


def write_text(lines: list[str]) -> None:
    sys.stdout.buffer.write("".join(line + "\n" for line in lines).encode("utf-8"))


def cmd_declarations(root: Path, args: argparse.Namespace) -> int:
    files = build_index(root)
    if args.path:
        wanted = resolve_path_arg(root, args.path, {f.path for f in files})
        files = [f for f in files if f.path == wanted]
    rows = []
    for f in files:
        for d in sorted(f.declarations, key=lambda d: (d.name, d.line, d.col)):
            rows.append({
                "col": d.col, "kind": d.kind, "line": d.line, "module": f.module,
                "name": d.name, "package": d.package, "path": d.path, "role": f.role,
                "source_set": f.source_set, "visibility": d.visibility,
            })
    if args.json:
        write_json({"declarations": rows, "files": len(files), "schema": SCHEMA, "scope": SCOPE_TEXT})
        return EXIT_OK
    lines = [
        f"{r['path']}:{r['line']}:{r['col']}  {r['visibility']} {r['kind']} "
        + (f"{r['package']}.{r['name']}" if r["package"] else r["name"])
        for r in rows
    ]
    if not rows:
        lines.append("no declarations found within scanned scope")
    lines.append(f"-- {len(rows)} top-level declaration(s) in {len(files)} file(s); "
                 f"scope: {SCOPE_TEXT}; lexical, not semantic")
    write_text(lines)
    return EXIT_OK


def footer(lines: list[str]) -> None:
    lines.append(f"-- scope: {SCOPE_TEXT}; lexical reference candidates, not semantic resolution")
    lines.extend(f"-- {a}" for a in APPROXIMATIONS)


def ref_row(r: Reference, index: Index) -> dict:
    return {"ambiguous": r.ambiguous, "col": r.col, "kind": r.kind, "line": r.line,
            "path": r.path, "role": index.by_path[r.path].role}


def cmd_who_uses(root: Path, args: argparse.Namespace) -> int:
    index = Index(build_index(root))
    sym = index.symbol_arg(args.symbol)
    refs = [r for r in index.references() if r.symbol == sym]
    used = [r for r in refs if r.kind != "import-only"]
    only = [r for r in refs if r.kind == "import-only"]
    decls = sorted(index.decls[sym], key=lambda d: (d.path, d.line, d.col))
    if args.json:
        write_json({
            "approximations": list(APPROXIMATIONS), "schema": SCHEMA, "scope": SCOPE_TEXT,
            "symbol": {"declarations": [{"col": d.col, "kind": d.kind, "line": d.line,
                                         "path": d.path, "visibility": d.visibility} for d in decls],
                       "name": sym.name, "package": sym.package},
            "references": [ref_row(r, index) for r in used],
            "import_only": [ref_row(r, index) for r in only],
        })
        return EXIT_OK
    lines = [f"who-uses {sym.qualified}"]
    lines.extend(f"  declared {d.path}:{d.line}:{d.col}  {d.visibility} {d.kind}" for d in decls)
    for role in ("production", "test"):
        group = [r for r in used if index.by_path[r.path].role == role]
        if group:
            lines.append(f"{role}:")
            lines.extend(f"  {r.path}:{r.line}:{r.col}  {r.kind}" + ("  ambiguous" if r.ambiguous else "")
                         for r in group)
    if only:
        lines.append("import-only (imported, never used after the imports):")
        lines.extend(f"  {r.path}:{r.line}:{r.col}" for r in only)
    if not used:
        lines.append(EMPTY)
    footer(lines)
    write_text(lines)
    return EXIT_OK


def cmd_impact(root: Path, args: argparse.Namespace) -> int:
    index = Index(build_index(root))
    path = resolve_path_arg(root, args.path, set(index.by_path))
    f = index.by_path[path]
    declared = sorted({Symbol(d.package, d.name) for d in f.declarations})
    users: dict[str, set[str]] = {}
    importers: dict[str, set[str]] = {}
    ambiguous: dict[str, set[str]] = {}
    for r in index.references():
        if r.symbol in declared and r.path != path:
            target = importers if r.kind == "import-only" else users
            target.setdefault(r.path, set()).add(r.symbol.name)
            if r.ambiguous:
                ambiguous.setdefault(r.path, set()).add(r.symbol.name)
    cites: dict[str, list[int]] = {}
    for cid, line in f.citations:
        cites.setdefault(cid, []).append(line)

    def group(table: dict[str, set[str]], role: str | None) -> list[dict]:
        return [{"ambiguous": sorted(ambiguous.get(p, set()) & s), "path": p,
                 "role": index.by_path[p].role, "symbols": sorted(s)}
                for p, s in sorted(table.items()) if role is None or index.by_path[p].role == role]

    def listed(row: dict) -> str:
        return ", ".join(s + (" (ambiguous)" if s in row["ambiguous"] else "") for s in row["symbols"])

    decl_rows = [{"col": d.col, "kind": d.kind, "line": d.line, "name": d.name,
                  "visibility": d.visibility}
                 for d in sorted(f.declarations, key=lambda d: (d.name, d.line, d.col))]
    if args.json:
        write_json({
            "approximations": list(APPROXIMATIONS), "cites": {k: sorted(v) for k, v in sorted(cites.items())},
            "declares": decl_rows, "import_only": group(importers, None), "package": f.package,
            "path": path, "role": f.role, "schema": SCHEMA, "scope": SCOPE_TEXT,
            "used_by": group(users, None),
        })
        return EXIT_OK
    lines = [f"impact {path}  ({f.role}, {f.module}/{f.source_set}, package {f.package or '<default>'})",
             "declares:"]
    lines.extend(f"  {d['visibility']} {d['kind']} {d['name']}  ({d['line']}:{d['col']})" for d in decl_rows)
    for role, title in (("production", "used by (production):"), ("test", "used by (tests):")):
        rows = group(users, role)
        if rows:
            lines.append(title)
            lines.extend(f"  {r['path']}  {listed(r)}" for r in rows)
    if importers:
        lines.append("import-only (imported, never used after the imports):")
        lines.extend(f"  {r['path']}  {listed(r)}" for r in group(importers, None))
    if not users:
        lines.append(EMPTY)
    lines.append("cites: " + (", ".join(sorted(cites)) if cites else "no ledger rows"))
    footer(lines)
    write_text(lines)
    return EXIT_OK


class Parser(argparse.ArgumentParser):
    def error(self, message: str) -> None:  # usage errors exit 2, never 3
        self.print_usage(sys.stderr)
        sys.stderr.write(f"codemap.py: {message}\n")
        sys.exit(EXIT_USAGE)


def main(argv: list[str] | None = None) -> int:
    parser = Parser(prog="codemap.py", description=__doc__.split("\n\n")[0])
    parser.add_argument("--root", help="repository root (fixtures); default: this script's repository")
    sub = parser.add_subparsers(dest="command", required=True)
    p = sub.add_parser("declarations", help="print the top-level declaration index")
    p.add_argument("path", nargs="?", help="one scanned file, repo-relative or absolute")
    p.add_argument("--json", action="store_true")
    p = sub.add_parser("who-uses", help="reference candidates on one top-level symbol")
    p.add_argument("symbol", help="a bare name while it is unique, else package-qualified")
    p.add_argument("--json", action="store_true")
    p = sub.add_parser("impact", help="what a file declares, who uses it, which rows it cites")
    p.add_argument("path", help="one scanned file, repo-relative or absolute")
    p.add_argument("--json", action="store_true")
    args = parser.parse_args(argv)
    commands = {"declarations": cmd_declarations, "who-uses": cmd_who_uses, "impact": cmd_impact}
    try:
        root = find_root(args.root)
        return commands[args.command](root, args)
    except CodemapError as exc:
        sys.stderr.write(f"codemap.py: {exc}\n")
        return exc.code


if __name__ == "__main__":
    sys.exit(main())
