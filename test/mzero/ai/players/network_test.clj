(ns mzero.ai.players.network-test
  (:require [mzero.ai.players.network :as sut]
            [mzero.utils.testing :refer [check-spec deftest]]))

(check-spec `sut/new-layers
            {:clojure.spec.test.check/opts {:num-tests 100}})
