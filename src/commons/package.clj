(ns commons.package
  (:require [babashka.process :as p]))

(defn run-package-manager
  "Runs package manager install in the given directory."
  [install-cmd dir]
  (println (str "Running: " install-cmd " (in " dir ")"))
  (let [result (p/shell {:continue true :dir dir} install-cmd)]
    (when (not (zero? (:exit result)))
      (println (str "Warning: package manager install exited with code " (:exit result))))))
