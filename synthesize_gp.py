import pandas as pd
import numpy as np
import random
import time
import os
import pickle
import traceback
import subprocess

import datautils
import GP.gp_utils as gp_utils

# We use 200 example cases for the training set (including all hand-chosen inputs) and 2000 for the unseen test set. We recommend a budget of 60 million program executions; 
# We allocate these to 200 training cases used to evaluate a population of 1000 individuals for 300
# generations in our experimental GP runs.


def loop_through_tasks(task_id_lists, base_save_folder, num_runs, training_set_size):
    for t, taskid in enumerate(task_id_lists):
        for r in range(num_runs):
            save_folder = f"{base_save_folder}/Size{training_set_size}/{taskid}/replicate_{r}"
            time.sleep(random.random()*5)
            if os.path.exists(save_folder):
                continue
            else:
                if not os.path.exists(save_folder):
                    os.makedirs(save_folder)

                print("working on ")
                print(save_folder)

                # Build the command based on training set size
                if training_set_size == 200:
                    namespace = f"clojush.problems.psb2size200.{taskid}"
                elif training_set_size == 50:
                    namespace = f"clojush.problems.psb2size50.{taskid}"
                else:
                    print(f"Unsupported training_set_size: {training_set_size}")
                    continue

                cmd = ['lein', 'run', namespace]

                # Run the command and save the output to a file
                save_file = f"{save_folder}/output.txt"
                with open(save_file, 'w') as f:
                    subprocess.run(cmd, stdout=f, stderr=subprocess.STDOUT, cwd='Clojush')
            
            return 

    print("all finished")


if __name__ == "__main__":
    task_id_lists = datautils.PSB2_DATASETS
    base_save_folder = "PushGP_Results"
    num_runs = 100

    loop_through_tasks(task_id_lists, base_save_folder, num_runs, 200)