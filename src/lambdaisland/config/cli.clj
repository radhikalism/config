(ns lambdaisland.config.cli
  "Config provider for lambdaisland/cli

  Goes well with specifying `:key` on command line flags."
  (:require
   [lambdaisland.config :as config]
   [lambdaisland.cli :as cli]))

(defn add-provider
  "Update a config to add a provider which looks up values in lambdaisland.cli/*opts*."
  [config]
  (reset! (:values config) {})
  (update config :providers
          conj (config/->DerefMapProvider #'cli/*opts*)))
