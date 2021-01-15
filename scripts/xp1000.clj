(ns xp1000
  "Run 1000 times games with 2 types of player and compares the results,
  giving the good stats."
  (:require [mzero.ai.main :as aim]
            [mzero.ai.world :as aiw]
            [mzero.game.generation :as gg]
            [mzero.utils.utils :as u]
            [mzero.utils.testing :refer [count-calls]]
            [mzero.ai.players.tree-exploration :as te]
            [mzero.game.events :as ge]
            [mzero.game.state :as gs]
            [mzero.ai.player :as aip]
            [mzero.game.board :as gb]
            [clojure.string :as str]
            [mzero.utils.xp :refer [measure display-measures]]))


(defn -step-avg-xp [nb-xps player-type]
  (let [game-args (str  "-s 20 -v WARNING -t " player-type)]
    (display-measures
     (measure #(aim/go game-args)
              (comp ::aiw/game-step :world)
              (repeat (Integer/parseInt nb-xps) []))
        game-args)))

(defn -fastest-te-impl
  "A subsim is ~ an atomic operation in the tree exploration,
  somewhat similar to one random move. The speed we wish to measure is
  subsim/sec"
  [board-size constr nb-xps]
  (let [game-args
        (format "-n 10 -o '{:node-constructor %s :nb-sims 100}'" constr)
        timed-go
        (fn [world]
          (with-redefs [ge/move-player (count-calls ge/move-player)]
            (-> (aim/go (str "-v WARNING -t tree-exploration " game-args) world)
                u/timed
                (conj ((:call-count (meta ge/move-player)))))))
        measure-fn
        #(vector (/ (last %) (/ (first %) 1000))
                 (last %))        
        random-worlds ;; seeded generation of game states, always same list
        (map (comp list aiw/get-initial-world-state)
             (gg/generate-game-states nb-xps board-size 41))]
    
    (display-measures (measure timed-go measure-fn random-worlds map)
                      game-args
                      "Tree exploration op/s")))

(defn -compare-sht
  "For random, an op is just the number of steps played"
  [board-size nb-xps]
  (doseq [player-type ["exhaustive" "random" "tree-exploration"]]
    (let [timed-go
          #(u/timed (aim/go (str "-v WARNING -l 200000 -t " player-type) %))
          measure-fn 
          #(vector (first %)
                   (-> % second :world ::aiw/game-step))
          random-worlds
          (map (comp list aiw/get-initial-world-state)
               (gg/generate-game-states nb-xps board-size 41 true))]
      (display-measures (measure timed-go measure-fn random-worlds map)
                        player-type
                        "1.Time 2.Steps /"))))

(defn -compare-sht2
  "For random, an op is just the number of steps played"
  [board-size nb-xps node-type & tunings]
  (prn "XP: " (str/join "/" (into [board-size nb-xps node-type] tunings)))
  (let [player-opts
        (reduce #(assoc-in %1 [:tuning (keyword %2)] true)
                {:node-constructor node-type
                 :seed 42
                 :tuning {}}
                tunings)
        option-string
        (format "-v WARNING -t tree-exploration -o '%s'" player-opts)
        measure-fn 
        #(vector (-> % second :world ::aiw/game-step))
        timed-go
        (fn [world]
          (-> (u/timed (aim/go option-string world))
              (#(or (prn (measure-fn %)) %))))
        random-worlds
        (map (comp list aiw/get-initial-world-state)
             (gg/generate-game-states nb-xps board-size 41 true))
        measures
        (measure timed-go measure-fn random-worlds map)
        timing
        (first (u/timed (display-measures measures "Steps")))]
    (prn "Time: " timing " / Average per game: " (/ timing nb-xps))))

(defn -xp-
  "Experiment with 3 node types for tree-exploration: te-node, dag-node,
  java-dag-node. For each, tries no tuning, random-min tuning,
  wall-fix, and both."
  [board-size nb-xps node-type & tunings]
  ())

(defn -runcli [& args]
  (apply (resolve (symbol (str "xp1000/" (first args))))
         (map read-string (rest args))))

(defn run-until-score [score tuning-opts]
  (let [world
        (aiw/get-initial-world-state
         (first (gg/generate-game-states 1 26 41 true)))
        player
        (aip/init-player (te/map->TreeExplorationPlayer {})
                         {:nb-sims 100
                          :node-constructor "tree-exploration/te-node"
                          :seed 42
                          :tuning tuning-opts}
                         (-> world ::gs/game-state))]
    (aim/gon "-v WARNING -t tree-exploration -n 1" world player)
    (while (< (-> (aim/n) :world ::gs/game-state ::gs/score) score))
    (-> @aim/curr-game :world aiw/data->string)))

(def node-path (atom []))

(defn run-simulation []
  (reset! node-path [])
  (let [state (-> @aim/curr-game :world ::gs/game-state)
        copied-state (atom state)
        
        tree-sim te/tree-simulate]
    (with-redefs [gs/cell->char
                  (assoc gs/cell->char :path \+)
                  te/tree-simulate
                  (fn [tn gs ss]
                    (swap! copied-state
                           assoc-in
                           (into [::gb/game-board] (-> gs ::gs/player-position))
                           :path)
                    (swap! node-path conj
                           (let [updated (#'te/update-children tn)]
                             (-> updated
                                 te/node-path
                                 (assoc :d (te/min-direction updated te/frequency)))))
                    (tree-sim tn gs ss))]
      (swap! aim/curr-game
             update-in [:player :root-node]
             te/tree-simulate state (#'te/max-sim-size state))
      (println (gs/state->string @copied-state)))))

(defn job [score]
  [(println (run-until-score score {}))
   (println (run-until-score score {:wall-fix true}))]
  (swap! aim/curr-game assoc-in [:player :root-node] (te/te-node nil)))
