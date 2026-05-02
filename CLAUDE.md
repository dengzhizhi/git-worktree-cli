# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## What This Repository Is

A git worktree manager CLI built with [Babashka](https://babashka.org). It manages multiple git worktrees with interactive TUI selection, atomic creation/rollback, setup script support, and per-editor executable configuration.

## Project Structure

```
worktree           # Entry point (shebang: #!/usr/bin/env bb); dispatch table lives here
bb.edn             # Babashka project config; test task with explicit namespace list
src/
  commands/        # One file per command — thin handlers calling commons
    new.clj
    setup.clj
    list.clj
    remove.clj
    merge.clj
    purge.clj
    open.clj       # Editor resolution, validation, launch
    config.clj     # config set/get/clear/path/set-editor-path/get-editor-path
  commons/
    git.clj        # Git operations, porcelain parsing
    config.clj     # EDN config I/O, XDG-aware path
    editors.clj    # valid-editors set + default-executables map (single source of truth)
    paths.clj      # Path resolution, branch validation
    tui.clj        # fzf integration with numbered-list fallback
    atomic.clj     # Atomic worktree creation with rollback
    setup.clj      # Setup script discovery and execution
    help.clj       # --help formatter
test/
  commands/
    config_test.clj
    open_test.clj
    list_test.clj
  commons/
    git_test.clj
    config_test.clj
    paths_test.clj
    tui_test.clj
    setup_test.clj
    help_test.clj
```

## Babashka Implementation Rules

- **Entry point**: `worktree` script at project root with shebang `#!/usr/bin/env bb`
- **CLI parsing**: built-in `babashka.cli` only — no external CLI libraries
- **Module size**: no source file may exceed 500 lines
- **Tests**: unit tests in `test/` for pure functions; run with `bb test`
- **Test registration**: new test namespaces MUST be added to both `require` and `run-tests` in `bb.edn` — they are NOT auto-discovered
- **Config storage**: EDN at `$XDG_CONFIG_HOME/worktree/config.edn`, falling back to `~/.config/worktree/config.edn`
- **TUI**: use `fzf` (piped via shell) for interactive selection; fall back to numbered list if `fzf` not found
- **Shell invocation**: always use varargs form `(p/shell executable arg1 arg2)` — never string concat `(p/shell (str cmd " " arg))`
- **Commit messages**: use descriptive messages; prefix with ticket ID when working on a tracked ticket

## Key Architectural Patterns

### Dispatch table (`worktree`)
Each command is a map with `:cmds`, `:desc`, `:fn`, `:args->opts`, and `:spec`. The `:spec` drives `--help` output. New commands follow the same shape.

### Exit wrapper pattern
Command namespaces define `(def exit! (fn [code] (System/exit code)))` as a redefable var. Tests redef it to capture exit codes without terminating the process.

### Config layer (`commons/config.clj`)
Pure I/O — reads/writes EDN, no domain validation. Config shape:
```edn
{:editor "vscode"
 :worktreepath "/my/worktrees"
 :editor-paths {:vscode "/opt/homebrew/bin/code"
                :nvim   "/usr/local/bin/nvim"}}
```
- `get-editor` returns `nil` when no editor is set (no default)
- `get-editor-path` / `set-editor-path!` use `(keyword editor)` — never string keys

### Editor data (`commons/editors.clj`)
Single source of truth — imported by `commands.open`, `commands.config`. Never duplicate these values.

**Supported editors**: `cursor`, `idea`, `nvim`, `pycharm`, `sublime`, `vim`, `vimr`, `vscode`, `webstorm`, `zed`

Default executables use bare command names (PATH-resolved), not absolute paths. Users override with `wt config set-editor-path <editor> <path>`.

### Atomic operations (`commons/atomic.clj`)
Worktree creation follows acquire → create → setup → release, with rollback on any failure.

## Running the Tool

```bash
./worktree list
./worktree open feature/my-branch --editor nvim
bb test   # run unit tests
```
