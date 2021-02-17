(ns mzero.ai.player-test
  (:require [mzero.ai.player :as sut]
            [clojure.test :refer [is testing]]
            [mzero.ai.world :as aiw]
            [mzero.game.state :as gs]
            [mzero.utils.testing :refer [check-spec deftest]]
            [mzero.game.state-test :as gst]))

(check-spec `sut/get-player-senses
            {:clojure.spec.test.check/opts {:num-tests 100}})

(deftest get-board-subset-test
  
  (let [world-state (aiw/get-initial-world-state gst/test-state-2)
        world-state-2 (aiw/get-initial-world-state
                       (assoc gst/test-state-2 ::gs/player-position [0 4]))]
    (is (= (::sut/board-subset (sut/get-player-senses world-state 1))
         [[:empty :wall :empty]
          [:fruit :empty :empty]
          [:empty :wall :empty]]))
    (is (= (::sut/board-subset (sut/get-player-senses world-state-2 1))
           [[:empty :empty :empty]
            [:empty :empty :empty]
            [:empty :empty :empty]]))))

(deftest seed-player-test
  (let [world-state (aiw/get-initial-world-state gst/test-state-2)]
    (is (= (type (-> (sut/load-player "random" nil world-state) :rng)) java.util.Random))
    (testing "Without seed, randomly seeded"
      (let [p1 (sut/load-player "random" nil world-state)
            p2 (sut/load-player "random" nil world-state)]
        (is (not= (.nextInt (-> p1 :rng)) (.nextInt (-> p2 :rng))))))
    (testing "With seed, properly seeded"
      (let [p1 (sut/load-player "random" {:seed 39} world-state)
            p2 (sut/load-player "random" {:seed 39} world-state)
            p3 (sut/load-player "random" {:seed 40} world-state)]
        (is (= (.nextInt (-> p1 :rng)) (.nextInt (-> p2 :rng))))
        (is (not= (.nextInt (-> p1 :rng)) (.nextInt (-> p3 :rng))))))))
