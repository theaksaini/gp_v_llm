import pandas as pd
import numpy as np
import random
import time

from pyshgp.gp.estimators import PushEstimator
from pyshgp.gp.genome import GeneSpawner
from pyshgp.push.instruction_set import InstructionSet

import utils

# Our recommendation is to use 200 example cases for the training set (including all hand-chosen
# inputs) and 2000 for the unseen test set. We recommend a budget of 60 million program executions; 
# we allocate these to 200 training cases used to evaluate a population of 1000 individuals for 300
# generations in our experimental GP runs.


def random_int():
    """Return a random integer."""
    return random.randint(-100, 100)


spawner = GeneSpawner(
    n_inputs=1,
    instruction_set=InstructionSet().register_core_by_stack({"exec", "int", "bool"}),
    literals=[],
    erc_generators=[
        random_int,
    ],
)


if __name__ == "__main__":
    X_train, y_train, X_test, y_test = utils.generate_training_test_data("../Datasets/PSB2/datasets", "gcd", 42)

    est = PushEstimator(
        search="GA",
        population_size=50,
        max_generations=15,
        spawner=spawner,
        simplification_steps=10,
        last_str_from_stdout=True,
        parallelism=True,
        verbose=2
    )

    start = time.time()
    est.fit(X=X_train, y=y_train)
    end = time.time()
    print("========================================")
    print("post-evolution stats")
    print("========================================")
    print("Runtime: ", end - start)
    print("Test Error: ", np.sum(est.score(X_test, y_test)))