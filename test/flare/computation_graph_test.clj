(ns flare.computation-graph-test
  (:refer-clojure :exclude [+ * concat])
  (:require [clojure.test :refer :all]
            [flare.computation-graph :refer :all]
            [flare.core :as flare]
            [flare.node :as node]
            [flare.model :as model]
            [flare.neanderthal-ops :as no]))

(flare/set! {:factory (no/factory)})

(defn get-node [name shape]
  (let [t (flare/zeros shape)]
    (node/const name t)))

(deftest scope-test
  (testing "nested scope"
    (node/with-scope :affine
      (node/with-scope :logistic
        (let [Y (get-node "Y" [10])]
          (are [x y] (= x y)
            "affine/logistic/Y" (:ref-name Y)))))))

(deftest arithcmetic-test
  (testing "simple addition"
    (let [Y (get-node "Y" [1 10])
          X (get-node "X" [1 10])
          L (get-node "L" [1 5])
          Z (+ X Y)]
      (are [k v] (= (get Z k) v)
        :shape [1 10]
        :graph-op (->SumGraphOp)
        :children [X Y])
      (is (thrown? RuntimeException (+ X L)))))
  (testing "simple multiplication"
    (let [Y (get-node "Y" [1 10])
          X (get-node "X" [10 1])
          Z (* X Y)
          Z-rev (* Y X)]
      (is (thrown? RuntimeException (* Z Y)))
      (are [k v] (= (get Z k) v)
        :shape [10 10]
        :graph-op (->MultGraphOp)
        :children [X Y])
      (are [k v] (= (get Z-rev k) v)
        :shape [1 1]
        :graph-op (->MultGraphOp)
        :children [Y X]))))

(deftest hadamard-test
  (testing "hadamard"
    (let [X (get-node "X" [2])
          Y (get-node "Y" [2])]
      (is (= [2] (:shape (hadamard X Y))))
      (is (thrown? RuntimeException (hadamard X (get-node [1 2])))))))

(deftest logistic-regression-test
  (testing "create logistic regression graph (make parameters inputs)"
    (let [num-classes 2
          num-feats 10
          W (get-node "W" [num-classes num-feats])
          b (get-node "bias" [num-classes])
          feat-vec (get-node "f" [num-feats])
          activations (+ (* W feat-vec) b)
          label (get-node "label" [1])
          loss (cross-entropy-loss activations label)]
      (is (flare/scalar-shape? (:shape loss))))))

(deftest concat-op-test
  (testing "concat op"
    (let [op (->ConcatOp 0)
          inputs [(get-node "x" [2 4]) (get-node "y" [3 4])]]
      (is (= [5 4] (forward-shape op inputs)))
      (op-validate! op inputs)
      (is (thrown? RuntimeException
                   (op-validate! op [(get-node "z1" [3 4])
                                     (get-node "z2" [3 3])]))))))

(deftest split-op-test
  (testing "split op"
    (let [op (->SplitOp 0 1 3)
          inputs [(get-node "x" [5])]]
      (is (= [2] (forward-shape op inputs)))
      (op-validate! op inputs)
      (is (thrown? RuntimeException
                   (op-validate! op [(get-node "z1" [3 4])
                                     (get-node "z2" [3 3])]))))
    (let [output (split (get-node "y" [10]) 0 3 6)]
      output)))

