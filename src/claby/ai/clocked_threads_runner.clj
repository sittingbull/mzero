(ns claby.ai.clocked-threads-runner
  "Game runner with clocked threads, in which the world and the player
  update regularly at predetermined frequencies,
  `player-step-duration` and `game-step-duration`.

  It adds 2 fields to `world-state`, the number of `missteps` and the
  time to wait before performing a new update of the world (see below)."
  (:require [claby.ai.world :as aiw]
            [claby.ai.player :as aip]
            [claby.ai.game-runner :refer [GameRunner]]
            [clojure.spec.alpha :as s]))

;; Time interval (ms) between each game step
(def max-game-step-duration 1000)

(s/def ::game-step-duration (s/int-in 1 max-game-step-duration))

;; time to wait = game step duration - step execution time (obtained
;; by comparing step timestamps before and after running the step) or
;; 0 if step execution time exceeded game step duration
(s/def ::time-to-wait (s/int-in 0 max-game-step-duration))

;; number of steps that were not performed fast enough (i.e. took more
;; than game-step-duration
(s/def ::missteps nat-int?)

(defn update-timing-data
  [world new-timestamp game-step-duration]
  (let [step-execution-time (- new-timestamp (-> world ::aiw/step-timestamp))
        time-to-wait (max (- game-step-duration step-execution-time) 0)]
    (-> world
        (assoc ::time-to-wait time-to-wait)
        (update ::missteps #(if (zero? time-to-wait) (inc %) %)))))

(defn run-timed-step
  "Runs a step with synchronized timing management, as follows:
  - first compare time since last step's beginning with game step duration;
  - if bigger, count it as a misstep;
  - wait for the remaining amount of time (0 if time was bigger);
  - run the step as usual"
  [world-state-atom {:as game-options, :keys [game-step-duration logging-steps]}]
  (swap! world-state-atom update-timing-data
         (System/currentTimeMillis) game-step-duration)
  (Thread/sleep (@world-state-atom ::time-to-wait 0))
  (aiw/run-step world-state-atom logging-steps))

(defn- run-world
  [world-state game-options]
  (while (aiw/active? @world-state)
    (run-timed-step world-state game-options))
  @world-state)

(defn- run-player
  [world-state player-state player-step-duration]
  (while (aiw/active? @world-state)
    (Thread/sleep player-step-duration)
    (aip/request-movement player-state world-state)))

(defrecord ClockedThreadsRunner [world-state player-state opts]
  GameRunner
  (run-game
    [{:keys [world-state player-state]
      {:keys [game-step-duration player-step-duration]} opts}]
    {:pre [(s/valid? ::game-step-duration game-step-duration)
           (s/valid? ::game-step-duration player-step-duration)]}

    ;; initialize missteps for world state
    (swap! world-state assoc ::missteps 0)
    
    ;; start clocked threads
    (let [game-result
          (future (run-world world-state opts))
          player-result
          (future (run-player world-state player-state (opts :player-step-duration)))]
      
       ;; checks that player does not stop running during game
       ;; execution, then return result
       (while (not (realized? game-result))
         (when (realized? player-result) @player-result))
       (shutdown-agents)
       @game-result)))