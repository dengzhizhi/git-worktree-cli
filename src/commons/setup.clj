(ns commons.setup
  (:require [babashka.fs :as fs]
            [babashka.process :as p]
            [cheshire.core :as json]
            [clojure.string :as str]
            [commons.git :as git]
            [commons.tui :as tui]))

(defn- parse-setup-file
  "Parse a worktrees.json file. Returns vector of command strings or nil."
  [file-path]
  (when (fs/exists? file-path)
    (try
      (let [content (slurp file-path)
            parsed  (json/parse-string content)]
        (cond
          ;; Object with "setup-worktree" key (check before sequential to handle map-as-seq edge cases)
          (and (map? parsed) (contains? parsed "setup-worktree"))
          (let [cmds (get parsed "setup-worktree")]
            (when (seq cmds) (vec cmds)))

          ;; Plain array (JSON array → sequential in cheshire)
          (sequential? parsed)
          (when (seq parsed) (vec parsed))

          :else nil))
      (catch Exception e
        (binding [*out* *err*]
          (println "Warning: failed to parse" (str file-path) ":" (.getMessage e)))
        nil))))

(defn load-setup-commands
  "Discovers and loads setup commands from worktrees.json in the repo.
   Checks .cursor/worktrees.json, then .claude/worktrees.json, then worktrees.json at root.
   Returns {:commands [...] :file-path \"...\"} or nil."
  [repo-root]
  (let [cursor-path (str repo-root "/.cursor/worktrees.json")
        claude-path (str repo-root "/.claude/worktrees.json")
        root-path   (str repo-root "/worktrees.json")]
    (or
      (when-let [cmds (parse-setup-file cursor-path)]
        {:commands (vec cmds) :file-path cursor-path})
      (when-let [cmds (parse-setup-file claude-path)]
        {:commands (vec cmds) :file-path claude-path})
      (when-let [cmds (parse-setup-file root-path)]
        {:commands (vec cmds) :file-path root-path}))))

(defn run-setup-scripts-secure
  "Discovers setup scripts and runs them with the trust model.
   Without :trust?, shows commands and prompts for confirmation.
   With :trust?, runs without prompting.
   Returns true if setup ran, false if skipped or not found."
  [worktree-path {:keys [trust?]}]
  (let [repo-root (git/get-repo-root)]
    (when repo-root
      (when-let [setup (load-setup-commands repo-root)]
        (println (str "Found setup file: " (:file-path setup)))
        (when (tui/confirm-commands (:commands setup)
                                    {:title "The following setup commands will be executed:"
                                     :trust? trust?})
          (let [env (merge (into {} (System/getenv))
                           {"ROOT_WORKTREE_PATH" repo-root})]
            (doseq [cmd (:commands setup)]
              (println (str "Executing: " cmd))
              (let [result (p/shell {:continue true :dir worktree-path :env env} cmd)]
                (when (not (zero? (:exit result)))
                  (println (str "Setup command failed (exit " (:exit result) "): " cmd)))))
            (println "Setup commands completed.")
            true))))))
