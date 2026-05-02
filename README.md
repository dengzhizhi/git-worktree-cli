# worktree

A fast git worktree manager built with [Babashka](https://babashka.org). Manages multiple git worktrees with interactive TUI, atomic creation, and setup script support.

No Node.js required — only `bb` (Babashka) and optionally `fzf`.

## Prerequisites

- [Babashka](https://babashka.org) (`bb`) — required
- [fzf](https://github.com/junegunn/fzf) — optional (falls back to numbered list)

## Installation

```bash
# Clone the repo
git clone https://github.com/yourname/worktree-cli.git

# Symlink the script into your PATH
ln -sf "$PWD/worktree-cli/worktree" ~/bin/worktree

# Verify
worktree list
```

## Quick Start

```bash
# List all worktrees
worktree list

# Create a new worktree for a branch
worktree new feature/my-feature

# Open a worktree in your editor
worktree open feature/my-feature

# Remove a worktree
worktree remove feature/my-feature

# Configure your editor (valid: cursor, idea, nvim, pycharm, sublime, vim, vimr, vscode, webstorm, zed)
worktree config set editor vscode
```

## Commands

| Command | Description |
|---|---|
| `new <branch>` | Create a new worktree |
| `setup <branch>` | Create a worktree and run setup scripts |
| `list` / `ls` | List all worktrees |
| `remove <branch>` / `rm` | Remove a worktree |
| `merge <branch>` | Merge a branch into current branch |
| `purge` | Interactively remove multiple worktrees |
| `open [branch]` | Open a worktree in your editor |
| `config` | Manage configuration |

See [GUIDE.md](GUIDE.md) for full documentation.
