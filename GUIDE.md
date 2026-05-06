# worktree — Full Usage Guide

## Configuration

Config is stored as EDN at `$XDG_CONFIG_HOME/worktree/config.edn` (defaults to `~/.config/worktree/config.edn`).

```bash
# Set editor (valid: cursor, idea, nvim, pycharm, sublime, vim, vimr, vscode, webstorm, zed)
worktree config set editor vscode

# Override the executable path for an editor (useful when not in PATH)
worktree config set-editor-path vscode /opt/homebrew/bin/code

# Get the resolved executable for an editor (override or default)
worktree config get-editor-path vscode

# Set base path for new worktrees
worktree config set worktreepath ~/worktrees

# Get a config value
worktree config get editor

# Clear worktreepath (revert to sibling behavior)
worktree config clear worktreepath

# Show config file location
worktree config path
```

## new — Create Worktree

```bash
worktree new <branchName> [options]

Options:
  -p, --path <path>       Custom path for the new worktree
  -c, --checkout          Create branch if it doesn't exist
  -i, --install <cmd>     Run package manager (npm, pnpm, bun, yarn)
  -e, --editor <editor>   Override editor for this session

Examples:
  worktree new feature/login
  worktree new feature/login --checkout
  worktree new feature/login --install pnpm --editor code
  worktree new feature/login --path /my/custom/path
```

**Path resolution (3 tiers):**
1. `--path` flag if provided
2. `worktreepath` config + repo name + sanitized branch name
3. Sibling directory: `../current-dir-name-branch-name`

**Dirty state handling:** If your current worktree has uncommitted changes, you'll be prompted to stash them (restored after), abort, or continue.

**Error messages:**

- `Error: cannot create worktree for current branch (main)` -- the specified branch is already checked out in the current repository and cannot have a separate worktree.
- `Error: branch 'feature/login' is already checked out at '/path/to/worktree'` -- the specified branch has a worktree at the given path. Each branch can only have one active worktree.
- `Failed to create worktree:` (followed by git's stderr) -- the `git worktree add` command failed for another reason (path already exists, nested path, permissions, etc.). The exact git error is shown below the prefix line.

## setup — Create Worktree + Run Scripts

```bash
worktree setup <branchName> [options]

Options:
  (all options from `new`, plus:)
  -t, --trust             Skip confirmation and run setup scripts directly

Examples:
  worktree setup feature/login
  worktree setup feature/login --trust
```

**Setup scripts** are discovered from (in order):
1. `.cursor/worktrees.json`
2. `.claude/worktrees.json`
3. `worktrees.json` at repo root

Supported formats:
```json
// Plain array:
["npm install", "cp .env.example .env"]

// Object with key:
{"setup-worktree": ["npm install", "cp .env.example .env"]}
```

**`ROOT_WORKTREE_PATH`** is injected into every setup command's environment and points to the main (root) worktree — the repo you ran `wt setup` from. This lets scripts copy shared files, reference config, or run tools that need to know where the canonical repo lives, regardless of where the new worktree is created.

```json
[
  "npm install",
  "cp $ROOT_WORKTREE_PATH/.env $PWD/.env",
  "cp $ROOT_WORKTREE_PATH/.env.local $PWD/.env.local"
]
```

Each command runs with its working directory set to the **new worktree**, so `$PWD` is the new worktree and `$ROOT_WORKTREE_PATH` is the main repo root.

## list — List Worktrees

```bash
worktree list
worktree ls
```

Shows all worktrees with branch name, path, and status indicators: `[main]`, `[locked]`, `[prunable]`.

## remove — Remove Worktree

```bash
worktree remove [pathOrBranch] [options]
worktree rm [pathOrBranch]

Options:
  -f, --force    Force removal even if locked or has untracked files

Examples:
  worktree remove feature/login
  worktree remove feature/login --force
  worktree remove          # interactive selection via fzf
```

## merge — Merge Branch

```bash
worktree merge <branchName> [options]

Options:
  --auto-commit           Auto-commit dirty worktree before merge
  -m, --message <msg>     Commit message for --auto-commit

Examples:
  worktree merge feature/login
  worktree merge feature/login --auto-commit
  worktree merge feature/login --auto-commit --message "WIP: feature work"
```

The source worktree is preserved after merge.

## purge — Remove Multiple Worktrees

```bash
worktree purge
```

Launches interactive multi-select (fzf with TAB, or numbered list). Shows all non-main worktrees, lets you select multiple, confirms, and removes them.

## open — Open Worktree in Editor

```bash
worktree open [pathOrBranch] [options]

Options:
  -e, --editor <editor>   Editor to use (required — see below)

Examples:
  worktree open feature/login --editor nvim
  worktree open feature/login --editor vscode
```

**An editor is required.** Either set a default once with `wt config set editor <name>`, or pass `--editor` on every invocation. If neither is provided, the command exits with an error.

```bash
# Set a default so you never need --editor again
worktree config set editor nvim

# Override per-invocation
worktree open feature/login --editor vscode
```

Valid editors: `cursor`, `idea`, `nvim`, `pycharm`, `sublime`, `vim`, `vimr`, `vscode`, `webstorm`, `zed`

If the editor executable is not in your `PATH`, override it with:
```bash
worktree config set-editor-path vscode /opt/homebrew/bin/code
```
