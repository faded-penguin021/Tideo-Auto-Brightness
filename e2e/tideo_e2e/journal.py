"""The recovery journal: identity-bound, single-run locked, write-ahead and conflict-aware.

Every mutation the run makes, directly or through Tideo, has an entry holding the value it found
(`original`) and the values the run may have left there: the last one observed (`attributable`)
and the one it is about to write until an observation sees it land (`expected`). Each entry is
fsync'd BEFORE the mutation it covers, so an interruption at any point leaves the device in
`original` or in a value the run caused. Recovery restores a key only from such a value; anything
else is a conflict — someone else changed it — and is kept for the owner, never overwritten.

The journal lives in the private store, outside the repo. A journal that exists blocks every run
until `--recover` empties it or the owner resolves its conflicts.
"""

from __future__ import annotations

import fcntl
import hashlib
import hmac
import json
import os
import secrets
from dataclasses import asdict, dataclass
from pathlib import Path

VERSION = 1

SETTING, GRANT, PREF = "setting", "grant", "pref"
# The mode Tideo gives back on stop (Runtime.owner_mode): only ever a conflict, never written.
OWNER_MODE = "owner_mode"
KINDS = (SETTING, GRANT, PREF, OWNER_MODE)
PENDING, CONFLICT = "pending", "conflict"


class JournalError(RuntimeError):
    pass


class JournalLocked(JournalError):
    """Another run or recovery holds the lock."""


class PendingJournal(JournalError):
    """A journal from an earlier run is not empty: recover or resolve first."""


class IdentityMismatch(JournalError):
    """The connected device, user or package is not the one the journal is bound to."""


class UnrestorableOriginal(JournalError):
    """The original value cannot be written back (an absent settings row)."""


def private_store() -> Path:
    base = os.environ.get("XDG_STATE_HOME") or os.path.join(os.path.expanduser("~"), ".local",
                                                            "state")
    return Path(base) / "tideo-e2e"


def _ensure_store(store: Path) -> None:
    store.mkdir(mode=0o700, parents=True, exist_ok=True)
    os.chmod(store, 0o700)


def serial_digest(store: Path, serial: str) -> str:
    """A salted digest of the serial; the salt never leaves the private store."""
    _ensure_store(store)
    salt_file = store / "salt"
    if not salt_file.exists():
        # Published whole or not at all: a durable temp file, then link(), which never replaces.
        tmp = store / f"salt.{os.getpid()}.{secrets.token_hex(4)}"
        fd = os.open(tmp, os.O_WRONLY | os.O_CREAT | os.O_EXCL, 0o600)
        with os.fdopen(fd, "wb") as f:
            f.write(secrets.token_bytes(SALT_BYTES))
            f.flush()
            os.fsync(f.fileno())
        try:
            os.link(tmp, salt_file)
        except FileExistsError:
            pass
        finally:
            tmp.unlink()
        fsync_dir(store)
    salt = salt_file.read_bytes()
    if len(salt) != SALT_BYTES:
        raise JournalError(f"{salt_file} is damaged; the owner must inspect it")
    return hmac.new(salt, serial.encode(), hashlib.sha256).hexdigest()


SALT_BYTES = 32


def fsync_dir(path: Path) -> None:
    dfd = os.open(path, os.O_RDONLY)
    try:
        os.fsync(dfd)
    finally:
        os.close(dfd)


@dataclass(frozen=True)
class Identity:
    serial_digest: str
    user: int
    first_install_time: str
    version_code: int
    cert_digest: str


@dataclass
class Entry:
    kind: str
    key: str              # "system/screen_brightness", the permission, or a pref name
    original: str
    attributable: list[str]
    state: str = PENDING
    seen: str | None = None  # the unattributable value that made it a conflict
    # The value the harness is about to write, until an observation sees it land: an Apply
    # commits asynchronously, after observations that still read the old value.
    expected: str | None = None

    @property
    def id(self) -> str:
        return f"{self.kind}:{self.key}"


@dataclass
class Runtime:
    service_on: bool
    paused: bool
    # The brightness mode the owner gets back when Tideo stops: its saved_brightness_mode while
    # it runs, else the setting. Private to Tideo, so verified after recovery, never written.
    owner_mode: str | None = None
    # Whether the pipeline could write brightness during the run: on at the start, or startable.
    driven: bool = False


class Journal:
    """Open with `for_run` or `for_recovery`; both hold the lock until `release`."""

    def __init__(self, store: Path, lock_fd: int):
        self.store = store
        self.path = store / "journal.json"
        self._lock_fd = lock_fd
        self.identity: Identity | None = None
        self.entries: dict[str, Entry] = {}
        self.runtime: Runtime | None = None
        # The curve points the run found, while an override it records may still be stored.
        # Only Tideo's Discard removes one, and only while the service that recorded it runs.
        self.points: list[str] | None = None

    # ── opening ──

    @classmethod
    def _locked(cls, store: Path) -> Journal:
        _ensure_store(store)
        fd = os.open(store / "journal.lock", os.O_RDWR | os.O_CREAT, 0o600)
        try:
            fcntl.flock(fd, fcntl.LOCK_EX | fcntl.LOCK_NB)
        except BlockingIOError:
            os.close(fd)
            raise JournalLocked("another run or recovery holds the journal lock") from None
        except BaseException:
            os.close(fd)
            raise
        return cls(store, fd)

    @classmethod
    def for_run(cls, store: Path, identity: Identity) -> Journal:
        j = cls._locked(store)
        try:
            if j.path.exists():
                raise PendingJournal(f"{j.path} is not empty: run --recover, or resolve it")
            j.identity = identity
            j._persist()
        except BaseException:
            j.release()
            raise
        return j

    @classmethod
    def for_recovery(cls, store: Path) -> Journal:
        j = cls._locked(store)
        try:
            if j.path.exists():
                j._load()
        except BaseException:
            j.release()
            raise
        return j

    def release(self) -> None:
        if self._lock_fd >= 0:
            os.close(self._lock_fd)  # closing the descriptor drops the flock
            self._lock_fd = -1

    def __enter__(self) -> Journal:
        return self

    def __exit__(self, *_exc) -> None:
        self.release()

    # ── identity ──

    def check_identity(self, identity: Identity) -> None:
        if self.identity is not None and identity != self.identity:
            fields = [k for k, v in asdict(identity).items() if asdict(self.identity)[k] != v]
            raise IdentityMismatch(f"journal is bound to another {', '.join(fields)}")

    # ── write-ahead recording (call BEFORE the mutation) ──

    def watch(self, kind: str, key: str, original: str | None) -> Entry:
        """Journal a key the next action may change, through Tideo or directly."""
        self._check()
        if kind not in KINDS:
            raise JournalError(f"unknown kind {kind!r}")
        if original is None:
            raise UnrestorableOriginal(f"{kind}:{key} is absent; restoring it needs a delete")
        entry = self.entries.get(f"{kind}:{key}")
        if entry is None:
            entry = Entry(kind, key, original, [])
            self.entries[entry.id] = entry
            self._persist()
        return entry

    def expect(self, kind: str, key: str, original: str | None, value: str) -> None:
        """Journal a value the harness is about to write itself."""
        entry = self.watch(kind, key, original)
        expected = None if value == entry.original else value
        if entry.expected != expected:
            entry.expected = expected
            self._persist()

    def observe(self, kind: str, key: str, value: str | None) -> None:
        """After an action the run performed, attribute the value it left on a watched key.

        The window between the action and this read is the run's: a change by someone else in
        that window would be attributed to the run. Keep the action short. The device holds
        `value` now, so it replaces whatever the run left there before: a value the run wrote
        earlier and then moved off is the owner's again if it reappears."""
        self._check()
        entry = self.entries[f"{kind}:{key}"]
        if value is None:
            raise UnrestorableOriginal(f"{entry.id} became absent; only a delete could be undone")
        now = [] if value == entry.original else [value]
        expected = None if value == entry.expected else entry.expected
        if (entry.attributable, entry.expected) != (now, expected):
            entry.attributable, entry.expected = now, expected
            self._persist()

    def set_runtime(self, service_on: bool, paused: bool, owner_mode: str | None = None,
                    driven: bool = False) -> None:
        """Record the runtime state once, before the first action that may change it."""
        self._check()
        if self.runtime is None:
            self.runtime = Runtime(service_on, paused, owner_mode, driven)
            self._persist()

    def set_points(self, points: list[str]) -> None:
        """Before the first action that may record an override: the points found."""
        self._check()
        if self.points is None:
            self.points = list(points)
            self._persist()

    def clear_points(self) -> None:
        self._check()
        self.points = None
        self._persist()

    # ── recovery bookkeeping ──

    def pending(self, kind: str | None = None) -> list[Entry]:
        return [e for e in self.entries.values()
                if e.state == PENDING and (kind is None or e.kind == kind)]

    def conflicts(self) -> list[Entry]:
        return [e for e in self.entries.values() if e.state == CONFLICT]

    def mark_conflict(self, entry: Entry, seen: str | None) -> None:
        self._check()
        entry.state, entry.seen = CONFLICT, seen
        self._persist()

    def drop(self, entries: list[Entry]) -> None:
        self._check()
        for e in entries:
            del self.entries[e.id]
        self._persist()

    def clear_runtime(self) -> None:
        self._check()
        self.runtime = None
        self._persist()

    def resolve(self, entry_id: str) -> None:
        """The owner's call: forget a conflict, leaving the device as it is."""
        entry = self.entries[entry_id]
        if entry.state != CONFLICT:
            raise JournalError(f"{entry_id} is not a conflict; recover it instead")
        self.drop([entry])

    @property
    def empty(self) -> bool:
        return not self.entries and self.runtime is None and self.points is None

    # ── storage: atomic replace, fsync'd file and directory ──

    def _check(self) -> None:
        if self._lock_fd < 0:
            raise JournalError("journal is released")
        if self._broken:
            raise JournalError("a journal write failed; reopen it with for_recovery")

    _broken = False

    def _persist(self) -> None:
        self._check()
        try:
            self._write()
        except BaseException:
            self._broken = True  # memory is ahead of the disk now; never act on it
            raise

    def _write(self) -> None:
        doc = {
            "version": VERSION,
            "identity": asdict(self.identity) if self.identity else None,
            "entries": [asdict(e) for e in self.entries.values()],
            "runtime": asdict(self.runtime) if self.runtime else None,
            "points": self.points,
        }
        tmp = self.path.with_suffix(".tmp")
        fd = os.open(tmp, os.O_WRONLY | os.O_CREAT | os.O_TRUNC, 0o600)
        with os.fdopen(fd, "w", encoding="utf-8") as f:
            json.dump(doc, f, indent=1, sort_keys=True)
            f.flush()
            os.fsync(f.fileno())
        os.replace(tmp, self.path)
        fsync_dir(self.store)

    def close_clean(self) -> None:
        """Delete the journal file; only an empty journal may go."""
        self._check()
        if not self.empty:
            raise JournalError("journal still holds entries")
        if self.path.exists():
            self.path.unlink()
            fsync_dir(self.store)

    def _load(self) -> None:
        doc = json.loads(self.path.read_text(encoding="utf-8"))
        if doc.get("version") != VERSION:
            raise JournalError(f"journal version {doc.get('version')!r}; this harness reads "
                               f"{VERSION}")
        if not doc.get("identity"):
            raise JournalError("journal has no identity binding; the owner must inspect it")
        self.identity = Identity(**doc["identity"])
        self.entries = {e.id: e for e in (Entry(**row) for row in doc["entries"])}
        self.runtime = Runtime(**doc["runtime"]) if doc["runtime"] else None
        self.points = doc.get("points")
