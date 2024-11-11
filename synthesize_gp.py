import pandas as pd
import numpy as np
import random
import time
import sys
import os
import pickle
import traceback

from pyshgp.gp.estimators import PushEstimator
from pyshgp.gp.genome import GeneSpawner



import datautils
import GP.gp_utils as gp_utils

# We use 200 example cases for the training set (including all hand-chosen inputs) and 2000 for the unseen test set. We recommend a budget of 60 million program executions; 
# We allocate these to 200 training cases used to evaluate a population of 1000 individuals for 300
# generations in our experimental GP runs.


def loop_through_tasks(task_id_lists, base_save_folder, data_dir, metadata_file, num_runs, ga_params):
    for t, taskid in enumerate(task_id_lists):
        for r in range(num_runs):
            save_folder = f"{base_save_folder}/{taskid}_{r}"
            time.sleep(random.random()*5)
            if not os.path.exists(save_folder):
                os.makedirs(save_folder)
            else:
                continue

            print("working on ")
            print(save_folder)

            print("loading data")
                
            # Split the data into training and testing sets
            X_train, y_train, X_test, y_test = datautils.generate_training_test_data(data_dir, taskid, r)                    
            try:
                scores = pd.DataFrame(columns = ['taskid','run','runtime','train_error','test_error','train_unsolved','test_unsolved'])  
                print("Starting the Evolution: ")

                metadata = datautils.get_problem_metadata(metadata_file, taskid)
                spawner = GeneSpawner(**metadata)

                est = PushEstimator(
                    search="GA",
                    population_size=ga_params["population_size"],
                    max_generations=ga_params["max_generations"],
                    spawner=spawner,
                    simplification_steps=10,
                    last_str_from_stdout=True,
                    parallelism=True,
                    verbose=3
                )

                start = time.time()
                est.fit(X=X_train, y=y_train)
                end = time.time()
                
                this_score = {}
                this_score["runtime"] = end - start
                
                assert len(est.solution.error_vector) == len(y_train)
                this_score["train_total_error"] = np.sum(est.solution.error_vector)

                # Record the number of unsolved cases in the training set
                this_score["train_unsolved"] = np.sum(est.solution.error_vector > 0)

                assert len(est.score(X_test, y_test)) == len(y_test)
                this_score["test_total_error"] = np.sum(est.score(X_test, y_test))

                # Record the number of unsolved cases in the training set
                this_score["test_unsolved"] = np.sum(est.score(X_test, y_test) > 0)

                this_score["taskid"] = taskid
                this_score["run"] = r

                scores.loc[len(scores.index)] = this_score

                with open(f"{save_folder}/scores.pkl", "wb") as f:
                    pickle.dump(scores, f)

                return
            
            except Exception as e:
                trace =  traceback.format_exc()
                pipeline_failure_dict = {"taskid": taskid,  "error": str(e), "trace": trace}
                print("failed on ")
                print(save_folder)
                print(e)
                print(trace)

                with open(f"{save_folder}/failed.pkl", "wb") as f:
                    pickle.dump(pipeline_failure_dict, f)

                return
        
    print("all finished")




if __name__ == "__main__":
    task_id_lists = datautils.PSB2_DATASETS
    base_save_folder = "results"
    data_dir = "../Datasets/PSB2/datasets/"
    metadata_file = "PSB2_metadata.csv"
    num_runs_local = 5
    ga_params_local = {
        "population_size": 10,
        "max_generations": 50
    }
    num_runs_hpc = 100
    ga_params_hpc = {
        "population_size": 1000,
        "max_generations": 300
    }


    loop_through_tasks(task_id_lists, base_save_folder, data_dir, metadata_file, num_runs_local, ga_params_local)