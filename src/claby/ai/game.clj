(ns claby.ai.game
  "Game thread with main game loop, responsible for starting/ending
  the game, updating game state, providing player senses, executing
  player and enemies movement requests.

  The game runs in discretized time, with a specified `game-step-duration`.
  At every `game-step`, the game checks whether movements are requested via the
  `requested-movements`, executes them and updates the `game-state`.

  The game makes no explicit attempt to synchronize movement requests
  reading and execution, meaning the player may see that a movement
  request has been read, but the player senses may not have been
  updated.

  However, an execution that takes longer than the duration of a step
  will be considered an exception."
  
  (:require [clojure.spec.alpha :as s]
            [clojure.spec.gen.alpha :as gen]
            [claby.game.state :as gs]
            [claby.game.events :as ge]
            [claby.game.generation :as gg]
            [claby.utils :as u]))

;;; Game data spec & function
;;;;;;;

(s/def ::game-step int?)

(s/def ::requested-movements (s/map-of ::ge/being ::ge/direction))

(s/def ::game-data
  (-> (s/keys :req [::gs/game-state ::game-step ::requested-movements])
      
      (s/and (fn [{:keys [::requested-movements]
                   {:keys [::gs/enemy-positions]} ::gs/game-state}]
               (comment "for all movements,  enemy index < enemy count")
               (every? #(or (= % :player) (< % (count enemy-positions)))
                       (keys requested-movements))))))
                       

(defn create-game-with-state [game-state]
  {::gs/game-state game-state
   ::game-step 0
   ::requested-movements {}})


(defn data->string
  "Converts game data to nice string"
  [{:keys [::gs/game-state ::game-step], :as game-data}]
  (str (format "Step %d\n" game-step)
       (gs/state->string game-state)))

;;; Running the game
;;;;;;

#_(add-watch game-data
           :display-data-after-change
           (fn [_ _ old-data new-data]
             (when-not (empty? (::requested-movements old-data))
               (println (data->string new-data)))))

(s/fdef run-step
  :args (-> (s/cat :game-data ::game-data)
            (s/and #(= (-> % :game-data ::gs/game-state ::gs/status) :active)))
  :ret ::game-data
  :fn (s/and
       (fn [{{:keys [game-data]} :args, :keys [ret]}]
         (comment "Step increases")
         (= (::game-step ret) (inc (::game-step game-data))))
       (fn [{:keys [:ret]}]
         (comment "Movements cleared")
         (empty? (::requested-movements ret)))))

(defn run-step
  "Runs a step of the game : execute movements, clear requested-movements,
  increase step."
  [{:as game-data, :keys [::requested-movements]}]
  (-> game-data
      
      ;; execute movements, stop if ever game ends
      (update 
       ::gs/game-state
       (partial u/reduce-until #(not= (::gs/status %) :active) ge/move-being)
       requested-movements)
      
      (assoc ::requested-movements {})
      (update ::game-step inc)))

(defn active?
  [game-data]
  (= :active (-> game-data ::gs/game-state ::gs/status)))

(defn run-game
  "Main game loop"
  [game-data-atom game-step-time]
  (println "The game begins.")
  (while (active? @game-data-atom)
    (Thread/sleep game-step-time)
    (swap! game-data-atom run-step))
  @game-data-atom)
