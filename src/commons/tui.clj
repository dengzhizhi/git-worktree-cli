(ns commons.tui
  (:require [clojure.string :as str]))

;; ANSI color helpers
(defn- ansi [& codes] (str "\033[" (str/join ";" codes) "m"))
(def ^:private reset   (ansi 0))
(def ^:private dim     (ansi 2))
(def ^:private cyan    (ansi 1 36))
(def ^:private green   (ansi 1 32))
(def ^:private yellow  (ansi 1 33))
(def ^:private red     (ansi 1 31))
(def ^:private magenta (ansi 1 35))
(def ^:private gray    (ansi 2 37))

(defn format-worktree-line-colored
  "Like format-worktree-line but with ANSI colors for terminal display."
  [{:keys [branch path head detached? bare? main? locked? prunable?]}]
  (let [display (cond
                  bare?
                  (str magenta "(bare)" reset gray " → " reset path)

                  detached?
                  (str yellow "(detached at "
                       (subs (or head "") 0 (min 8 (count (or head "")))) ")"
                       reset gray " → " reset path)

                  :else
                  (str cyan branch reset gray " → " reset path))
        tags    (cond-> []
                  main?     (conj (str green "[main]" reset))
                  locked?   (conj (str yellow "[locked]" reset))
                  prunable? (conj (str red "[prunable]" reset)))]
    (if (seq tags)
      (str display "  " (str/join " " tags))
      display)))

(defn format-worktree-line
  "Formats a WorktreeInfo map into a plain display string."
  [{:keys [branch path head detached? bare? main? locked? prunable?]}]
  (let [display (cond
                  bare?      (str "(bare) → " path)
                  detached?  (str "(detached at " (subs (or head "") 0 (min 8 (count (or head "")))) ") → " path)
                  :else      (str branch " → " path))
        tags    (cond-> []
                  main?     (conj "[main]")
                  locked?   (conj "[locked]")
                  prunable? (conj "[prunable]"))]
    (if (seq tags)
      (str display "  " (str/join " " tags))
      display)))

(defn numbered-select
  "Shows a numbered list and reads user input. Returns selected item or seq of items."
  [items {:keys [prompt multi?]}]
  (println (or prompt "Select:"))
  (doseq [[i item] (map-indexed vector items)]
    (println (str "  " (inc i) ") " item)))
  (print (if multi? "Enter numbers (comma-separated): " "Enter number: "))
  (flush)
  (let [input (read-line)]
    (if multi?
      (let [nums (map #(Integer/parseInt (str/trim %)) (str/split input #","))]
        (keep #(nth items (dec %) nil) nums))
      (let [n (Integer/parseInt (str/trim input))]
        (nth items (dec n) nil)))))

(defn- line->worktree
  "Given a formatted line and the original worktrees, find the matching WorktreeInfo."
  [line worktrees]
  (first (filter #(= (format-worktree-line %) line) worktrees)))

(defn select-worktree
  "Interactive single-select for a worktree. Returns WorktreeInfo or nil.
   Options: :prompt, :exclude-main?"
  [worktrees {:keys [prompt exclude-main?]}]
  (let [filtered (if exclude-main?
                   (filter (complement :main?) worktrees)
                   worktrees)
        lines    (mapv format-worktree-line filtered)]
    (when (seq filtered)
      (let [selected (numbered-select lines {:prompt (or prompt "Select worktree:")})]
        (when selected
          (line->worktree selected filtered))))))

(defn multi-select-worktrees
  "Interactive multi-select for worktrees. Returns seq of WorktreeInfo or nil.
   Always excludes main."
  [worktrees {:keys [prompt]}]
  (let [filtered (filter (complement :main?) worktrees)
        lines    (mapv format-worktree-line filtered)]
    (when (seq filtered)
      (let [selected-lines (numbered-select lines {:prompt (or prompt "Select worktrees:")
                                                   :multi? true})]
        (when (seq selected-lines)
          (filter #(some (fn [l] (= l (format-worktree-line %))) selected-lines)
                  filtered))))))

(defn confirm
  "Prompts user for y/n confirmation. Returns boolean."
  [message default?]
  (print (str message " [" (if default? "Y/n" "y/N") "] "))
  (flush)
  (let [input (str/trim (or (read-line) ""))]
    (case (str/lower-case input)
      ("y" "yes") true
      ("n" "no")  false
      default?)))

(defn handle-dirty-state
  "Prompts user to choose how to handle dirty worktree state.
   Returns :stash, :abort, or :continue."
  [message]
  (println (or message "Your worktree has uncommitted changes."))
  (println)
  (println "How would you like to proceed?")
  (println "  1) Stash changes (will restore after)")
  (println "  2) Abort operation")
  (println "  3) Continue anyway (may cause issues)")
  (print "Enter choice [1]: ")
  (flush)
  (let [input (str/trim (or (read-line) ""))]
    (case input
      "2" :abort
      "3" :continue
      :stash)))

(defn confirm-commands
  "Shows commands and asks for confirmation (unless trust? is true).
   Returns true if should run, false if skipped."
  [commands {:keys [title trust?]}]
  (if trust?
    true
    (do
      (println (or title "The following setup commands will be executed:"))
      (doseq [cmd commands]
        (println (str "  " cmd)))
      (println)
      (confirm "Run these commands?" false))))
