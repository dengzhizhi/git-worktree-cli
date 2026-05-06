(ns commons.worktree
  (:require [babashka.fs :as fs]
            [commons.atomic :as atomic]
            [commons.git :as git]
            [commons.package :as package]))

(def exit!
  "Redefable exit for testing."
  (fn [code] (System/exit code)))

(defn create-or-use-worktree
  "Creates a new worktree or reuses an existing one at path.
   Returns the worktree path.
   Performs pre-check for existing worktree on the branch and
   catches git errors with graceful messages."
  [branch-name path checkout install stash-hash]
  ;; Pre-check: is branch already checked out in a worktree?
  (let [existing (git/find-worktree-by-branch branch-name)]
    (when existing
      (let [current (git/get-current-branch)]
        (if (= branch-name current)
          (do
            (println (str "Error: cannot create worktree for current branch (" current ")"))
            (when @stash-hash
              (println "Restoring stashed changes...")
              (git/apply-stash @stash-hash (str (fs/cwd))))
            (exit! 1))
          (do
            (println (str "Error: branch '" branch-name "' is already checked out at '" (:path existing) "'"))
            (when @stash-hash
              (println "Restoring stashed changes...")
              (git/apply-stash @stash-hash (str (fs/cwd))))
            (exit! 1))))))
  ;; Use existing worktree at path or create new one
  (if (and (fs/exists? path)
           (fs/exists? (str path "/.git")))
    (do (println (str "Using existing worktree at " path))
        path)
    (try
      (atomic/with-atomic-rollback
        (fn [register-rollback!]
          (let [branch-exists (or (git/branch-exists-local? branch-name)
                                  (when-let [remote (git/get-upstream-remote)]
                                    (git/branch-exists-remote? branch-name remote)))
                create-branch? (or checkout (not branch-exists))]
            (println (str "Creating worktree for branch '" branch-name "' at " path "..."))
            (atomic/create-worktree register-rollback! path branch-name create-branch?)
            (when install
              (package/run-package-manager install path)))))
      (catch clojure.lang.ExceptionInfo e
        (let [e-data (ex-data e)]
          (println "Failed to create worktree:")
          (println (:err e-data))
          (when @stash-hash
            (println "Restoring stashed changes...")
            (git/apply-stash @stash-hash (str (fs/cwd))))
          (exit! 1))))))
