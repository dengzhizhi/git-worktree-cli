(ns commons.paths
  (:require [babashka.fs :as fs]
            [clojure.string :as str]
            [commons.config :as config]
            [commons.git :as git]))

(defn validate-branch-name
  "Validates a git branch name. Returns {:valid? true} or {:valid? false :error msg}."
  [branch]
  (cond
    (str/blank? branch)
    {:valid? false :error "Branch name cannot be empty"}

    (re-find #"[\s~^:?*\[\\]" branch)
    {:valid? false :error (str "Branch name contains invalid characters: " branch)}

    (str/includes? branch "..")
    {:valid? false :error "Branch name cannot contain '..'"}

    (str/ends-with? branch ".lock")
    {:valid? false :error "Branch name cannot end with '.lock'"}

    :else
    {:valid? true}))

(defn sanitize-branch-name
  "Replaces '/' with '-' so the branch name is safe to use as a directory name."
  [branch]
  (str/replace branch "/" "-"))

(defn resolve-worktree-path
  "3-tier path resolution for a new worktree:
   1. custom-path (from --path flag) — absolutized
   2. configured worktreepath → worktreepath/repo-name/sanitized-branch
   3. sibling — parent-of-cwd/cwd-name-sanitized-branch"
  [branch-name {:keys [custom-path cwd]}]
  (let [cwd (or cwd (str (fs/cwd)))]
    (cond
      custom-path
      (str (fs/absolutize custom-path))

      (config/get-worktreepath)
      (let [wt-base    (config/get-worktreepath)
            repo-root  (git/get-repo-root)
            repo-name  (fs/file-name repo-root)
            branch-dir (sanitize-branch-name branch-name)]
        (str (fs/path wt-base repo-name branch-dir)))

      :else
      (let [parent     (str (fs/parent cwd))
            dir-name   (fs/file-name cwd)
            branch-dir (sanitize-branch-name branch-name)]
        (str (fs/path parent (str dir-name "-" branch-dir)))))))
