(ns lambdaisland.config.cli
  "Config provider for lambdaisland/cli

  Goes well with specifying `:key` on command line flags. The cli dependency is
  BYO, you need to add it to your project dependencies explicitly. Use 0.19.78
  or later for provenance tracking support."
  (:require
   [lambdaisland.config :as config]
   [lambdaisland.cli :as cli]))

(deftype LambdaislandCLIProvider [!opts]
  config/ConfigProvider
  (-value [this k] (get @!opts k))
  (-source [this k] (or
                     (get-in @!opts [::cli/sources k])
                     "command line flag or argument"))
  (-reload [this]))

(defn add-provider
  "Handle lambdaisland/cli command line args for config.

  Update a config to add a provider which looks up values in
  lambdaisland.cli/*opts*, or another var/atom of your choice containing the
  opts map coming from lambdaisland/cli. It will take precedence over other
  providers."
  ([config]
   (add-provider config #'cli/*opts*))
  ([config !opts]
   (reset! (:values config) {})
   (update config :providers
           into
           [(->LambdaislandCLIProvider !opts)])))
