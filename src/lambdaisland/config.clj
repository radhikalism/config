(ns lambdaisland.config
  (:refer-clojure :exclude [get])
  (:require
   [aero.core :as aero]
   [clojure.core :as c]
   [clojure.java.io :as io]
   [clojure.string :as str]
   [lambdaisland.data-printers :as printers]))

(defn- env-case [s]
  (-> s str/upper-case (str/replace #"[-/]" "_")))

(defn key->env-var
  "Take the key used to identify a setting or secret, and turn it into a string
  suitable for use as an environment variable.

  - if the key is already a string it is left untouched
  - otherwise it is assumed to be an ident (symbol or keyword)
  - identifiers are uppercased and munged, as per [[munge]]
  - dashes become underscores
  - if the ident is qualified (has a namespace), two underscores are used to
    separate name and namespace"
  [prefix k]
  (str
   (when prefix
     (str (env-case prefix) "__"))
   (if (string? k)
     k
     (str (when (qualified-ident? k)
            (str (str/upper-case (munge (namespace k)))
                 "__"))
          (str/upper-case (munge (name k)))))))

(defn env-key
  "The current environment name, as a keyword, for instance `:dev`, `:prod`, or `:test`

  Checked in order
  - `:env` explicitly passed in
  - `PREFIX_ENV` env var, based on the configured `:prefix`, uppercased
  - `prefix.env` Java system property (use Java CLI flag `-D`, e.g. `-Dprefix.env=prod`)
  - if env var `CI=true` then `:test` (convention used by most CI providers)
  - otherwise: `:dev`"
  [opts]
  (or
   (:env opts)
   (some-> (:prefix opts) (str/replace #"/" "_") str/upper-case (str "_ENV") System/getenv keyword)
   (some-> (:prefix opts) (str/replace #"/" ".") (str ".env") System/getProperty keyword)
   (when (= "true" (System/getenv "CI")) :test)
   :dev))

(defprotocol ConfigProvider
  (-value [this k])
  (-source [this k])
  (-reload [this]))

(defn ensure-aero [path cache opts]
  (when-not @cache
    (reset! cache (aero/read-config path opts))))

(deftype AeroProvider [path opts cache]
  ConfigProvider
  (-value [this k]
    (when-not @cache
      (reset! cache (aero/read-config path opts)))
    (c/get @cache k))
  (-source [this k]
    (cond
      (instance? java.io.File path)
      (.getCanonicalPath ^java.io.File path)
      (string? path)
      (.getCanonicalPath ^java.io.File (io/file path))
      :else
      path))
  (-reload [this]
    (reset! cache (aero/read-config path opts))))

(deftype EnvProvider [prefix]
  ConfigProvider
  (-value [this k] (System/getenv (key->env-var prefix k)))
  (-source [this k] (str "$" (key->env-var prefix k) " environment variable"))
  (-reload [this]))

(defn- property-key [prefix k]
  (str (when prefix
         (str prefix "."))
       (str/replace (subs (str k) 1) #"/" ".")))

(deftype PropertiesProvider [prefix]
  ConfigProvider
  (-value [this k] (System/getProperty (property-key prefix k)))
  (-source [this k] (str (property-key prefix k) " java system property"))
  (-reload [this]))

(deftype MapProvider [m]
  ConfigProvider
  (-value [this k] (c/get m k))
  (-source [this k] (str k " map lookup"))
  (-reload [this]))

(deftype DerefMapProvider [m]
  ConfigProvider
  (-value [this k] (c/get @m k))
  (-source [this k] (str k " deref map lookup"))
  (-reload [this]))

(doseq [c [AeroProvider
           EnvProvider
           PropertiesProvider
           MapProvider
           DerefMapProvider]]
  (printers/register-print c (.getName c) meta)
  (printers/register-pprint c (.getName c) meta))

(defn new-config [env providers]
  {:env env
   :providers (remove nil? providers)
   :values (atom {})})

(defn create [{:keys [prefix env-vars java-system-props local-config xdg-config
                      prefix-env prefix-props]
               :as   opts
               :or   {env-vars          true
                      java-system-props true
                      local-config      true
                      xdg-config        true
                      prefix-env        true
                      prefix-props      true}}]
  (let [env          (env-key opts)
        config-edn   (io/resource (str prefix "/config.edn"))
        env-edn      (io/resource (str prefix "/" (name env) ".edn"))
        config-local (io/file "config.local.edn")
        aero-opts    {:profile env}
        xdg-path     (io/file
                      (or (System/getenv "XDG_CONFIG_HOME")
                          (io/file (System/getProperty "user.home") ".config"))
                      (str prefix ".edn"))]
    (new-config
     env
     [(when env-vars
        (->EnvProvider (when prefix-env prefix)))
      (when (and local-config (.exists config-local))
        (->AeroProvider config-local aero-opts (atom nil)))
      (when (and xdg-config (.exists xdg-path))
        (->AeroProvider xdg-path aero-opts (atom nil)))
      (when java-system-props
        (->PropertiesProvider (when prefix-props prefix)))
      (when env-edn
        (->AeroProvider env-edn aero-opts (atom nil)))
      (when config-edn
        (->AeroProvider config-edn aero-opts (atom nil)))])))

(defn get-entry [{:keys [providers values] :as config} k]
  (let [values (swap! values
                      (fn [m]
                        (if (contains? m k)
                          m
                          (reduce
                           (fn [m p]
                             (if-let [v (-value p k)]
                               (reduced (assoc m k {:val v
                                                    :provider p
                                                    :source (-source p k)}))
                               m))
                           m
                           providers))))]
    (c/get values k)))

(defn get [config k]
  (:val (get-entry config k)))

(defn source [config k]
  (:source (get-entry config k)))

(defn entries [config]
  @(:values config))

(defn values [config]
  (update-vals (entries config) :val))

(defn sources [config]
  (update-vals (entries config) :source))

(defn reload! [config]
  (run! -reload (:providers config))
  (reset! (:values config) {}))
