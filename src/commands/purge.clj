(ns commands.purge
  (:require [babashka.fs :as fs]
            [babashka.process :as p]
            [clojure.string :as str]
            [commons.git :as git]
            [commons.tui :as tui]))

(defn- remove-worktree
  "Attempts to remove a worktree. Returns :removed, :skipped, or :failed."
  [wt]
  (let [path   (:path wt)
        branch (or (:branch wt) "(detached)")
        result @(p/process {:out :string :err :string}
                            "git" "worktree" "remove" path)]
    (if (zero? (:exit result))
      (do
        (when (fs/exists? path) (fs/delete-tree path))
        (println (str "  Removed: " branch))
        :removed)
      (let [err (:err result)]
        (cond
          (str/includes? err "locked")
          (if (tui/confirm (str "  Worktree '" branch "' is locked. Force remove?") false)
            (do
              @(p/process "git" "worktree" "remove" "--force" path)
              (when (fs/exists? path) (fs/delete-tree path))
              (println (str "  Force removed: " branch))
              :removed)
            (do (println (str "  Skipped: " branch))
                :skipped))

          (str/includes? err "modified or untracked files")
          (if (tui/confirm (str "  Worktree '" branch "' has modified files. Force remove?") false)
            (do
              @(p/process "git" "worktree" "remove" "--force" path)
              (when (fs/exists? path) (fs/delete-tree path))
              (println (str "  Force removed: " branch))
              :removed)
            (do (println (str "  Skipped: " branch))
                :skipped))

          :else
          (do (println (str "  Failed: " branch " — " err))
              :failed))))))

(defn run
  "Interactively multi-select and remove worktrees."
  [_]
  (let [worktrees (git/get-worktrees)
        purgeable (filter (complement :main?) worktrees)]
    (if (empty? purgeable)
      (println "No worktrees to purge.")
      (do
        (println (str "Purgeable worktrees (" (count purgeable) "):"))
        (doseq [wt purgeable]
          (println (str "  " (tui/format-worktree-line wt))))
        (println)
        (if-let [selected (tui/multi-select-worktrees worktrees {:prompt "Select worktrees to remove: "})]
          (do
            (println (str "\nSelected " (count selected) " worktree(s) for removal:"))
            (doseq [wt selected]
              (println (str "  " (or (:branch wt) "(detached)"))))
            (println)
            (when (tui/confirm "Proceed with removal?" false)
              (println "\nRemoving...")
              (doseq [wt selected]
                (remove-worktree wt))
              (println "\nPurge complete.")))
          (println "No worktrees selected."))))))
