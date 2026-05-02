(ns commons.git
  (:require [babashka.process :as p]
            [clojure.string :as str]))

(defn parse-worktrees
  "Parse `git worktree list --porcelain` output into a vector of WorktreeInfo maps.
   The first block is always the main worktree (:main? true)."
  [porcelain-output]
  (let [blocks (->> (str/split porcelain-output #"\n\n")
                    (map str/trim)
                    (filter #(not (str/blank? %))))]
    (vec
      (map-indexed
        (fn [idx block]
          (let [lines (str/split-lines block)
                info  (reduce
                        (fn [acc line]
                          (cond
                            (str/starts-with? line "worktree ")
                            (assoc acc :path (subs line 9))

                            (str/starts-with? line "HEAD ")
                            (assoc acc :head (subs line 5))

                            (str/starts-with? line "branch ")
                            (let [ref (subs line 7)]
                              (assoc acc :branch (str/replace ref "refs/heads/" "")))

                            (= line "detached")
                            (assoc acc :detached? true)

                            (= line "bare")
                            (assoc acc :bare? true)

                            (= line "locked")
                            (assoc acc :locked? true :lock-reason nil)

                            (str/starts-with? line "locked ")
                            (assoc acc :locked? true :lock-reason (subs line 7))

                            (= line "prunable")
                            (assoc acc :prunable? true :prune-reason nil)

                            (str/starts-with? line "prunable ")
                            (assoc acc :prunable? true :prune-reason (subs line 9))

                            :else acc))
                        {:path nil
                         :head nil
                         :branch nil
                         :detached? false
                         :locked? false
                         :lock-reason nil
                         :prunable? false
                         :prune-reason nil
                         :bare? false}
                        lines)]
            (assoc info :main? (zero? idx))))
        blocks))))

(defn get-worktrees
  "Run git worktree list --porcelain and return parsed WorktreeInfo maps."
  []
  (let [result (p/shell {:out :string :continue true} "git worktree list --porcelain")]
    (parse-worktrees (or (:out result) ""))))

(defn is-worktree-clean?
  "Returns true if the working tree at cwd has no uncommitted changes."
  [cwd]
  (let [result @(p/process {:out :string} "git" "-C" cwd "status" "--porcelain")]
    (str/blank? (str/trim (:out result)))))

(defn is-main-repo-bare?
  "Returns true if the main git repo is a bare repo."
  []
  (let [result (p/shell {:out :string :continue true}
                         "git config --get --bool core.bare")]
    (= "true" (str/trim (or (:out result) "")))))

(defn get-repo-root
  "Returns the root path of the current git repo, or nil if not in a git repo."
  []
  (let [result (p/shell {:out :string :continue true}
                         "git rev-parse --show-toplevel")]
    (when (zero? (:exit result))
      (str/trim (:out result)))))

(defn get-current-branch
  "Returns the current branch name (abbrev ref)."
  []
  (let [result (p/shell {:out :string} "git rev-parse --abbrev-ref HEAD")]
    (str/trim (:out result))))

(defn stash-changes
  "Stashes uncommitted changes using `git stash create` (does NOT add to stash list).
   Returns the stash commit hash, or nil if worktree is already clean."
  ([cwd] (stash-changes cwd nil))
  ([cwd message]
   (when-not (is-worktree-clean? cwd)
     ;; Stage untracked files
     @(p/process "git" "-C" cwd "add" "-A")
     ;; Create stash object (returns hash)
     (let [result (if message
                    @(p/process {:out :string} "git" "-C" cwd "stash" "create" message)
                    @(p/process {:out :string} "git" "-C" cwd "stash" "create"))
           hash   (str/trim (:out result))]
       ;; Clean working tree
       @(p/process "git" "-C" cwd "reset" "--hard" "HEAD")
       @(p/process "git" "-C" cwd "clean" "-fd")
       (when-not (str/blank? hash) hash)))))

(defn apply-stash
  "Restores a previously stashed state by hash."
  [hash cwd]
  (when hash
    @(p/process "git" "-C" cwd "stash" "apply" hash)))

(defn get-upstream-remote
  "Returns the first remote name (typically 'origin'), or nil if none."
  []
  (let [result (p/shell {:out :string :continue true} "git remote")]
    (when (zero? (:exit result))
      (let [remotes (str/split-lines (str/trim (:out result)))]
        (first (filter #(not (str/blank? %)) remotes))))))

(defn branch-exists-local?
  "Returns true if branch exists locally."
  [branch]
  (let [result @(p/process {:out :string} "git" "branch" "--list" branch)]
    (not (str/blank? (str/trim (:out result))))))

(defn branch-exists-remote?
  "Returns true if branch exists on the given remote."
  [branch remote]
  (let [result @(p/process {:out :string} "git" "branch" "-r" "--list"
                            (str remote "/" branch))]
    (not (str/blank? (str/trim (:out result))))))

(defn find-worktree-by-branch
  "Find a worktree by branch name. Returns WorktreeInfo or nil."
  [branch]
  (first (filter #(= branch (:branch %)) (get-worktrees))))

(defn find-worktree-by-path
  "Find a worktree by absolute path. Returns WorktreeInfo or nil."
  [path]
  (first (filter #(= path (:path %)) (get-worktrees))))
