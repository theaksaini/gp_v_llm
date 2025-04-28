;; spin-words.clj
;; Peter Kelly, pxkelly@hamilton.edu
;;
;; Problem inspired by: https://www.codewars.com/kata/5264d2b162488dc400000001

(ns clojush.problems.psb2size50.spin-words
  (:use clojush.pushgp.pushgp
        [clojush pushstate interpreter random util globals]
        clojush.instructions.tag
        clojure.math.numeric-tower)
  (:require [clojure.string :as str]
            [psb2.core :as psb2]))

(defn word-generator
  "Generates words at a nice distribution for Spin Words
   80% of the time word will have length in range [1, 8].
   20% of the time it will have length in range [1, 16]"
  []
  (let [chars-between #(map char (range (int %1) (inc (int %2))))
        chars (chars-between \a \z)
        word-len (inc (rand-int (if (< (rand) 0.8)
                                  8
                                  16)))]
    (apply str (repeatedly word-len #(rand-nth chars)))))

(defn spin-words-input
  "Makes a Spin Words input of length len, which is just a string of words, where the
   words that are length 5 or greater are reversed"
  [len]
  (let [words (apply str
                     (take len           ; This looks weird because str isn't lazy, so you
                           (apply str    ; need to take len twice here.
                                  (take len
                                        (interpose " " (repeatedly word-generator))))))]
    (if (not= (last words) \space)
      words
      (apply str (butlast words)))))

; Atom generators
(def atom-generators
  (make-proportional-atom-generators
   (concat
    (registered-for-stacks [:integer :boolean :string :char :exec]) ; stacks
    (list (tag-instruction-erc [:integer :boolean :string :char :exec] 1000) ; tags
          (tagged-instruction-erc 1000)))
   (list 'in1) ; inputs
   (list 4
         5
         \space ; constants
         (fn [] (lrand-nth (map char (range 97 122)))) ; visible character ERC
         (fn [] (spin-words-input (lrand-int 21)))) ; string ERC
   {:proportion-inputs 0.15
    :proportion-constants 0.05}))

; A list of data domains for the problem. Each domain is a vector containing
; a "set" of inputs and two integers representing how many cases from the set
; should be used as training and testing cases respectively. Each "set" of
; inputs is either a list or a function that, when called, will create a
; random element of the set.
(def data-domains
  [[(list ""
          "a"
          "this is a test"
          "this is another test"
          "hi"
          "cat"
          "walk"
          "jazz"
          "llama"
          "heart"
          "pantry"
          "helpful"
          "disrespectful"
          "stop spinning these"
          "couple longer words"
          "oneloongworrrrrrrrrd"
          "a b c d e f g h i j"
          "ab cd ef gh ij kl mn"
          "abc def gef hij klm"
          "word less than five"
          "abcde fghij klmno"
          "abcdef ghijkl mnopqr"
          "abcdefg hijklmn"
          "abcdefgh ijklmnop"
          "abcdefghi jklmnopqrs"
          "on pineapple island"
          "maybe this isgood"
          "racecar palindrome"
          "ella is a short pali"
          "science hi") 30 0] ; "Special" inputs covering some base cases
   [(fn [] (spin-words-input (inc (lrand-int 20)))) 170 2000]])

; Helper function for error function
(defn create-test-cases
  "Takes a sequence of inputs and gives IO test cases of the form
   [input output]."
  [inputs]
  (map (fn [in]
         (vector in
                 (str/join " " (map #(if (>= (count %) 5) (apply str (reverse %)) %) (str/split in #" ")))))
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
                   (for [[input correct-output] (case data-cases
                                                  :train train-cases
                                                  :test test-cases
                                                  data-cases)]
                     (let [final-state (run-push (:program individual)
                                                 (->> (make-push-state)
                                                      (push-item input :input)))
                           result (stack-ref :string 0 final-state)]
                       (when print-outputs
                         (println (format "\n| Correct output: %s\n| Program output: %s" (str correct-output) (str result))))
                         ; Record the behavior
                       (swap! behavior conj result)
                         ; Error is Levenshtein distance
                       (if (string? result)
                         (levenshtein-distance correct-output (str result))
                         10000) ; penalty for no return value
                       )))]
       (if (= data-cases :test)
         (assoc individual :test-errors errors)
         (assoc individual
                :behaviors (reverse @behavior)
                :errors errors))))))

(defn get-train-and-test
  "Returns the train and test cases."
  [data-domains]
  (map create-test-cases
       (test-and-train-data-from-domains data-domains)))

; Define train and test cases
;(def train-and-test-cases
;  (get-train-and-test data-domains))

(defn problem-specific-maps-to-lists
  "For each entry in vec-of-maps, collects the vals with keys starting with 'input1' and 'output1' and makes a list of these values."
  [vec-of-maps]
  (mapv (fn [m]
          (vector (:input1 m) (:output1 m)))
        vec-of-maps))

(def train-and-test-data "Data taken from https://zenodo.org/record/5084812" (psb2/fetch-examples "data" "spin-words" 50 2000))

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
    (printf ";; -*- Spin Words problem report - generation %s\n" generation) (flush)
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
   :max-error 10000})