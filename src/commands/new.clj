(ns commands.new
  (:require [babashka.fs :as fs]
            [babashka.process :as p]
            [clojure.string :as str]
            [commons.atomic :as atomic]
            [commons.git :as git]
            [commons.package :as package]
            [commons.paths :as paths]
            [commons.tui :as tui]
            [commons.worktree :as worktree]))

(defn run
  "Create a new git worktree."
  [{:keys [opts]}]
  (let [{:keys [branch-name path checkout install]} opts]

    ;; Validate we're in a git repo
    (when (nil? (git/get-repo-root))
      (println "Error: not inside a git repository")
      (System/exit 1))

    ;; Validate branch name
    (when (nil? branch-name)
      (println "Usage: worktree new <branchName>")
      (System/exit 1))

    (let [branch-name (name branch-name)
          validation  (paths/validate-branch-name branch-name)]
      (when-not (:valid? validation)
        (println (str "Error: " (:error validation)))
        (System/exit 1))

      (let [is-bare  (git/is-main-repo-bare?)
            stash-hash (atom nil)]
        (try
          ;; Handle dirty state (only for non-bare repos)
          (when-not is-bare
            (let [cwd (str (fs/cwd))]
              (when-not (git/is-worktree-clean? cwd)
                (let [action (tui/handle-dirty-state nil)]
                  (case action
                    :abort (do (println "Operation aborted.") (System/exit 0))
                    :stash (let [hash (git/stash-changes cwd "worktree new: stash before creating")]
                             (reset! stash-hash hash)
                             (println (str "Stashed changes (hash: " hash ")")))
                    :continue nil)))))

          ;; Resolve target path
          (let [target-path (paths/resolve-worktree-path branch-name
                                                          {:custom-path (when path (name path))
                                                           :cwd (str (fs/cwd))})]
            (worktree/create-or-use-worktree branch-name target-path checkout install stash-hash)
            (println (str "Worktree ready at: " target-path)))

          (finally
            ;; Restore stash if we stashed
            (when @stash-hash
              (println "Restoring stashed changes...")
              (git/apply-stash @stash-hash (str (fs/cwd))))))))))
