(ns commands.setup
  (:require [babashka.fs :as fs]
            [babashka.process :as p]
            [clojure.string :as str]
            [commons.atomic :as atomic]
            [commons.git :as git]
            [commons.package :as package]
            [commons.paths :as paths]
            [commons.setup :as setup]
            [commons.tui :as tui]
            [commons.worktree :as worktree]))

(def exit!
  "Redefable exit for testing."
  (fn [code] (System/exit code)))

(defn run
  "Create a new git worktree and run setup scripts."
  [{:keys [opts]}]
  (let [{:keys [branch-name path checkout install trust]} opts]

    ;; Validate we're in a git repo
    (when (nil? (git/get-repo-root))
      (println "Error: not inside a git repository")
      (exit! 1))

    ;; Validate branch name
    (when (nil? branch-name)
      (println "Usage: worktree setup <branchName>")
      (exit! 1))

    (let [branch-name (name branch-name)
          validation  (paths/validate-branch-name branch-name)]
      (when-not (:valid? validation)
        (println (str "Error: " (:error validation)))
        (exit! 1))

      (let [is-bare    (git/is-main-repo-bare?)
            stash-hash (atom nil)]
        (try
          ;; Handle dirty state (only for non-bare repos)
          (when-not is-bare
            (let [cwd (str (fs/cwd))]
              (when-not (git/is-worktree-clean? cwd)
                (let [action (tui/handle-dirty-state nil)]
                  (case action
                    :abort (do (println "Operation aborted.") (exit! 0))
                    :stash (let [hash (git/stash-changes cwd "worktree setup: stash before creating")]
                             (reset! stash-hash hash)
                             (println (str "Stashed changes (hash: " hash ")")))
                    :continue nil)))))

          ;; Resolve target path
          (let [target-path (paths/resolve-worktree-path branch-name
                                                          {:custom-path (when path (name path))
                                                           :cwd (str (fs/cwd))})]
            ;; Create worktree (same as new command, using shared function)
            (worktree/create-or-use-worktree branch-name target-path checkout install stash-hash)

            (println (str "Worktree ready at: " target-path))

            ;; Run setup scripts
            (let [repo-root (git/get-repo-root)]
              (if (and repo-root (commons.setup/load-setup-commands repo-root))
                (setup/run-setup-scripts-secure target-path {:trust? trust})
                (println "No setup file found. Create worktrees.json or .cursor/worktrees.json to define setup commands."))))

          (finally
            ;; Restore stash if we stashed
            (when @stash-hash
              (println "Restoring stashed changes...")
              (git/apply-stash @stash-hash (str (fs/cwd))))))))))
