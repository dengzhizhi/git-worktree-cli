(ns commands.setup
  (:require [babashka.fs :as fs]
            [babashka.process :as p]
            [clojure.string :as str]
            [commons.atomic :as atomic]
            [commons.git :as git]
            [commons.paths :as paths]
            [commons.setup :as setup]
            [commons.tui :as tui]))

(defn- run-package-manager
  "Runs package manager install in the given directory."
  [install-cmd dir]
  (println (str "Running: " install-cmd " (in " dir ")"))
  (let [result (p/shell {:continue true :dir dir} install-cmd)]
    (when (not (zero? (:exit result)))
      (println (str "Warning: package manager install exited with code " (:exit result))))))

(defn run
  "Create a new git worktree and run setup scripts."
  [{:keys [opts]}]
  (let [{:keys [branch-name path checkout install trust]} opts]

    ;; Validate we're in a git repo
    (when (nil? (git/get-repo-root))
      (println "Error: not inside a git repository")
      (System/exit 1))

    ;; Validate branch name
    (when (nil? branch-name)
      (println "Usage: worktree setup <branchName>")
      (System/exit 1))

    (let [branch-name (name branch-name)
          validation  (paths/validate-branch-name branch-name)]
      (when-not (:valid? validation)
        (println (str "Error: " (:error validation)))
        (System/exit 1))

      (let [is-bare    (git/is-main-repo-bare?)
            stash-hash (atom nil)]
        (try
          ;; Handle dirty state (only for non-bare repos)
          (when-not is-bare
            (let [cwd (str (fs/cwd))]
              (when-not (git/is-worktree-clean? cwd)
                (let [action (tui/handle-dirty-state nil)]
                  (case action
                    :abort (do (println "Operation aborted.") (System/exit 0))
                    :stash (let [hash (git/stash-changes cwd "worktree setup: stash before creating")]
                             (reset! stash-hash hash)
                             (println (str "Stashed changes (hash: " hash ")")))
                    :continue nil)))))

          ;; Resolve target path
          (let [target-path (paths/resolve-worktree-path branch-name
                                                          {:custom-path (when path (name path))
                                                           :cwd (str (fs/cwd))})]
            ;; Create worktree (same as new command)
            (if (and (fs/exists? target-path)
                     (fs/exists? (str target-path "/.git")))
              (println (str "Using existing worktree at " target-path))
              (atomic/with-atomic-rollback
                (fn [register-rollback!]
                  (let [branch-exists (or (git/branch-exists-local? branch-name)
                                          (when-let [remote (git/get-upstream-remote)]
                                            (git/branch-exists-remote? branch-name remote)))
                        create-branch? (or checkout (not branch-exists))]
                    (println (str "Creating worktree for branch '" branch-name "' at " target-path "..."))
                    (atomic/create-worktree register-rollback! target-path branch-name create-branch?)
                    (when install
                      (run-package-manager install target-path))))))

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
