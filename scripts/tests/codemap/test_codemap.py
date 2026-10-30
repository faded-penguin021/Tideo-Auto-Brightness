"""Tests for scripts/codemap.py on synthetic Kotlin trees, plus one real-tree scan.

Run by scripts/verify.sh: python3 -B -m unittest discover -s scripts/tests/codemap
"""

import importlib.util
import json
import os
import re
import shutil
import subprocess
import sys
import tempfile
import unittest
from pathlib import Path

sys.dont_write_bytecode = True
REPO = Path(__file__).resolve().parents[3]
SCRIPT = REPO / "scripts" / "codemap.py"
_spec = importlib.util.spec_from_file_location("codemap", SCRIPT)
codemap = importlib.util.module_from_spec(_spec)
sys.modules["codemap"] = codemap
_spec.loader.exec_module(codemap)

MAIN = "app/src/main/kotlin/x"
TEST = "app/src/test/kotlin/x"


def idents(src):
    return [t.text for t in codemap.tokenize("F.kt", src) if t.kind in ("ident", "qident")]


def decls(src):
    index = codemap.index_file(f"{MAIN}/F.kt", src.encode("utf-8"))
    return [(d.name, d.kind, d.visibility) for d in index.declarations]


def names(src):
    return [d[0] for d in decls(src)]


def make_tree(root, files, newline="\n"):
    for rel, text in files.items():
        p = Path(root) / rel
        p.parent.mkdir(parents=True, exist_ok=True)
        p.write_bytes(text.replace("\n", newline).encode("utf-8") if isinstance(text, str) else text)
    subprocess.run(["git", "init", "-q", str(root)], check=True)
    subprocess.run(["git", "-C", str(root), "add", "-A"], check=True)


def run(args, cwd=None, seed="0"):
    env = dict(os.environ, PYTHONHASHSEED=seed)
    return subprocess.run([sys.executable, "-B", str(SCRIPT), *args], cwd=cwd, env=env,
                          capture_output=True)


TREE = {
    f"{MAIN}/Solar.kt": "package x\n\nimport kotlin.math.*\nimport y.Z as W\n\n"
                        "object SolarCalculator { fun at(h: Int) = h }\n"
                        "internal fun hourToHhmm(h: Int) = \"$h:00\"\n"
                        "fun Alpha() = 1\n",
    f"{MAIN}/Input.kt": "package x\n\ndata class Input(\n    val a: Int,\n    val b: Int,\n)\n",
    f"{TEST}/SolarTest.kt": "package x\n\nclass SolarTest { fun t() = SolarCalculator.at(1) }\n",
    "domain/src/main/kotlin/d/Thing.kt": "package d\n\nsealed interface Thing\n",
    "e2e/Ignored.kt": "this is not scanned (\n",
    "app/src/androidTest/kotlin/x/Ignored.kt": "neither is this (\n",
}


class Tokens(unittest.TestCase):
    def test_line_block_and_nested_comments(self):
        self.assertEqual(idents("a // b c\nd /* e /* f */ g */ h"), ["a", "d", "h"])

    def test_empty_block_comment_separates(self):
        self.assertEqual(idents("a/**/b"), ["a", "b"])

    def test_raw_string_text_dropped(self):
        self.assertEqual(idents('val x = """ foo // bar /* ( "" """\nval y = 1'),
                         ["val", "x", "val", "y"])

    def test_raw_string_quote_run_closes_at_last_quote(self):
        self.assertEqual(idents('val r = """foo"""" + after'), ["val", "r", "after"])

    def test_simple_template(self):
        self.assertEqual(idents('"hi $name!"'), ["name"])

    def test_block_template(self):
        self.assertEqual(idents('"${a + b(c)}"'), ["a", "b", "c"])

    def test_nested_template(self):
        self.assertEqual(idents('"${"in ${inner} x"} $outer"'), ["inner", "outer"])

    def test_escaped_dollar_is_text(self):
        self.assertEqual(idents(r'"\$notTemplate"'), [])

    def test_dollar_char_template_is_text(self):
        self.assertEqual(idents("\"${'$'}notTemplate\""), [])

    def test_char_literals_move_no_bracket(self):
        self.assertEqual(idents("val c = '{'\nval d = '\"'\nval e = '\\''"),
                         ["val", "c", "val", "d", "val", "e"])

    def test_positions(self):
        toks = codemap.tokenize("F.kt", "a\r\n  b\r c")
        self.assertEqual([(t.text, t.line, t.col) for t in toks], [("a", 1, 1), ("b", 2, 3), ("c", 3, 2)])


class Declarations(unittest.TestCase):
    def test_constructor_properties_are_not_top_level(self):
        self.assertEqual(names("data class A(\n    val x: Int,\n    var y: String,\n)"), ["A"])

    def test_bodyless_class(self):
        self.assertEqual(names("class B\nval z = 1"), ["B", "z"])

    def test_generic_extension_function(self):
        self.assertEqual(names("fun <T> List<T>.second(): T = this[1]\nfun Foo?.orNot() = 1"),
                         ["second", "orNot"])

    def test_parenthesised_receiver(self):
        self.assertEqual(names("fun (() -> Unit).twice() { this(); this() }"), ["twice"])

    def test_expression_bodied_function(self):
        self.assertEqual(names("fun f() = listOf(1)\n    .map { it }\nfun g() = 2"), ["f", "g"])

    def test_anonymous_object_and_function(self):
        src = ("val r = object : Runnable { override fun run() {} }\n"
               "fun g() = object : Runnable { override fun run() {} }\n"
               "val h = fun(x: Int) = x\n"
               "val k =\n    fun(x: Int) = x\n"
               "val e =\n    fun String.() {}\n"
               "val n =\n    fun String?.(): Int = 1\n"
               "val p =\n    fun (() -> Unit).() {}\n")
        self.assertEqual(names(src), ["r", "g", "h", "k", "e", "n", "p"])

    def test_property_with_accessors(self):
        src = "val p: Int\n    get() = 1\nprivate var q = 0\n    private set\nval s by lazy { 1 }"
        self.assertEqual(decls(src), [("p", "val", "public"), ("q", "var", "private"),
                                      ("s", "val", "public")])

    def test_extension_property(self):
        self.assertEqual(names("val <T> List<T>.second: T\n    get() = this[1]"), ["second"])

    def test_class_like_modifiers(self):
        src = ("data class A(val x: Int)\nenum class B { ONE, TWO; fun f() = 1 }\n"
               "sealed class C\nsealed interface D\n@JvmInline\nvalue class E(val v: Int)\n"
               "annotation class F\nfun interface G { fun g() }\ndata object H\n"
               "interface I\nobject J\ntypealias K = (Int) -> Unit\ninner class L")
        self.assertEqual([(n, k) for n, k, _ in decls(src)], [
            ("A", "data class"), ("B", "enum class"), ("C", "sealed class"),
            ("D", "sealed interface"), ("E", "value class"), ("F", "annotation class"),
            ("G", "fun interface"), ("H", "data object"), ("I", "interface"), ("J", "object"),
            ("K", "typealias"), ("L", "inner class")])

    def test_visibility(self):
        self.assertEqual(decls("private fun a() {}\ninternal val b = 1\npublic class C\nclass D"),
                         [("a", "fun", "private"), ("b", "val", "internal"),
                          ("C", "class", "public"), ("D", "class", "public")])

    def test_overloads_are_separate_declarations(self):
        self.assertEqual(names("fun o(x: Int) = x\nfun o(x: String) = x"), ["o", "o"])

    def test_annotations_and_header(self):
        src = ('@file:Suppress("X")\npackage a.b\n\nimport c.D\nimport e.*\nimport f.G as H\n\n'
               '@Suppress("Y")\n@Composable\nprivate fun C(\n    m: Modifier = Modifier,\n) {}\n'
               "@[A B] class Q\n")
        index = codemap.index_file(f"{MAIN}/F.kt", src.encode())
        self.assertEqual(index.package, "a.b")
        self.assertEqual([(i.target, i.star, i.alias) for i in index.imports],
                         [("c.D", False, None), ("e", True, None), ("f.G", False, "H")])
        self.assertEqual([d.name for d in index.declarations], ["C", "Q"])

    def test_class_literal_and_semicolons(self):
        self.assertEqual(names("val k = Foo::class; val m = 2\nclass N"), ["k", "m", "N"])

    def test_backtick_names(self):
        self.assertEqual(names("fun `a name`() = 1\nval `val` = 2"), ["a name", "val"])

    def test_template_keyword_text_is_not_a_declaration(self):
        self.assertEqual(names('val s = """\n$private\nfun x() = 1\n"""'), ["s"])


class Malformed(unittest.TestCase):
    def assertFailsAt(self, src, line, col, words):
        data = src if isinstance(src, bytes) else src.encode()
        with self.assertRaises(codemap.CodemapError) as cm:
            codemap.index_file(f"{MAIN}/F.kt", data)
        message = str(cm.exception)
        self.assertIn(f"{MAIN}/F.kt:{line}:{col}:", message)
        self.assertIn(words, message)
        self.assertEqual(cm.exception.code, codemap.EXIT_MALFORMED)

    def test_unterminated_string(self):
        self.assertFailsAt('val a = 1\nval s = "abc\n', 2, 9, "unterminated string")

    def test_unterminated_raw_string(self):
        self.assertFailsAt('val s = """abc\n', 1, 9, "unterminated raw string")

    def test_unterminated_block_comment(self):
        self.assertFailsAt("val a = 1 /* x /* y */\n", 1, 11, "unterminated block comment")

    def test_unterminated_template(self):
        self.assertFailsAt('val s = "${a\n', 2, 1, "unterminated string template")

    def test_unterminated_char(self):
        self.assertFailsAt("val c = 'ab'\n", 1, 9, "unterminated character literal")

    def test_unclosed_bracket(self):
        self.assertFailsAt("fun f(a: Int {\n}\n", 1, 6, "never closed")

    def test_stray_close(self):
        self.assertFailsAt("val a = 1)\n", 1, 10, "closes nothing")

    def test_mismatched_close(self):
        self.assertFailsAt("val a = (1]\n", 1, 11, "does not match")

    def test_invalid_utf8(self):
        self.assertFailsAt(b"val a = 1\nval b = \"\xff\"\n", 2, 10, "invalid UTF-8")

    def test_unsupported_depth0_statement(self):
        self.assertFailsAt('package x\nprintln("hi")\n', 2, 1, "unsupported depth-0 syntax")

    def test_unsupported_keyword_inside_declaration(self):
        self.assertFailsAt("val a = 1 val b = 2\n", 1, 11, "unsupported depth-0 syntax")

    def test_lone_modifier_before_declaration(self):
        # Either an initializer named like a modifier or a split header: never guess which.
        self.assertFailsAt("val data = 1\nval x =\n    data\nclass Plain\n", 3, 5, "alone on its line")
        self.assertFailsAt("val a = 1\nprivate\nfun f() = 1\n", 2, 1, "alone on its line")


class Cli(unittest.TestCase):
    def setUp(self):
        self.tmp = Path(tempfile.mkdtemp(prefix="codemap-test-"))
        self.addCleanup(shutil.rmtree, self.tmp)

    def tree(self, name="t", files=TREE, newline="\n"):
        root = self.tmp / name
        make_tree(root, files, newline)
        return root

    def test_scope_roles_and_json(self):
        root = self.tree()
        proc = run(["--root", str(root), "declarations", "--json"])
        self.assertEqual(proc.returncode, 0, proc.stderr)
        out = json.loads(proc.stdout)
        self.assertEqual(out["files"], 4)
        got = [(d["path"].rsplit("/", 1)[1], d["name"], d["role"], d["source_set"])
               for d in out["declarations"]]
        self.assertEqual(got, [
            ("Input.kt", "Input", "production", "main"),
            ("Solar.kt", "Alpha", "production", "main"),  # path, then symbol: not position
            ("Solar.kt", "SolarCalculator", "production", "main"),
            ("Solar.kt", "hourToHhmm", "production", "main"),
            ("SolarTest.kt", "SolarTest", "test", "test"),
            ("Thing.kt", "Thing", "production", "main"),
        ])

    def test_untracked_file_is_invisible(self):
        root = self.tree()
        (root / MAIN / "New.kt").write_text("package x\nclass New\n")
        out = json.loads(run(["--root", str(root), "declarations", "--json"]).stdout)
        self.assertNotIn("New", [d["name"] for d in out["declarations"]])

    def test_path_argument_and_text_output(self):
        root = self.tree()
        proc = run(["--root", str(root), "declarations", f"{MAIN}/Solar.kt"])
        self.assertEqual(proc.returncode, 0, proc.stderr)
        lines = proc.stdout.decode().splitlines()
        self.assertEqual(lines[0], f"{MAIN}/Solar.kt:8:5  public fun x.Alpha")
        self.assertEqual(lines[1], f"{MAIN}/Solar.kt:6:8  public object x.SolarCalculator")
        self.assertEqual(lines[2], f"{MAIN}/Solar.kt:7:14  internal fun x.hourToHhmm")
        self.assertIn("lexical, not semantic", lines[-1])

    def test_path_outside_scope(self):
        root = self.tree()
        for arg in ("e2e/Ignored.kt", "app/src/main/kotlin/x/Missing.kt", "/etc/passwd"):
            proc = run(["--root", str(root), "declarations", arg])
            self.assertEqual(proc.returncode, codemap.EXIT_OUT_OF_SCOPE, arg)

    def test_malformed_file_fails_whole_scan(self):
        files = dict(TREE)
        files[f"{MAIN}/Bad.kt"] = "package x\nval s = \"open\n"
        proc = run(["--root", str(self.tree(files=files)), "declarations"])
        self.assertEqual(proc.returncode, codemap.EXIT_MALFORMED)
        self.assertEqual(proc.stdout, b"")
        self.assertIn(f"{MAIN}/Bad.kt:2:9: unterminated string", proc.stderr.decode())

    def test_unreadable_tracked_file(self):
        root = self.tree()
        (root / MAIN / "Input.kt").unlink()
        proc = run(["--root", str(root), "declarations"])
        self.assertEqual(proc.returncode, codemap.EXIT_MALFORMED)
        self.assertIn("unreadable tracked file", proc.stderr.decode())

    def test_git_failure(self):
        plain = self.tmp / "not-a-repo"
        plain.mkdir()
        proc = run(["--root", str(plain), "declarations"])
        self.assertEqual(proc.returncode, codemap.EXIT_MALFORMED)
        self.assertIn("git", proc.stderr.decode())

    def test_usage_error(self):
        self.assertEqual(run(["declarations", "--bogus"]).returncode, codemap.EXIT_USAGE)

    def test_determinism_bytes(self):
        lf = self.tree("lf")
        crlf = self.tree("crlf", newline="\r\n")
        outputs = [
            run(["--root", str(lf), "declarations", "--json"], cwd=self.tmp, seed="1").stdout,
            run(["--root", str(lf), "declarations", "--json"], cwd="/", seed="2").stdout,
            run(["--root", str(crlf), "declarations", "--json"], cwd=lf, seed="3").stdout,
        ]
        self.assertTrue(outputs[0])
        self.assertEqual(outputs[0], outputs[1])
        self.assertEqual(outputs[0], outputs[2])
        self.assertTrue(outputs[0].endswith(b"}\n") and b"\r" not in outputs[0])


REFS = {
    f"{MAIN}/../p/Lib.kt": "package p\n\n// DX-901: synthetic citation; DX-902 too\n"
                           "object Obj { fun member() = 1 }\nfun bar() = 1\nfun String.ext() = 1\n"
                           "class Type { val member = 1 }\nprivate fun hidden() = 1\nfun compute() = 1\n"
                           "class Outer { class Nested; class Middle { class Leaf } }\nfun self() = bar()\n"
                           "annotation class Mark\n",
    f"{MAIN}/../p/Same.kt": "package p\n\nclass Holder { fun compute() = 2 }\n"
                            "fun same(h: Holder) = listOf(Obj.member(), ::bar, Type::member, Type::class,\n"
                            "    \"x\".ext(), h.compute(), \"$bar\")\n"
                            "fun other(hidden: Int) = hidden\n",
    f"{MAIN}/../p/Main2.kt": "package p\n\nfun main2() = TestHelper()\n",
    f"{MAIN}/../q/Imports.kt": "package q\n\nimport p.Obj\nimport p.bar as baz\nimport p.Type\n"
                               "import p.Outer.Nested\nimport p.compute\n\n"
                               "fun use() = listOf(Obj.member(), baz(), Nested(), Type::class)\n",
    f"{MAIN}/../r/Star.kt": "package r\n\nimport p.*\n\nfun s() = bar()\n",
    f"{MAIN}/../s/Qual.kt": "package s\n\nfun t() = p.Obj.member() + p.missing.Thing\n",
    f"{TEST}/../p/TestOnly.kt": "package p\n\nclass TestHelper\nfun t2() = TestHelper()\n",
    f"{TEST}/../p/UsesTestOnly.kt": "package p\n\nfun t3() = TestHelper()\n",
    f"{MAIN}/../p2/Dup.kt": "package p2\n\nclass Dup\n",
    f"{MAIN}/../p3/Dup.kt": "package p3\n\nclass Dup\n",
    f"{MAIN}/../u/Both.kt": "package u\n\nimport p2.*\nimport p3.*\n\nfun both() = Dup()\n",
    f"{MAIN}/../p3/Shadow.kt": "package p3\n\nimport p2.Dup\n\nfun shadow() = Dup()\n",
    f"{MAIN}/../v/External.kt": "package v\n\nimport kotlin.collections.bar\n\nfun external() = bar()\n",
    f"{MAIN}/../q/Deep.kt": "package q\n\nimport p.Outer.Middle.Leaf\n\nfun deep() = Leaf()\n",
    f"{MAIN}/../q/ClassStar.kt": "package q\n\nimport p.Outer.*\n\nfun cs() = Nested()\n",
    f"{MAIN}/../w/Annotated.kt": "@file:p.Mark\npackage w\n\nclass W\n",
    f"{MAIN}/../pp/C.kt": "package pp\n\nclass C\n",
    f"{MAIN}/../qq/Obj.kt": "package qq\n\nobject pp { class C }\n",
    f"{MAIN}/../rr/Use.kt": "package rr\n\nimport qq.pp\n\nfun use() = pp.C()\n",
    f"{MAIN}/../k/K2.kt": "package k\n\nobject K2 { val thing = 1 }\n",
    f"{MAIN}/../k/K2/Other.kt": "package k.K2\n\nclass Other\n",
    f"{MAIN}/../k3/Use.kt": "package k3\n\nfun use() = k.K2.thing\n",
    f"{MAIN}/../y/OwnDup.kt": "package y\n\nimport p2.*\n\nclass Dup\nfun use() = Dup()\n",
    f"{MAIN}/../z/DoubleStar.kt": "package z\n\nimport p2.*\nimport p2.*\n\nfun d() = Dup()\n",
}
REFS = {os.path.normpath(k): v for k, v in REFS.items()}


class References(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.tmp = Path(tempfile.mkdtemp(prefix="codemap-refs-"))
        cls.root = cls.tmp / "t"
        make_tree(cls.root, REFS)

    @classmethod
    def tearDownClass(cls):
        shutil.rmtree(cls.tmp)

    def who(self, symbol, key="references"):
        proc = run(["--root", str(self.root), "who-uses", symbol, "--json"])
        self.assertEqual(proc.returncode, 0, proc.stderr.decode())
        return sorted((r["path"].split("/kotlin/", 1)[1], r["line"], r["kind"], r["ambiguous"])
                      for r in json.loads(proc.stdout)[key])

    def test_same_package_member_reference_extension_and_template(self):
        self.assertEqual(self.who("bar"), [
            ("p/Same.kt", 4, "same-package", False),   # ::bar
            ("p/Same.kt", 5, "same-package", False),   # "$bar"
            ("q/Imports.kt", 9, "import", False),      # through the alias baz
            ("r/Star.kt", 5, "star", False),
        ])
        self.assertEqual(self.who("ext"), [("p/Same.kt", 5, "same-package", False)])  # "x".ext()

    def test_type_member_and_class_literal(self):
        self.assertEqual(self.who("Type"), [
            ("p/Same.kt", 4, "same-package", False), ("p/Same.kt", 4, "same-package", False),
            ("q/Imports.kt", 9, "import", False),
        ])

    def test_qualified_and_object_member(self):
        self.assertEqual(self.who("Obj"), [
            ("p/Same.kt", 4, "same-package", False),
            ("q/Imports.kt", 9, "import", False),
            ("s/Qual.kt", 3, "qualified", False),
        ])

    def test_same_named_member_is_the_documented_false_positive(self):
        # h.compute() calls Holder.compute, and line 3 declares that member; the lexical answer
        # lists both.
        self.assertEqual(self.who("p.compute"), [("p/Same.kt", 3, "same-package", False),
                                                 ("p/Same.kt", 5, "same-package", False)])

    def test_unused_import_is_import_only(self):
        self.assertEqual(self.who("p.compute", "import_only"), [("q/Imports.kt", 7, "import-only", False)])

    def test_private_declaration_is_file_scoped(self):
        # Lib.kt's private hidden() is invisible to Same.kt, whose own hidden() is another
        # declaration of the same symbol: neither file references the other's.
        self.assertEqual(self.who("hidden"), [])

    def test_member_import_of_nested_type(self):
        self.assertEqual(self.who("Outer"), [
            ("q/ClassStar.kt", 3, "member-import", False),  # import p.Outer.*
            ("q/Deep.kt", 5, "member-import", False),       # import p.Outer.Middle.Leaf
            ("q/Imports.kt", 9, "member-import", False),
        ])

    def test_file_annotation(self):
        self.assertEqual(self.who("Mark"), [("w/Annotated.kt", 1, "qualified", False)])

    def test_imported_name_beats_package_qualification(self):
        self.assertEqual(self.who("qq.pp"), [("rr/Use.kt", 5, "import", False)])
        self.assertEqual(self.who("pp.C"), [])

    def test_qualified_tries_shorter_packages(self):
        self.assertEqual(self.who("K2"), [("k3/Use.kt", 3, "qualified", False)])

    def test_own_declaration_hides_star_import(self):
        self.assertNotIn("y/OwnDup.kt", [r[0] for r in self.who("p2.Dup")])

    def test_repeated_star_import_is_not_ambiguous(self):
        self.assertIn(("z/DoubleStar.kt", 6, "star", False), self.who("p2.Dup"))

    def test_impact_keeps_ambiguity(self):
        proc = run(["--root", str(self.root), "impact", "app/src/main/kotlin/p2/Dup.kt", "--json"])
        rows = {u["path"].split("/kotlin/", 1)[1]: u["ambiguous"] for u in json.loads(proc.stdout)["used_by"]}
        self.assertEqual(rows["u/Both.kt"], ["Dup"])
        self.assertEqual(rows["z/DoubleStar.kt"], [])
        text = run(["--root", str(self.root), "impact", "app/src/main/kotlin/p2/Dup.kt"]).stdout.decode()
        self.assertIn("u/Both.kt  Dup (ambiguous)", text)

    def test_test_source_declaration_invisible_from_main(self):
        self.assertEqual(self.who("TestHelper"), [("p/UsesTestOnly.kt", 3, "same-package", False)])

    def test_ambiguous_name_then_qualified(self):
        proc = run(["--root", str(self.root), "who-uses", "Dup"])
        self.assertEqual(proc.returncode, codemap.EXIT_AMBIGUOUS_SYMBOL)
        self.assertIn("p2.Dup", proc.stderr.decode())
        self.assertIn("p3.Dup", proc.stderr.decode())
        self.assertEqual(self.who("p2.Dup"), [
            ("p3/Shadow.kt", 5, "import", False),  # an explicit import beats the own package
            ("u/Both.kt", 6, "star", True),
            ("z/DoubleStar.kt", 6, "star", False),
        ])
        self.assertEqual(self.who("p3.Dup"), [("u/Both.kt", 6, "star", True)])

    def test_external_import_shadows_lower_levels(self):
        self.assertNotIn("v/External.kt", [r[0] for r in self.who("bar")])

    def test_own_file_references_ignored(self):
        self.assertNotIn("p/Lib.kt", [r[0] for r in self.who("bar")])

    def test_empty_answer_wording(self):
        proc = run(["--root", str(self.root), "who-uses", "hidden"])
        self.assertEqual(proc.returncode, 0)
        self.assertIn(codemap.EMPTY, proc.stdout.decode())
        self.assertIn("not semantic resolution", proc.stdout.decode())

    def test_impact(self):
        proc = run(["--root", str(self.root), "impact", "app/src/main/kotlin/p/Lib.kt", "--json"])
        self.assertEqual(proc.returncode, 0, proc.stderr.decode())
        out = json.loads(proc.stdout)
        self.assertEqual([d["name"] for d in out["declares"]],
                         ["Mark", "Obj", "Outer", "Type", "bar", "compute", "ext", "hidden", "self"])
        used = {u["path"].split("/kotlin/", 1)[1]: u["symbols"] for u in out["used_by"]}
        self.assertEqual(used, {
            "p/Same.kt": ["Obj", "Type", "bar", "compute", "ext"],
            "q/ClassStar.kt": ["Outer"], "q/Deep.kt": ["Outer"],
            "q/Imports.kt": ["Obj", "Outer", "Type", "bar"],
            "r/Star.kt": ["bar"], "s/Qual.kt": ["Obj"], "w/Annotated.kt": ["Mark"],
        })
        self.assertEqual([u["path"].split("/kotlin/", 1)[1] for u in out["import_only"]], ["q/Imports.kt"])
        self.assertEqual(out["cites"], {"DX-901": [3], "DX-902": [3]})

    def test_exit_statuses(self):
        cases = [
            (["who-uses", "NoSuch"], codemap.EXIT_UNKNOWN_SYMBOL),
            (["who-uses", "p.NoSuch"], codemap.EXIT_UNKNOWN_SYMBOL),
            (["who-uses", "Dup"], codemap.EXIT_AMBIGUOUS_SYMBOL),
            (["impact", "e2e/Nope.kt"], codemap.EXIT_OUT_OF_SCOPE),
            (["impact"], codemap.EXIT_USAGE),
            (["who-uses", "bar"], codemap.EXIT_OK),
        ]
        for args, code in cases:
            self.assertEqual(run(["--root", str(self.root), *args]).returncode, code, args)

    def test_package_spanning_modules_fails(self):
        root = self.tmp / "span"
        make_tree(root, {"app/src/main/kotlin/p/A.kt": "package p\nclass A\n",
                         "domain/src/main/kotlin/p/B.kt": "package p\nclass B\n"})
        proc = run(["--root", str(root), "who-uses", "A"])
        self.assertEqual(proc.returncode, codemap.EXIT_MALFORMED)
        self.assertIn("spans modules app, domain", proc.stderr.decode())

    def test_determinism_bytes(self):
        crlf = self.tmp / "crlf"
        if not crlf.exists():
            make_tree(crlf, REFS, "\r\n")
        for args in (["who-uses", "bar", "--json"], ["impact", "app/src/main/kotlin/p/Lib.kt", "--json"]):
            outs = [
                run(["--root", str(self.root), *args], cwd=self.tmp, seed="1").stdout,
                run(["--root", str(self.root), *args], cwd="/", seed="2").stdout,
                run(["--root", str(crlf), *args], cwd=self.root, seed="3").stdout,
            ]
            self.assertTrue(outs[0])
            self.assertEqual(outs[0], outs[1], args)
            self.assertEqual(outs[0], outs[2], args)


class RealTree(unittest.TestCase):
    def test_every_tracked_file_scans(self):
        proc = run(["declarations", "--json"], cwd="/")
        self.assertEqual(proc.returncode, 0, proc.stderr.decode())
        out = json.loads(proc.stdout)
        tracked = subprocess.run(["git", "-C", str(REPO), "ls-files", "*.kt"], check=True,
                                 capture_output=True, text=True).stdout.split()
        in_scope = [p for p in tracked if codemap.SCOPE_RE.match(p)]
        self.assertEqual(out["files"], len(in_scope))

        rel = "domain/src/main/kotlin/com/tideo/autobrightness/domain/circadian/DynamicScaleEngine.kt"
        here = {d["name"] for d in out["declarations"] if d["path"] == rel}
        self.assertIn("DynamicScaleInput", here)
        header = re.search(r"data class DynamicScaleInput\((.*?)\n\)", (REPO / rel).read_text(), re.S)
        props = set(re.findall(r"\b(?:val|var) (\w+)", header.group(1)))
        self.assertTrue(props)
        self.assertFalse(props & here, props & here)

    # Derived at Unit 3's base by `grep -rlw` plus a reading, before the resolver ran; each list
    # holds the matches, and the comments name the non-matches it must leave out.
    CORPUS = {
        "SolarCalculator": {  # imports, a qualified call, same package (a test)
            "CircadianWindowProvider.kt", "ContextSolarTimes.kt", "CircadianChart.kt",
            "CircadianWindowProviderTest.kt", "SolarInvariantTest.kt", "CircadianParityTest.kt",
            "TaskerReference.kt"},
        "ContextSolarTimes": {  # AndroidContextSignalSource shares the package, no import
            "AndroidContextSignalSource.kt", "ContextsViewModel.kt", "ContextSolarTimesTest.kt"},
        "hourToHhmm": {"ExperimentChart.kt"},  # internal, `::hourToHhmm`; CircadianChart is its own
        "toThresholdConfig": {"PipelineCycleRunner.kt", "ReactivityScreen.kt"},  # `x.toThresholdConfig()`
        "DisplayCalibration": {  # `import …DisplayCalibration.Point` included
            "DisplayCalibrationRun.kt", "HighBrightnessUpdates.kt", "DisplayCalibrationStore.kt",
            "HandoffPipelineTest.kt", "HighBrightnessUpdatesTest.kt", "DisplayCalibrationTest.kt"},
        # AabSettingsMapper sits in app.settings but imports domain's: not a user of app's.
        "com.tideo.autobrightness.app.settings.ThresholdConfig": {"NestedSchemaRoundTripTest.kt"},
        "com.tideo.autobrightness.domain.brightness.ThresholdConfig": {
            "AabSettingsMapper.kt", "ReactivityChart.kt", "BrightnessEngine.kt", "GraphsA11yTest.kt",
            "SettlingPlacementTest.kt", "CorePipelineParityTest.kt", "DifferentialSweepParityTest.kt",
            "LightCycleParityTest.kt"},
    }

    def test_corpus(self):
        index = codemap.Index(codemap.build_index(REPO))
        for symbol, expected in self.CORPUS.items():
            sym = index.symbol_arg(symbol)
            refs = [r for r in index.references() if r.symbol == sym]
            got = {r.path.rsplit("/", 1)[1] for r in refs if r.kind != "import-only"}
            self.assertEqual(got, expected, symbol)
            self.assertEqual([r for r in refs if r.kind == "import-only"], [], symbol)


if __name__ == "__main__":
    unittest.main()
