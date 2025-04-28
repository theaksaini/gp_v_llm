;; bowling.clj
;; Peter Kelly, pxkelly@hamilton.edu
;;
;; Problem inspired by: https://www.codewars.com/kata/5531abe4855bcc8d1f00004c/javascript

(ns clojush.problems.psb2size200.bowling
  (:use clojush.pushgp.pushgp
        [clojush pushstate interpreter random util globals]
        clojush.instructions.tag
        [clojure.math numeric-tower])
  (:require [psb2.core :as psb2]))

; If the top item ion the string stack is a single character that is a bowling character,
; return the equivalent integer. Otherwise, noop.
(define-registered
  string_bowling_atoi
  ^{:stack-types [:char :integer]}
  (fn [state]
    (if (empty? (:char state))
      state
      (let [top-char (stack-ref :char 0 state)]
        (if (not (some #{(first (str top-char))} "123456789-X/"))
          state
          (let [int-to-push (cond
                              (= \X top-char) 10
                              (= \/ top-char) 10
                              (= \- top-char) 0
                              true (Integer/parseInt (str top-char)))]
            (pop-item :char
                      (push-item int-to-push :integer state))))))))

; Atom generators
(def atom-generators
  (make-proportional-atom-generators
   (concat
    (registered-for-stacks [:integer :exec :string :char :boolean]) ; stacks
    (list (tag-instruction-erc [:integer :exec :boolean :string :char] 1000) ; tags
          (tagged-instruction-erc 1000)))
   (list 'in1) ; inputs
   (list \-
         \X
         \/
         \1
         \2
         \3
         \4
         \5
         \6
         \7
         \8
         \9
         10 ; constants
         (fn [] (- (lrand-int 20) 10)) ; Integer ERC [-10, 10]
) ; constants
   {:proportion-inputs 0.15
    :proportion-constants 0.05}))

(defn convert-game
  "Takes a bowling input as a vector and converts it to a proper string"
  [vect-game]
  (loop [full-game (map str vect-game) ret-str "" frame-count 0]
    (cond
      (= full-game '()) ret-str ; The vector has been converted to a string
      (= frame-count 2) (recur full-game ret-str 0) ; Reset the count
      (= (first full-game) "10") (recur (rest full-game) (str ret-str "X") 0) ; Special case strike
      (and (> (count full-game) 1)
           (= (+ (Integer/parseInt (first full-game)) (Integer/parseInt (second full-game))) 10)
           (= frame-count 0))
      (recur (drop 2 full-game) (str ret-str (first full-game) "/") 0) ; Special case spare
      (= (first full-game) "0") (recur (rest full-game) (str ret-str "-") (inc frame-count)) ; Special case miss
      :else (recur (rest full-game) (str ret-str (first full-game)) (inc frame-count)))))

; Define test cases
(defn bowling-input
  "Makes an input for bowling. It has to be generated in a very specific way given the many
   cases there are to bowling score cards."
  []
  (loop [frames 0 game []]
    (cond
      (>= frames 10) (convert-game game)   ; The game is generated, so return that
      (= frames 9) (let [score (lrand-int 11)   ; The last frame is very special
                         score2 (lrand-int (- 11 score))
                         score3 (lrand-int 11)
                         score4 (lrand-int 11)]
                     (cond
                       (= score 10) (recur (inc frames) (conj game score score3 score4))  ; Strike gets 2 more bowls
                       (= (+ score score2) 10) (recur (inc frames) (conj game score score2 score3)) ; Spare gets 1 more bowl
                       :else (recur (inc frames) (conj game score score2))))  ; Otherwise, just 2 normal bowls
      :else (let [score (lrand-int 11)    ; otherwise, generate 1 or two numbers
                  score2 (lrand-int (- 11 score))]
              (if (= score 10)
                (recur (inc frames) (conj game score))
                (recur (inc frames) (conj game score score2)))))))

; A list of data domains for the problem. Each domain is a vector containing
; a "set" of inputs and two integers representing how many cases from the set
; should be used as training and testing cases respectively. Each "set" of
; inputs is either a list or a function that, when called, will create a
; random element of the set.
(def data-domains
  [[(list "--------------------" ; All gutter balls
          "XXXXXXXXXXXX" ; All strikes
          "5/5/5/5/5/5/5/5/5/5/5" ; All spares
          "7115XXX548/279-X53" ; Ending with a strike
          "532/4362X179-41447/5"  ; Ending with a spare
          "24815361356212813581"   ; No strikes, no spares
          "------X------------"  ; One strike, nothing else
          "----------3/--------" ; One spare, nothing else
          "--------------1-----"
          "11111111111111111111"
          "111111X111111111111"
          "-4-/-2-/-7-6-/-3-/-4"
          "-/-/-/-/-/-/-/-/-/-/-"
          "X52X52X52X52X52"
          "XXXXX----------"
          "XXXXX81XXX-1"
          "XXXX9/XXX2/XXX"
          "XXXXXXXXXXX9"
          "--X34--------------"
          "------3/61----------"
          "----------XX7-----" ; Other helpful edge cases
          )21 0]
   [(fn [] (bowling-input)) 179 2000]])

; Converts a character into its proper score
(defn char-to-score
  [ch]
  (cond
    (= ch \X) 10
    (= ch \/) 10
    (= ch \-) 0
    true (Integer/parseInt (str ch))))

; Takes a bowling string and converts it to the score
(defn string-to-score
  [string frames]
  (if (zero? frames)
    0
    (let [frame-type (cond
                       (= (first string) \X) :strike
                       (= (second string) \/) :spare
                       true :neither)
          frame-total (case frame-type
                        :neither (+ (char-to-score (first string))
                                    (char-to-score (second string)))
                        :spare (+ 10 (char-to-score (get string 2)))
                        :strike (if (= (get string 2) \/)
                                  20
                                  (+ 10
                                     (char-to-score (second string))
                                     (char-to-score (get string 2)))))
          chars (if (= frame-type :strike)
                  1
                  2)]
      (+ frame-total
         (string-to-score (apply str (drop chars string))
                          (dec frames))))))

; Helper function for error function
(defn create-test-cases
  "Takes a sequence of inputs and gives IO test cases of the form
   [input output]."
  [inputs]
  (map (fn [in]
         (vector in
                 (string-to-score in 10)))
       inputs))

(defn make-error-function-from-cases
  "Creates and returns the error function based on the train/test cases."
  [train-cases test-cases]
  (fn the-actual-error-function
    ([individual]
     (the-actual-error-function individual :train))
    ([individual data-cases] ; data-cases should be :train or :test
     (the-actual-error-function individual data-cases false))
    ([individual data-cases print-outputs]
     (let [behavior (atom '())
           errors (doall
                   (for [[input1 correct-output] (case data-cases
                                                   :train train-cases
                                                   :test test-cases
                                                   data-cases)]
                     (let [final-state (run-push (:program individual)
                                                 (->> (make-push-state)
                                                      (push-item input1 :input)))
                           result (stack-ref :integer 0 final-state)]
                       (when print-outputs
                         (println (format "Correct output: %6d | Program output: %s" correct-output (str result))))
                         ; Record the behavior
                       (swap! behavior conj result)
                         ; Error is integer distance
                       (if (number? result)
                         (abs (- result correct-output)) ; distance from correct integer
                         1000000) ; penalty for no return value
                       )))]
       (if (= data-cases :test)
         (assoc individual :test-errors errors)
         (assoc individual
                :behaviors (reverse @behavior)
                :errors errors))))))

(defn get-train-and-test
  "Returns the train and test cases."
  [data-domains]
  (map sort (map create-test-cases
                 (test-and-train-data-from-domains data-domains))))

; Define train and test cases
;(def train-and-test-cases
;  (get-train-and-test data-domains))

; Need to conver the maops to lists
; for examplel if train-and-test-data is the following;
; (def train-and-test-data {:train '({:input1 "alpha", :output1 56} {:input1 "beta", :output1 5}), 
 ;                         :test '({:input1 "gamma", :output1 569} {:input1 "zeta", :output1 59}) })
; train-and-test-cases should be:
; [[[alpha 56] [beta 5]] [[gamma 569] [zeta 59]]]

(defn problem-specific-maps-to-lists
  "For each entry in vec-of-maps, collects the vals with keys starting with 'input1' and 'output1' and makes a list of these values."
  [vec-of-maps]
  (mapv (fn [m]
          (vector (str (:input1 m)) (:output1 m)))
        vec-of-maps))

(def train-and-test-data "Data taken from https://zenodo.org/record/5084812" (psb2/fetch-examples "data" "bowling" 200 2000))
(println train-and-test-data)

(def train-and-test-cases
  (vector (problem-specific-maps-to-lists (:train train-and-test-data))
          (problem-specific-maps-to-lists (:test train-and-test-data))))

(defn initial-report
  [argmap]
  (println "Train and test cases:")
  (doseq [[i case] (map vector (range) (first train-and-test-cases))]
    (println (format "Train Case: %3d | Input/Output: %s" i (str case))))
  (doseq [[i case] (map vector (range) (second train-and-test-cases))]
    (println (format "Test Case: %3d | Input/Output: %s" i (str case))))
  (println ";;******************************"))

(defn custom-report
  "Custom generational report."
  [best population generation error-function report-simplifications]
  (let [best-test-errors (:test-errors (error-function best :test))
        best-total-test-error (apply +' best-test-errors)]
    (println ";;******************************")
    (printf ";; -*- Bowling problem report - generation %s\n" generation) (flush)
    (println "Test total error for best:" best-total-test-error)
    (println (format "Test mean error for best: %.5f" (double (/ best-total-test-error (count best-test-errors)))))
    (when (zero? (:total-error best))
      (doseq [[i error] (map vector
                             (range)
                             best-test-errors)]
        (println (format "Test Case  %3d | Error: %s" i (str error)))))
    (println ";;------------------------------")
    (println "Outputs of best individual on training cases:")
    (error-function best :train true)
    (println ";;******************************")
    )) ; To do validation, could have this function return an altered best individual
       ; with total-error > 0 if it had error of zero on train but not on validation
       ; set. Would need a third category of data cases, or a defined split of training cases.


; Define the argmap
(def argmap
  {:error-function (make-error-function-from-cases (first train-and-test-cases)
                                                           (second train-and-test-cases))
   :training-cases (first train-and-test-cases)
   :atom-generators atom-generators
   :max-points 2000
   :max-genome-size-in-initial-program 250
   :evalpush-limit 2000
   :population-size 1000
   :max-generations 1200
   :parent-selection :downsampled-lexicase
   :downsample-factor 0.25
   :genetic-operator-probabilities {:uniform-addition-and-deletion 1.0}
   :uniform-addition-and-deletion-rate 0.09
   :problem-specific-report custom-report
   :problem-specific-initial-report initial-report
   :report-simplifications 0
   :final-report-simplifications 5000
   :max-error 1000000})