;; mastermind.clj
;; Peter Kelly, pxkelly@hamilton.edu
;;
;; Problem inspired by our 200-level program languages course

(ns clojush.problems.psb2size200.mastermind
  (:use clojush.pushgp.pushgp
        [clojush pushstate interpreter random util globals]
        clojush.instructions.tag
        [clojure.math numeric-tower])
  (:require [psb2.core :as psb2]))

(define-registered
  output_integer1
  ^{:stack-types [:integer]}
  (fn [state]
    (if (empty? (:integer state))
      state
      (let [top-int (top-item :integer state)]
        (stack-assoc top-int :output 0 state)))))

(define-registered
  output_integer2
  ^{:stack-types [:integer]}
  (fn [state]
    (if (empty? (:integer state))
      state
      (let [top-int (top-item :integer state)]
        (stack-assoc top-int :output 1 state)))))

; Atom generators
(def atom-generators
  (make-proportional-atom-generators
   (concat
    (registered-for-stacks [:integer :exec :boolean :string :char]) ; stacks
    (list (tag-instruction-erc [:integer :exec :boolean :string :char] 1000) ; tags
          (tagged-instruction-erc 1000)))
   (list 'in1 'in2) ; inputs
   (list 0
         1
         \B
         \R
         \W
         \Y
         \O
         \G) ; constants
   {:proportion-inputs 0.15
    :proportion-constants 0.05}))

(defn mastermind-one-string
  "Makes a 4-char mastermind string"
  []
  (apply str (repeatedly 4
                         #(rand-nth "BRWYOG"))))

; Define test cases
(defn mastermind-input
  "Makes a mastermind input, which is two 4-character strings made from the letters B, R,
   W, Y, O, or G."
  []
  (vec (repeatedly 2 mastermind-one-string)))

; A list of data domains for the problem. Each domain is a vector containing
; a "set" of inputs and two integers representing how many cases from the set
; should be used as training and testing cases respectively. Each "set" of
; inputs is either a list or a function that, when called, will create a
; random element of the set.
(def data-domains
  [[(list ["RRRR" "RRRR"]
          ["BOYG" "GYOB"]
          ["WYYW" "BBOG"]
          ["GGGB" "BGGG"]
          ["BBBB" "OOOO"]
          ["BWYG" "YWBG"]
          ["RGOW" "OGWR"]
          ["YGGB" "GYGB"]
          ["YGGB" "GYBG"]
          ["GOGY" "OGGO"]
          ["GOGR" "GOYR"]
          ["YMOO" "YMRG"]
          ["GROY" "BGOW"]
          ["GGYG" "BYBB"]
          ["WWWW" "BYWR"]
          ["RBYO" "BWBB"]
          ["RBRB" "ORBY"]
          ["WORR" "BYOW"]
          ["YOWW" "YWWR"]
          ["BRYB" "WOGG"]) 20 0] ; "Special" inputs covering some base cases
   [(fn [] (let [s (mastermind-one-string)]
             [s s])) 10 200] ; All the same string
   [(fn [] (mastermind-input)) 170 1800]])

(defn remove-matches
  [code guess]
  (loop [check-code code check-guess guess matchless-code "" matchless-guess ""]
    (cond
      (= check-code "") (vector matchless-code matchless-guess)
      (= (first check-code) (first check-guess))
      (recur (subs check-code 1) (subs check-guess 1) matchless-code matchless-guess)
      :else (recur (subs check-code 1) (subs check-guess 1) (str matchless-code (first check-code)) (str matchless-guess (first check-guess))))))

; Helper function for error function
(defn create-test-cases
  "Takes a sequence of inputs and gives IO test cases of the form
   [[input1 input2] [output1 output2]]."
  [inputs]
  (map (fn [[in1 in2]]
         (vector [in1 in2]
                 (let [[matchless-code matchless-guess] (remove-matches in1 in2)
                       code-frequencies (frequencies matchless-code)
                       guess-frequencies (frequencies matchless-guess)
                       white-pegs (reduce (fn [count [element frequency]] (+ count (min frequency (get guess-frequencies element 0)))) 0 code-frequencies)]
                   (vector white-pegs (- 4 (count matchless-code))))))
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
           errors (flatten
                   (doall
                    (for [[[input1 input2] [correct-output1 correct-output2]]
                          (case data-cases
                            :train train-cases
                            :test test-cases
                            data-cases)]
                      (let [final-state (run-push (:program individual)
                                                  (->> (make-push-state)
                                                       (push-item :no-output :output)
                                                       (push-item :no-output :output)
                                                       (push-item input1 :input)
                                                       (push-item input2 :input)))
                            result1 (stack-ref :output 0 final-state)
                            result2 (stack-ref :output 1 final-state)]
                        (when print-outputs
                          (println (format "Correct output: %s %s | Program output: %s %s" (str correct-output1) (str correct-output2)
                                           (str result1) (str result2))))
                         ; Record the behavior
                        (swap! behavior conj result1 result2)
                         ; Error is integer distance for both outputs
                        (vector
                         (if (number? result1)
                           (abs (- result1 correct-output1)) ; distance from correct integer
                           1000000) ; penalty for no return value
                         (if (number? result2)
                           (abs (- result2 correct-output2)) ; distance from correct integer
                           1000000) ; penalty for no return value
                         )))))]
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
(def train-and-test-cases
  (get-train-and-test data-domains))


(defn problem-specific-maps-to-lists
  "For each entry in vec-of-maps, collects all vals with keys starting with 'input1', 'input2', etc. into a list.
   Then make a list of that list paired with the val of the key 'output1'."
  [vec-of-maps]
  (map (fn [m]
         (let [inputs (->> m
                           (filter (fn [[k _]] (re-matches #"input\d+" (name k))))
                           (sort-by (fn [[k _]] (Integer/parseInt (subs (name k) 5))))
                           (map second)
                           vec)
               outputs (->> m
                            (filter (fn [[k _]] (re-matches #"output\d+" (name k))))
                            (sort-by (fn [[k _]] (Integer/parseInt (subs (name k) 6))))
                            (map second)
                            vec)]
           [inputs outputs]))
       vec-of-maps))

(def train-and-test-data "Data taken from https://zenodo.org/record/5084812" (psb2/fetch-examples "data" "mastermind" 200 2000))

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
    (printf ";; -*- Mastermind problem report - generation %s\n" generation) (flush)
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