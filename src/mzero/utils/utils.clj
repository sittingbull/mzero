(ns mzero.utils.utils
  (:require [clojure.tools.logging :as log]
            [clojure.spec.alpha :as s]
            [clojure.spec.gen.alpha :as gen]
            [clojure.data.generators :as g]
            [clojure.tools.logging.impl :as li]))

(defmacro with-loglevel
  "Set the logging `level` during the execution of `body`,
  tailored to use of java.util.logging impl"
  [level & body]
  `(let [logger# (li/get-logger log/*logger-factory* "")
         old-level# (.getLevel logger#)]
     (.setLevel logger# ~level)
     (try
       ~@body
       (finally
         (.setLevel logger# old-level#)))))

(defn weighted-rand-nth
  "Pick an element of `coll` randomly according to the
  distribution represented by positive `weights`"
  [coll weights]
  (loop [sum-rest (* (g/double) (apply + weights))
         [item & restc] coll
         [w & restw] weights]
    (if (<= (- sum-rest w) 0) item (recur (- sum-rest w) restc restw))))

(defn reduce-until
  "Like clojure.core/reduce, but stops reduction when `pred` is
  true. `pred` takes one argument, the current reduction value, before
  applying another step of reduce. If `pred` does not become true,
  returns the result of reduce"
  ([pred f coll]
   (reduce #(if (pred %1) (reduced %1) (f %1 %2)) coll))
  ([pred f val coll]
   (reduce #(if (pred %1) (reduced %1) (f %1 %2)) val coll)))

(defn abs [x]
  (if (pos? x) x (- x)))

(defn almost=
  "Compares 2 numbers with a given `precision`, returns true if the
  numbers' difference is lower or equal than the precision

  Precision defaults to a ten thousandth of the first number's value."
  ([a b precision]
   (<= (abs (- a b)) precision))
  ([a b]
   (almost= a b (* (Math/abs a) 0.0001))))

(defmacro timed
  "Returns a vector with 2 values:
  -  time in miliseconds to run the expression, as a
  float--that is, taking into account micro/nanoseconds, subject to
  the underlying platform's precision;
  - expression return value."
  [expr]
  `(let [start-time# (System/nanoTime)
         result# ~expr]
     [(/ (- (System/nanoTime) start-time#) 1000000.0) result#]))

(defn filter-keys
  "Like select-keys, with a predicate on the keys"
  [pred map_]
  (select-keys map_ (filter pred (keys map_))))


(defn filter-vals
  "Like filter-keys, except on vals"
  [pred map_]
  (select-keys map_ (filter #(pred (map_ %)) (keys map_))))

(defn remove-common-beginning
  "Checks if seq1 and seq2 begin with a common subsequence, and returns
  the remainder of seq1--that is, seq1 stripped of the common
  subsequence. Returns a lazy sequence."
  [seq1 seq2]
  (if (or (empty? seq1) (not= (first seq1) (first seq2)))
    seq1
    (recur (rest seq1) (rest seq2))))

(defn map-map
  "Return a map with `f` applied to each of `m`'s keys"
  [f m]
  (reduce-kv (fn [acc k v] (assoc acc k (f v))) {} m))

(defn fn-name
  [fn-var]
  {:pre [(var? fn-var)]}
  (str (:name (meta fn-var))))

(defn with-logs
  "Return a function identical to `fn_`, that logs a message every time
  it is called, whose first part is fn_'s name & call count and second
  part is a custom message computed by `str-fn`"
  ([fn-var str-fn]
   {:pre [(var? fn-var)]}
   (let [call-count (atom 0)]
     (fn [& args]
       (log/info (format "%s : call # %d %s"
                         (fn-name fn-var)
                         @call-count
                         (apply str-fn args)))
       (swap! call-count inc)
       (apply fn-var args))))
  ([fn_]
   (with-logs fn_ (constantly ""))))

(defn with-mapped-gen
  "Like with-gen, but takes a fn to be fed to fmap, and applies it to a
  regular generator for the provided spec"
  [spec fn]
  (s/with-gen spec #(gen/fmap fn (s/gen spec))))

(defn ipmap
  "Similar to pmap, but interruptible--all threads will receive an
  interrupt signal if the main thread is interrupted. However,
  contrarily to pmap, evaluation is eager."
  [f & colls]
  (let [rets (apply map (fn [& vals_] (future (apply f vals_))) colls)]
    (try
      (vec (map deref rets))
      (catch InterruptedException _
        (doall (map future-cancel rets))
        (throw (InterruptedException.))))))

(defn scaffold
  "Show all the interfaces implemented by given `iface`"
  [iface]
  (doseq [[iface methods] (->> iface .getMethods
                            (map #(vector (.getName (.getDeclaringClass %))
                                    (symbol (.getName %))
                                    (count (.getParameterTypes %))))
                            (group-by first))]
    (println (str "  " iface))
    (doseq [[_ name argcount] methods]
      (println
        (str "    "
          (list name (into ['this] (take argcount (repeatedly gensym)))))))))

;; Demonic stuff because I want to use prr without adding a require
(ns clojure.core)
(defn prr
  "Prints v & returns v"
  ([f v]
   (println (str (f v)))
   v)
  ([v]
   (prr identity v)))
