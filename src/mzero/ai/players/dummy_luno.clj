(ns mzero.ai.players.dummy-luno
  "Player that gets the input senses, turns them into a real-valued
  vector, and uses a dummy 'ANN' on which it makes a 'forward pass',
  using the real-valued result to compute a random movement.

  The network comprises a hidden layer and an output vector.
  The number of units in the hidden layer can be set via
  the `:hidden-layer-size` option

  This implementation relies on the Neanderthal lib."
  (:require [mzero.ai.player :as aip]
            [mzero.game.events :as ge]
            [mzero.game.state :as gs]
            [mzero.game.board :as gb]
            [uncomplicate.neanderthal
             [core :refer [mm entry ncols]]
             [native :refer [dge native-float]]
             [random :as rnd]]))

(def vision-depth 4)
(def default-hidden-layer-size 6)

(defn- get-int-from-decimals
  " Gets an int in [0,100[ by using 2nd & 3rd numbers past
  decimal point of `value`"
  [value]
  (let [get-decimal-part #(- % (int %))]

    (-> (* value 10)
        get-decimal-part
        (* 100)
        int)))

(defn- get-real-valued-senses
  "Turns the board subset visible by the player (senses) from keyword
  matrix to real-valued vector"
  [world vision-depth]
  (->> (aip/get-player-senses world vision-depth)
       ::aip/board-subset
       (reduce into [])
       (map {:wall 1.0 :empty 0.0 :fruit 0.5})
       vec))

(defn- create-hidden-layer
  "`rng`: random number generator"
  [input-size layer-size rng]
  (rnd/rand-uniform! rng (dge input-size layer-size)))

(defn- forward-pass
  [input-vector hidden-layer output-vector]
  (-> input-vector
      (mm hidden-layer)
      (mm output-vector)
      (entry 0 0)))

(defrecord DummyLunoPlayer [hidden-layer-size]
  aip/Player
  (init-player [player opts world]
    (let [edge-length (aip/subset-size vision-depth)
          board-size (-> world ::gs/game-state ::gb/game-board count)
          input-size (Math/pow edge-length 2)
          hl-size (:hidden-layer-size opts default-hidden-layer-size)
          rng (if-let [seed (:seed opts)]
                (rnd/rng-state native-float seed)
                (rnd/rng-state native-float))
          hidden-layer (create-hidden-layer input-size hl-size rng)]
      
      (assert (< edge-length board-size))
      (assoc player
             :hidden-layer hidden-layer
             :rng rng)))
  
  (update-player [player world]
    (let [input-data
          (get-real-valued-senses world vision-depth)

          input-vector
          (dge 1 (count input-data) input-data)
          
          output-vector
          (rnd/rand-uniform! (:rng player)
                             (dge (ncols (-> player :hidden-layer)) 1))]

      ;; next move is selected by getting the 2nd/3rd decimals of
      ;; forward pass to get an int in [0, 100[, then the remainder
      ;; of the division by 4 is the index of the selected direction
      (->> (forward-pass input-vector (-> player :hidden-layer) output-vector)
           get-int-from-decimals
           (#(mod % 4))
           (nth ge/directions)
           (assoc player :next-movement)))))
