# Development Guide

## Running the tool

```bash
# From the repo root (direct invocation)
./worktree list
./worktree new test-branch

# Or symlink into PATH
ln -sf "$PWD/worktree" ~/bin/worktree
```

## Running tests

```bash
bb test
```

Tests cover pure functions and command handlers:
- `commons/git_test.clj` — porcelain parsing, stash commands
- `commons/config_test.clj` — EDN read/write, XDG path, editor-path round-trips
- `commons/paths_test.clj` — branch validation, 3-tier path resolution
- `commons/tui_test.clj` — format-worktree-line, numbered list, confirm
- `commons/setup_test.clj` — worktrees.json dual-format parsing
- `commands/config_test.clj` — set-editor-path!, get-editor-path!, editor validation
- `commands/open_test.clj` — resolve-executable, editor validation, shell invocation
- `commands/list_test.clj` — list formatting and flags

## Project structure

```
worktree           # Entry point script (shebang: #!/usr/bin/env bb)
bb.edn             # Babashka project config
src/
  commands/        # One file per command
    new.clj        # worktree new
    setup.clj      # worktree setup
    list.clj       # worktree list/ls
    remove.clj     # worktree remove/rm
    merge.clj      # worktree merge
    purge.clj      # worktree purge
    open.clj       # worktree open
    config.clj     # worktree config
  commons/         # Shared modules
    git.clj        # Git operations, porcelain parsing
    config.clj     # EDN config (XDG-aware)
    paths.clj      # Path resolution, branch validation
    tui.clj        # fzf integration, numbered fallback, confirm
    atomic.clj     # Atomic worktree creation with rollback
    setup.clj      # Setup script discovery and execution
test/
  commons/         # Unit tests for commons/
```

## Contributing

1. Fork the repo
2. Create a feature branch: `worktree new feature/my-change`
3. Make your changes with tests
4. Run `bb test` — all must pass
5. Open a PR

## Design decisions

- **babashka.cli only** — no external CLI parsing libraries
- **p/shell for simple commands** — `p/process` for captured output or piped input
- **fzf with numbered fallback** — works without fzf installed
- **Atomic rollback** — worktree creation rolls back on failure
- **XDG config** — respects `$XDG_CONFIG_HOME`
