(ns commons.help
  (:require [clojure.string :as str]))

(defn- opt-flag
  "Returns the flag string for an option, e.g. '--path, -p <value>' or '--checkout, -c'."
  [opt-key {:keys [alias coerce]}]
  (let [long-flag (str "--" (name opt-key))
        short     (when alias (str ", -" (name alias)))
        value     (when (not= coerce :boolean) " <value>")]
    (str long-flag short value)))

(defn- opt-line
  "Returns a single formatted option line including description."
  [flag desc]
  (str "  " flag "  " desc))

(defn- format-opts
  "Formats all options from a spec map into aligned lines, always appending --help/-h."
  [spec]
  (let [entries (concat (seq spec) [[:help {:alias :h :coerce :boolean :desc "Show this help message"}]])
        lines   (map (fn [[k v]]
                       (let [flag (opt-flag k v)
                             desc (or (:desc v) "")]
                         (opt-line flag desc)))
                     entries)]
    (str/join "\n" lines)))

(defn format-cmd-help
  "Formats a complete help string for a dispatch table entry.
   Entry must have :cmds, :desc, and optionally :args->opts and :spec."
  [{:keys [cmds desc args->opts spec]}]
  (let [cmd-str  (str/join " " cmds)
        args-str (when (seq args->opts)
                   (str " " (str/join " " (map #(str "<" (name %) ">") args->opts))))
        usage    (str "Usage: worktree " cmd-str args-str " [options]")
        opts     (format-opts (or spec {}))]
    (str/join "\n\n" (filter some? [usage desc (str "Options:\n" opts)]))))
