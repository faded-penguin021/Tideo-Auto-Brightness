#!/usr/bin/env bash
# AMH — output redaction filter (P17).
#
# stdin -> stdout. Replaces KNOWN credential shapes with [REDACTED:<class>]. Most are
# anchored on a token PREFIX (AKIA…, ghp_…, sk-proj-…); two are anchored on CONTEXT
# instead — the WHOLE of an Authorization: Bearer header, and a URL's `user:password@`
# userinfo — because such a credential need not carry a prefix of its own.
# Deliberately NOT generic entropy matching: entropy heuristics mangle ordinary build
# output, and output an agent cannot read gets the filter disabled rather than fixed.
#
# This is one of three layers. The prose rule ("never print a credential's value,
# prefix, suffix, length or hash") and the permission deny rails are the other two.
# The regex layer catches KNOWN shapes only — it narrows the window, it never
# replaces the rule.
#
# Usage:
#   cmd 2>&1 | redact.sh          filter
#   redact.sh --classes           list the token classes recognised
#   redact.sh --self-test         fixture matrix (tokens are generated at runtime,
#                                 never stored — a stored literal would itself be a
#                                 secret-shaped string in the tree)
#
# Shipped by the Agentic Maintenance Harness. Repo-agnostic: do not edit locally.
#
# On `AMH ledger row DNNN` references below: they point at the HARNESS's ledger, which
# explains why this script is shaped the way it is. They are deliberately NOT written as
# `D-NNN` citations, because a citation is a promise that the ID resolves — and in your
# repository it never can, since those rows are ours and cannot appear in your ledger.
# Written as citations they made the ladder's citation guard fail on a repo its owner had
# not yet touched, for rows they could not have written.

set -euo pipefail

# class<TAB>extended-regex. Order matters: more specific prefixes first, because the
# substitutions are applied in sequence (sk-ant- before the generic sk- shape).
#
# Every length is OPEN-ENDED ({n,}, never {n}). An exact count stops matching at the
# n-th character and prints the rest: an over-long token came out as
# `[REDACTED:<class>]` followed by its own tail, which is exactly the suffix the prose
# rule forbids. A vendor lengthening a token must not silently downgrade this filter
# from redaction to truncation.
PATTERNS=$(
	cat <<-'PATS'
		aws_access_key_id	AKIA[0-9A-Z]{16,}
		aws_temp_key	ASIA[0-9A-Z]{16,}
		github_pat	github_pat_[A-Za-z0-9_]{20,}
		github_token	gh[pousr]_[A-Za-z0-9]{20,}
		gitlab_pat	glpat-[A-Za-z0-9_-]{20,}
		slack_token	xox[abprs]-[A-Za-z0-9-]{10,}
		slack_webhook	https://([^]/?#@[:space:]"'`,;<>{}()]*@)?hooks\.slack\.com/services/[A-Za-z0-9/_-]{20,}
		anthropic_key	sk-ant-[A-Za-z0-9_-]{20,}
		openai_project_key	sk-proj-[A-Za-z0-9_-]{20,}
		openai_service_key	sk-svcacct-[A-Za-z0-9_-]{20,}
		openai_admin_key	sk-admin-[A-Za-z0-9_-]{20,}
		openai_key	sk-[A-Za-z0-9]{32,}
		google_api_key	AIza[0-9A-Za-z_-]{35,}
		npm_token	npm_[A-Za-z0-9]{36,}
		pypi_token	pypi-[A-Za-z0-9_-]{16,}
		huggingface_token	hf_[A-Za-z0-9]{30,}
		stripe_key	[sr]k_live_[A-Za-z0-9]{16,}
		jwt	eyJ[A-Za-z0-9_-]{8,}\.[A-Za-z0-9_-]{8,}\.[A-Za-z0-9_-]{8,}
		bearer_header	[Aa][Uu][Tt][Hh][Oo][Rr][Ii][Zz][Aa][Tt][Ii][Oo][Nn]:[[:space:]]*[Bb][Ee][Aa][Rr][Ee][Rr][[:space:]]+[A-Za-z0-9._~+/=-]*[A-Z0-9._~+/=-][A-Za-z0-9._~+/=-]*[A-Z0-9._~+/=-][A-Za-z0-9._~+/=-]{14,}
		url_credentials	[A-Za-z][A-Za-z0-9+.-]*://[^]:/?#@[:space:]"'`,;<>{}()]+:[^]/?#@[:space:]"'`,;<>{}()]+@
		url_token_userinfo	[A-Za-z][A-Za-z0-9+.-]*://[A-Za-z0-9._~+%-]{20,}@
		private_key_block	-----BEGIN [A-Z ]*PRIVATE KEY-----
	PATS
)

# No regex here may contain `|` or `&`: the generated substitution is `s|regex|repl|g`,
# so a `|` would terminate the command and a `&` in a replacement would re-insert the
# match. That is why the OpenAI families are four rows rather than one alternation.
#
# Notes on the two shapes that are not a bare token — both are far more able to eat
# ordinary build output than a prefixed token is, and a filter that mangles ordinary
# output gets switched off by its users, which is worse than the leak it prevents:
#
# `bearer_header` swallows the header name along with the value. Keeping the name and
# redacting only the value would need a backreference, which the uniform builder cannot
# express — and a special case inside the repo's entire secret scan costs more than a
# preserved word. The plain `{8,}` form ate any long word after the header:
# "Authorization: Bearer authentication" redacted, in a repository whose prose is *about*
# credential handling.
#
# The discriminator is NOT length. There is no length that separates a token from a word —
# English runs past 24 letters (`antidisestablishmentarianism`, and far past that in
# technical text), and a repository holding protein sequences or long identifiers has no
# ceiling at all. What actually holds is that a word appearing after `Bearer` in prose is
# almost all lowercase letters, and an opaque credential is not: the value must contain at
# least TWO characters that are not lowercase letters, with 14 more after the second.
#
# TWO, not one, and the difference is the whole rule. A word at the start of a sentence is
# capitalised, so "one non-lowercase character" lets any long capitalised word through the
# door — `Antidisestablishmentarianism` clears a length test and clears a single-capital
# test. It does not clear this one: an English word carries exactly one capital, wherever
# it sits, while a base64url token carries a dozen. The ANCHOR does the rest — this only
# fires immediately after the header name, where prose puts "authentication", "tokens" and
# "credentials" and nothing else.
# Accepted residue: an ALL-CAPS or CamelCase identifier written directly after
# `Authorization: Bearer` is redacted. That shape is a token, whoever wrote it.
#
# The fixture builds its token as uppercase/digits followed by alphanumerics, so it
# satisfies that predicate BY CONSTRUCTION. It has to. The first fix required a digit and
# drew the fixture from a plain alphanumeric generator, so about one run in 140 produced a
# token with no digit early enough to match; the ladder runs this self-test once per
# fixture repo, which compounded to roughly a one-in-five chance of a red CI run per push,
# at random, on the repo's ENTIRE secret scan. **A predicate a fixture satisfies only
# USUALLY is a flake, however sound the predicate looks** — and a flake in a guard is worse
# than a missing test, because it teaches everyone that red means "run it again".
#
# `url_credentials` matches the scheme, the userinfo and the `@`, so the HOST and path
# survive: `[REDACTED:url_credentials]host/path`. (This said "the scheme and host survive"
# until a review read the regex instead of the comment — the match starts at the scheme,
# so the scheme goes.) That keeps the diagnostic useful — a failed clone still names the
# host — while the credential is gone. This shape is not
# hypothetical — a token-bearing remote appears in this repo's own `git remote -v`.
# Both userinfo components exclude quoting and structural punctuation. Excluding only
# `/ ? # @` and whitespace was a blocker: in one-line JSON or logfmt, `scheme://host:port`
# plus any later `@` on the same line matched across the gap and deleted the host, the
# port and the surrounding structure —
# `{"url":"http://svc:8080","user":"a@b.com"}` came out as
# `{"url":"[REDACTED:url_credentials]b.com"}`.
#
# `url_token_userinfo` is the SAME shape with no colon in it: a URL whose userinfo is a
# bare token rather than a `user:password` pair. That is the documented Azure DevOps
# personal-access-token clone URL, and it is a real credential inside a command people
# paste into terminals and issue trackers. `url_credentials` cannot see it — its first
# component is followed by a mandatory `:` — so the whole shape was passing through in the
# clear.
#
# The discriminator is LENGTH OF USERINFO, and here that is legitimate, because the two
# populations really are separated by it. Colon-less userinfo in ordinary output is a
# short human or service name: `ssh://git@host` is three characters, `svc@`, `build@`,
# `ci@` are all under ten. An opaque token is not: an Azure DevOps PAT is 52 characters
# and every credential of this shape is a generated string. Twenty is the floor because it
# sits in the empty gap — well past any account name anyone writes by hand, well under any
# generated token — and unlike the bearer-header case there is no word-versus-token
# question to answer, because the ANCHOR is not a header name but `scheme://` immediately
# followed by the candidate and terminated by `@`.
#
# Note the userinfo class here is POSITIVE — the RFC 3986 unreserved set plus `%` — while
# `url_credentials` above uses a negated one. That difference is the whole safety margin,
# and it was earned: a negated class silently admits every character nobody thought to
# exclude, and the list of things that turned out to sit between a scheme and a later `@`
# on one line kept growing. `=` (logfmt, query strings) was excluded by hand, and then a
# review found `|` — an unpadded markdown table row, `|api|https://host|ops@corp.example|`,
# came out as `|api|[REDACTED:url_token_userinfo]corp.example|`, with the endpoint and the
# owner's name deleted and no credential anywhere on the line. That is the AMH ledger row
# D022 failure one delimiter further out, and `|` is the one character that CANNOT be
# added to a negated class here, because it is the `s|re|rep|g` delimiter. `\ ^ [ ! $ & *` were all
# admitted by the same hole. Naming what userinfo may contain ends the whole family: a
# real token is unreserved characters (Azure DevOps PATs are base32, Sentry keys hex,
# GitHub tokens alphanumerics and `_`), and anything else is a line, not a credential.
# Note too what does most of the work regardless: `/` is not in the class, so any URL that
# reaches a path — nearly every URL in ordinary output — cannot match at all.
#
# Accepted residue, recorded rather than silently carried, and both halves fixture-covered
# below so neither can drift unnoticed:
#   · A userinfo of twenty or more unreserved characters that is genuinely somebody's
#     account name — a very long IRC nickname, say — is redacted. Same trade as
#     `bearer_header` and the same answer: at that length the shape is a token, whoever
#     wrote it, and the cost is a host that still prints while one name does not.
#   · Base64 userinfo carrying its `=` padding is MISSED — `=` is outside the class, and
#     `url_credentials` cannot see it either for want of a colon. Percent-encoded padding
#     (`%3D`) is caught. Admitting `=` is what the markdown-table finding argues against,
#     so the miss is the cheaper side of that trade.
# If either ever costs something real, move the floor or the class here — never delete the
# class.
#
# `slack_webhook` tolerates optional userinfo so that it still fires on a webhook URL
# carrying credentials. Substitutions run once, in list order: without this,
# `url_credentials` rewrote the prefix and left the webhook token itself in the clear.
#
# Knowingly accepted false-positive surface, recorded rather than silently carried:
# `ASIA` is an English word, so `ASIA` + 16 uppercase/digit characters redacts (e.g. a
# run-together region identifier). AWS STS keys are worth the class, and the exposure is
# the same shape `AKIA` has carried since the beginning — but if this ever fires on real
# output, tighten it here rather than deleting the class.

build_sed_script() {
	local class regex
	while IFS=$'\t' read -r class regex; do
		[ -n "$class" ] || continue
		printf 's|%s|[REDACTED:%s]|g\n' "$regex" "$class"
	done <<<"$PATTERNS"
}

filter() { sed -E -f <(build_sed_script); }

list_classes() {
	local class regex
	while IFS=$'\t' read -r class regex; do
		[ -n "$class" ] || continue
		printf '%s\n' "$class"
	done <<<"$PATTERNS"
}

# --- self-test --------------------------------------------------------------

# Bounded read, then slice. The obvious form — `tr -dc … </dev/urandom | head -c N` —
# leaves tr writing into a pipe head has already closed, so every self-test run printed
# `tr: write error: Broken pipe`. The token was still correct; the noise was not, and
# noise in a guard's output is how a real diagnostic gets skimmed past.
rand_class() { # <class> <length>
	local class=$1 n=$2 pool=''
	while [ "${#pool}" -lt "$n" ]; do
		pool=$pool$(head -c 512 /dev/urandom | LC_ALL=C tr -dc "$class")
	done
	printf '%s' "${pool:0:n}"
}
rand_alnum() { rand_class 'A-Za-z0-9' "$1"; }
rand_upper() { rand_class 'A-Z0-9' "$1"; }

# shellcheck disable=SC2094 # "$1" is opened twice for READING only — the filter's stdin
# and cmp's operand. SC2094 warns about a read/write pair; nothing here writes it, and
# comparing a file against its own filtered stream is the whole point of the check.
clean_under_filter() { filter <"$1" | cmp -s - "$1"; }

ST_FAILS=0

# <class> <token> [expected-replacement] — the filtered line must equal the expected
# line EXACTLY.
#
# The old assertion was "the whole token is absent", which a PARTIAL redaction
# satisfies: an exact-length class that matched the first 35 characters of a 40-
# character token emitted `[REDACTED:google_api_key]` followed by five live characters
# of the key, and this test called that a pass. A structural blind spot in the check
# that guards the repo's entire secret scan is worse than the leak it missed, because
# it guarantees no fixture can ever find it. Exact equality is the only assertion that
# sees a surviving fragment, so the caller must state what the line becomes.
st_redacted() {
	local class=$1 token=$2 out want expect
	# Split off its own line: inside a single `local`, bash declares every name before
	# it assigns, so a default referring to `$class` reads it while still unset and
	# `set -u` aborts the run.
	expect=${3:-"[REDACTED:$class]"}
	out=$(printf 'log line: %s trailing\n' "$token" | filter)
	want="log line: $expect trailing"
	[ "$out" = "$want" ] && return 0
	if [[ "$out" != *"[REDACTED:$class]"* ]]; then
		printf 'SELF-TEST FAIL: %s was not redacted\n' "$class" >&2
	elif [[ "$out" == *"$token"* ]]; then
		printf 'SELF-TEST FAIL: %s survived redaction\n' "$class" >&2
	else
		# Deliberately does NOT print the surviving text: a diagnostic that echoes the
		# fragment leaks exactly the suffix this filter exists to suppress.
		printf 'SELF-TEST FAIL: %s was redacted only in part — the line does not match the expected shape, so a fragment of the token survives\n' "$class" >&2
	fi
	ST_FAILS=$((ST_FAILS + 1))
}

st_untouched() { # <label> <text> — ordinary output must pass through byte-identical
	local label=$1 text=$2 out
	out=$(printf '%s\n' "$text" | filter)
	if [ "$out" != "$text" ]; then
		printf 'SELF-TEST FAIL: %s was mangled by the filter\n' "$label" >&2
		ST_FAILS=$((ST_FAILS + 1))
	fi
}

self_test() {
	# Positive cases. Every token is generated here and never written to disk.
	st_redacted aws_access_key_id "AKIA$(rand_upper 16)"
	st_redacted github_pat "github_pat_$(rand_alnum 30)"
	st_redacted github_token "ghp_$(rand_alnum 36)"
	st_redacted slack_token "xoxb-$(rand_alnum 24)"
	st_redacted slack_webhook "https://hooks.slack.com/services/$(rand_alnum 30)"
	st_redacted anthropic_key "sk-ant-$(rand_alnum 40)"
	st_redacted openai_key "sk-$(rand_alnum 40)"
	st_redacted google_api_key "AIza$(rand_alnum 35)"
	st_redacted npm_token "npm_$(rand_alnum 36)"
	st_redacted pypi_token "pypi-$(rand_alnum 30)"
	st_redacted stripe_key "sk_live_$(rand_alnum 24)"
	st_redacted jwt "eyJ$(rand_alnum 20).$(rand_alnum 20).$(rand_alnum 20)"
	# Assembled at runtime: a stored literal would make this file match its own filter.
	st_redacted private_key_block "$(printf -- '-----%s RSA PRIVATE KEY-----' BEGIN)"

	# Shapes that were live and unmatched. `sk-proj-` is the one that matters most: it is
	# OpenAI's current format, and the class named after that vendor stopped matching it
	# when the `-` arrived, so the filter reported a clean line on a real key.
	st_redacted openai_project_key "sk-proj-$(rand_alnum 48)"
	st_redacted openai_service_key "sk-svcacct-$(rand_alnum 48)"
	st_redacted openai_admin_key "sk-admin-$(rand_alnum 48)"
	st_redacted aws_temp_key "ASIA$(rand_upper 16)"
	st_redacted gitlab_pat "glpat-$(rand_alnum 20)"
	st_redacted huggingface_token "hf_$(rand_alnum 34)"
	# Both are assembled in pieces for the same reason as the private-key block above:
	# written out whole, the fixture line would itself match the pattern it tests and
	# this file would stop being clean under its own filter.
	# Uppercase/digits first, then alphanumerics: the pattern requires two non-lowercase
	# characters with 14 more after, and this token has eight up front on every run.
	# A `rand_alnum` token satisfies that predicate only usually, which is what made this
	# fixture flaky.
	st_redacted bearer_header "$(printf '%s: %s %s' Authorization Bearer "$(rand_upper 8)$(rand_alnum 32)")"
	local url_prefix
	url_prefix="postgres://amh:$(rand_alnum 16)"
	st_redacted url_credentials "$url_prefix@db.internal.invalid/app" \
		'[REDACTED:url_credentials]db.internal.invalid/app'
	# Colon-less userinfo: the documented Azure DevOps PAT clone URL. 52 characters, so it
	# clears the 20-character floor BY CONSTRUCTION rather than on most draws.
	local pat_prefix
	pat_prefix="https://$(rand_alnum 52)"
	st_redacted url_token_userinfo "$pat_prefix@dev.azure.invalid/org/_git/repo" \
		'[REDACTED:url_token_userinfo]dev.azure.invalid/org/_git/repo'
	# The accepted residue, pinned as a fixture rather than asserted in a comment: a long
	# HUMAN name in colon-less userinfo redacts too. This is deliberate, and writing it
	# down as an assertion is the difference between a trade-off and a surprise — move the
	# floor and this fixture tells you what you have changed. Assembled at runtime like
	# every other token here: written out whole, the line would match its own class and
	# this file would stop being clean under its own filter.
	local nick_url
	nick_url="irc://$(rand_class 'a-z' 24)"
	st_redacted url_token_userinfo "$nick_url@irc.invalid/room" \
		'[REDACTED:url_token_userinfo]irc.invalid/room'

	# Over-long tokens. These are the B6 regression fixtures: with an exact-length class
	# each one used to emit the marker followed by its own tail, and the old "the whole
	# token is absent" assertion passed them. They fail loudly now if a `{n,}` is ever
	# tightened back to `{n}`.
	st_redacted aws_access_key_id "AKIA$(rand_upper 24)"
	st_redacted google_api_key "AIza$(rand_alnum 44)"
	st_redacted npm_token "npm_$(rand_alnum 44)"

	# A webhook URL that also carries userinfo. Substitutions run once and in order, so
	# whichever class fires first decides: with `url_credentials` winning, the prefix was
	# rewritten and the webhook token itself was left in the clear.
	local hook_prefix
	hook_prefix="https://amh:$(rand_alnum 16)"
	st_redacted slack_webhook "$hook_prefix@hooks.slack.com/services/$(rand_alnum 30)"

	# Negative cases: shapes that occur constantly in real build output and MUST
	# survive untouched. A filter that eats these gets turned off.
	st_untouched git_sha "commit 8f14e45fceea167a5a36dedd4bea2543dfd9e1b2 ok"
	st_untouched uuid "id=123e4567-e89b-12d3-a456-426614174000"
	st_untouched base64_blob "hash=YWJjZGVmZ2hpamtsbW5vcHFyc3R1dnd4eXo="
	st_untouched semver "resolved react@18.3.1 in 412ms"
	st_untouched path "writing /var/lib/build/AKIA-report.txt"
	st_untouched env_presence "DATABASE_URL is set"
	# The two non-token shapes are the ones most able to eat ordinary output, so they
	# carry the negative cases: a URL is only a credential when it carries userinfo, and
	# a header is only a credential when it carries a token.
	#
	# NOTE ON PLACEMENT, and it is the whole point: in every one of these the candidate
	# sits MID-LINE, with more text after it. A negative fixture whose benign text runs
	# to end-of-line passes by construction — the match had nothing left to run into —
	# and this repo has already shipped a whole fixture family that was worthless for
	# exactly that reason. The `,` and `"` cases below are the ones that were live
	# blockers: they eat a compact JSON or logfmt line whole.
	st_untouched plain_url "cloning https://github.com/faded-penguin021/AMH.git by ci@runner"
	st_untouched ssh_url "remote origin git@github.com:faded-penguin021/AMH.git (fetch)"
	st_untouched port_url "listening on http://localhost:8080/healthz owner=me@corp.example"
	st_untouched json_log "{\"url\":\"http://svc:8080\",\"user\":\"alice@example.com\"}"
	st_untouched logfmt "level=info url=http://db:5432,owner=me@x.com done"
	st_untouched semicolon_log "warning: http://cache:6379;contact=sre@team.io retry"
	st_untouched header_no_value "Authorization: Bearer" # nothing to redact
	st_untouched bearer_prose "Use Authorization: Bearer authentication for this endpoint"
	# The long-word cases, which are why the threshold is not a length: an all-lowercase
	# run stays untouched however long it gets, in prose or in a sequence dump.
	st_untouched bearer_long_word "Authorization: Bearer antidisestablishmentarianism is not a token"
	# Capitalised, because a word starting a sentence is — one capital is not a token.
	st_untouched bearer_capitalised "Authorization: Bearer Antidisestablishmentarianism is a word"
	st_untouched bearer_sequence "seq Authorization: Bearer acdefghiklmnpqrstvwyacdefghiklmnpqrstvwy end"
	st_untouched sk_kebab "cache key sk-build-linux-x86-64-node20-pnpm9-abc123-def456 hit"
	# The colon-less userinfo class, whose whole risk is what it eats. Every candidate
	# below sits MID-LINE with text after it, for the reason stated above the negative
	# block: a benign case that runs to end-of-line passes because the match had nothing
	# left to run into, and this repo has already shipped a whole family of fixtures that
	# were worthless for exactly that.
	#
	# Short userinfo is the `git@host` end of the population the length floor separates.
	st_untouched ssh_scheme_url "using ssh://git@github.com/faded-penguin021/AMH.git for now"
	st_untouched short_userinfo "fetching https://svc-account@dev.azure.invalid/org/x now"
	# Long, but a `/` arrives before any `@` — which is why nearly every real URL in
	# ordinary output cannot reach this class at all.
	st_untouched long_host_then_at "GET https://build.artifacts.internal.invalid/a/very/long/path by me@corp.example ok"
	# A query string: the one shape where a long run of URL-legal characters sits between
	# a scheme and a later `@`. Here `?` is what stops it, ten characters in.
	st_untouched query_then_at "open https://ci.invalid?job=build-linux-x86-64-node20-pnpm9&who=me@corp.example done"
	# ...and the same shape with NO `?`, `/`, space or quote before the `@`, so the only
	# thing that can stop the match is a character outside the userinfo class. Without it,
	# widening the class went unnoticed: the case above is blocked by its `?` and proves
	# nothing about the rest. This is the fixture that fails when the class grows.
	st_untouched logfmt_eq_at "level=info url=https://ci.invalid-build-node&owner=me@x.com msg=done"
	# An unpadded markdown table row. `|` cannot be excluded by a negated class — it is the
	# generated substitution's own delimiter — so this line is the argument for naming what
	# userinfo may contain instead of what it may not.
	st_untouched md_table_row "|api|https://api-gateway-internal-prod|ops@corp.example|"
	# Compact JSON and logfmt, the two lines that were live blockers for url_credentials.
	st_untouched json_scheme_at "{\"url\":\"https://averyveryverylongidentifier\",\"user\":\"alice@example.com\"}"
	st_untouched logfmt_scheme_at "level=info url=https://averyveryverylongidentifier owner=me@x.com done"

	# The ASSERTION is under test too, not only the classes. Every fixture above proves
	# something about the patterns; none of them proves the check can still SEE a partial
	# redaction, and that check is the entire fix for the second half of B6. Reverting
	# `st_redacted` to the old "the whole token is absent" form leaves every fixture
	# above green while a token's tail prints again — the check is not covered by the
	# things it checks. So: hand it a line that redacts only in part (a trailing
	# character outside the class, which the match cannot consume) and require it to
	# report a failure. The probe's own failure is then unwound, because a probe that
	# behaved correctly must not be counted as a fault.
	#
	# The probe is called directly, NOT in `$(...)`: `st_redacted` records its verdict by
	# incrementing ST_FAILS, and a command substitution runs it in a subshell where that
	# increment is discarded — the probe then reads as "detected nothing" no matter how
	# the assertion behaves. Its diagnostic goes to /dev/null because the probe is
	# expected to fail and its message would read as a real fault.
	local probe_before
	probe_before=$ST_FAILS
	st_redacted aws_access_key_id "AKIA$(rand_upper 16)z" 2>/dev/null
	if [ "$ST_FAILS" -eq "$probe_before" ]; then
		printf 'SELF-TEST FAIL: st_redacted no longer detects a surviving fragment — the B6 leak could return with this suite still green\n' >&2
		ST_FAILS=$((probe_before + 1))
	else
		ST_FAILS=$probe_before
	fi

	# The filter must be clean under itself: its own patterns must not look like
	# tokens, or the ladder's tree scan would flag this very file forever.
	#
	# The comparison lives in its own function so the SC2094 waiver covers ONE pipeline
	# and nothing else. Inline, the directive had to sit on the enclosing `if`, which
	# silenced SC2094 for the whole body — and SC2094 is the check for clobbering a
	# file. Blinding it inside the repo's entire secret scan (AMH ledger row D004) is the
	# exact shape of hole this guard exists to prevent.
	if [ -f "${BASH_SOURCE[0]}" ]; then
		if ! clean_under_filter "${BASH_SOURCE[0]}"; then
			printf 'SELF-TEST FAIL: redact.sh is not clean under its own filter\n' >&2
			ST_FAILS=$((ST_FAILS + 1))
		fi
	fi

	if [ "$ST_FAILS" -ne 0 ]; then
		printf 'redact.sh self-test: %d failure(s)\n' "$ST_FAILS" >&2
		return 1
	fi
	printf 'redact.sh self-test: ok\n'
}

case "${1:-}" in
"") filter ;;
--classes) list_classes ;;
--self-test) self_test ;;
*)
	printf 'usage: %s [--classes|--self-test]\n' "$0" >&2
	exit 2
	;;
esac
