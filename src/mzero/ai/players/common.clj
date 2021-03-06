(ns mzero.ai.players.common
  "Common helper functions mostly for working with neanderthal."
  (:require [uncomplicate.neanderthal.native :as nn]
            [uncomplicate.neanderthal.core :as nc]
            [mzero.utils.utils :as u]
            [clojure.spec.alpha :as s]))

(def max-dimension (* 8 1024))
(def ones-vector (nn/fv (repeat max-dimension 1.0)))
(def zeros-vector (nn/fv max-dimension))
(def zeros-matrix (nn/fge max-dimension max-dimension))

(defn ones
  "Return a vector of `size` ones (double).
  A subvector of `ones-vector` is used, for performance.
  It should not be altered (use copy if needed)"
  [size]
  (nc/subvector ones-vector 0 size))

(defn zeros
  "Similar to `ones`"
  [size]
  (nc/subvector zeros-vector 0 size))

(defn zeros-matr
  "Similar to `ones` and `zeros`, with 2 dims"
  [m n]
  (nc/submatrix zeros-matrix m n))

(defn vect=
  [v1 v2]
  (every? true? (map u/almost= v1 v2)))

(defn matrix=
  [m1 m2]
  (every? true? (map vect= (nc/rows m1) (nc/rows m2))))

(defn per-element-spec
  [spec]
  (fn [vect-or-matr]
    (cond
      (nc/vctr? vect-or-matr)
      (every? #(s/valid? spec %) vect-or-matr)

      (nc/matrix? vect-or-matr)
      (every? (fn [row] (every? #(s/valid? spec %) row)) vect-or-matr))))

(defn values-in?
  [coll minv maxv]
  (s/valid? (per-element-spec (s/and number? #(<= minv % maxv))) coll))
