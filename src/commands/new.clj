(ns commands.new
  (:require [babashka.fs :as fs]
            [babashka.process :as p]
            [clojure.string :as str]
            [commons.atomic :as atomic]
            [commons.git :as git]
            [commons.paths :as paths]
            [commons.tui :as tui]))

(defn- run-package-manager
  "Runs package manager install in the given directory."
  [install-cmd dir]
  (println (str "Running: " install-cmd " (in " dir ")"))
  (let [result (p/shell {:continue true :dir dir} install-cmd)]
    (when (not (zero? (:exit result)))
      (println (str "Warning: package manager install exited with code " (:exit result))))))

(defn create-or-use-worktree
  "Creates a new worktree or reuses an existing one at path.
   Returns the worktree path."
  [branch-name path checkout install]
  (if (and (fs/exists? path)
           (fs/exists? (str path "/.git")))
    (do (println (str "Using existing worktree at " path))
        path)
    (do
      (atomic/with-atomic-rollback
        (fn [register-rollback!]
          (let [branch-exists (or (git/branch-exists-local? branch-name)
                                  (when-let [remote (git/get-upstream-remote)]
                                    (git/branch-exists-remote? branch-name remote)))
                create-branch? (or checkout (not branch-exists))]
            (println (str "Creating worktree for branch '" branch-name "' at " path "..."))
            (atomic/create-worktree register-rollback! path branch-name create-branch?)
            (when install
              (run-package-manager install path)))))
      path)))

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
            (create-or-use-worktree branch-name target-path checkout install)
            (println (str "Worktree ready at: " target-path)))

          (finally
            ;; Restore stash if we stashed
            (when @stash-hash
              (println "Restoring stashed changes...")
              (git/apply-stash @stash-hash (str (fs/cwd))))))))))
