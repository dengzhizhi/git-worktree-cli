(ns commands.remove
  (:require [babashka.fs :as fs]
            [babashka.process :as p]
            [clojure.string :as str]
            [commons.git :as git]
            [commons.tui :as tui]))

(defn- do-remove
  "Executes git worktree remove and cleans up the directory."
  [wt force?]
  (let [path  (:path wt)
        branch (or (:branch wt) "(detached)")
        args  (if force?
                ["git" "worktree" "remove" "--force" path]
                ["git" "worktree" "remove" path])
        result (if force?
                 @(p/process {:out :string :err :string} "git" "worktree" "remove" "--force" path)
                 @(p/process {:out :string :err :string} "git" "worktree" "remove" path))]
    (if (zero? (:exit result))
      (do
        (when (fs/exists? path) (fs/delete-tree path))
        (println (str "Removed worktree '" branch "' at " path)))
      (let [err-msg (:err result)]
        (if (and (not force?)
                 (str/includes? err-msg "modified or untracked files"))
          ;; Offer to force remove
          (if (tui/confirm "Worktree has modified files. Force remove?" false)
            (do
              @(p/process "git" "worktree" "remove" "--force" path)
              (when (fs/exists? path) (fs/delete-tree path))
              (println (str "Force removed worktree '" branch "' at " path)))
            (println "Removal cancelled."))
          (do (println (str "Error removing worktree: " err-msg))
              (System/exit 1)))))))

(defn run
  "Remove a git worktree."
  [{:keys [opts]}]
  (let [{:keys [path-or-branch force]} opts
        worktrees (git/get-worktrees)]
    (cond
      ;; Specific target given
      path-or-branch
      (let [target (or (git/find-worktree-by-path (name path-or-branch))
                       (git/find-worktree-by-branch (name path-or-branch)))]
        (cond
          (nil? target)
          (do (println (str "Error: no worktree found for '" (name path-or-branch) "'"))
              (System/exit 1))

          (:main? target)
          (do (println "Error: cannot remove the main worktree")
              (System/exit 1))

          (and (:locked? target) (not force))
          (do (println (str "Error: worktree is locked" (when (:lock-reason target)
                                                           (str ": " (:lock-reason target)))))
              (println "Use --force to remove a locked worktree.")
              (System/exit 1))

          :else
          (let [branch (or (:branch target) "(detached)")]
            (println (str "Removing worktree '" branch "' at " (:path target)))
            (when-not force
              (when-not (tui/confirm "Are you sure?" false)
                (println "Removal cancelled.")
                (System/exit 0)))
            (do-remove target force))))

      ;; No target — interactive select (exclude main)
      :else
      (if-let [selected (tui/select-worktree worktrees {:prompt "Select worktree to remove: "
                                                         :exclude-main? true})]
        (do
          (when (and (:locked? selected) (not force))
            (println (str "Error: worktree is locked"))
            (println "Use --force to remove a locked worktree.")
            (System/exit 1))
          (println (str "Removing worktree '" (or (:branch selected) "(detached)") "'"))
          (when-not force
            (when-not (tui/confirm "Are you sure?" false)
              (println "Removal cancelled.")
              (System/exit 0)))
          (do-remove selected force))
        (println "No worktree selected.")))))
