(ns commands.list
  (:require [clojure.string :as str]
            [commons.git :as git]
            [commons.tui :as tui]))

(defn- branch-name [wt]
  (cond
    (:bare? wt)     "(bare)"
    (:detached? wt) (str "(detached at " (subs (:head wt) 0 7) ")")
    :else           (:branch wt)))

(defn run
  "List all git worktrees with branch, path, and status indicators."
  [{:keys [opts]}]
  (let [worktrees (git/get-worktrees)
        name-only (:name-only opts)
        dir-only  (:dir-only opts)]
    (cond
      (empty? worktrees)
      (println "No worktrees found (not in a git repository?)")

      name-only
      (doseq [wt worktrees]
        (println (branch-name wt)))

      dir-only
      (doseq [wt worktrees]
        (println (:path wt)))

      :else
      (do
        (println (str "Worktrees (" (count worktrees) "):"))
        (doseq [wt worktrees]
          (println (str "  " (tui/format-worktree-line-colored wt))))))))
