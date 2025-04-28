import pandas as pd
import numpy as np
import os
import pickle

from pyshgp.gp.estimators import PushEstimator
from pyshgp.gp.genome import GeneSpawner



import datautils
import GP.gp_utils as gp_utils

# We use 200 example cases for the training set (including all hand-chosen inputs) and 2000 for the unseen test set. We recommend a budget of 60 million program executions; 
# We allocate these to 200 training cases used to evaluate a population of 1000 individuals for 300
# generations in our experimental GP runs.


def generate_datasets(task_id_lists, base_save_folder, data_dir, num_runs):
    '''
    Generate the datasets for the given tasks and save them in the base_save_folder. 
    Serves as a preprocessing step since complete data files can be large to be transferred to HPC
    '''
    for t, taskid in enumerate(task_id_lists):
        save_folder = f"{base_save_folder}/{taskid}"
        if not os.path.exists(save_folder):
                os.makedirs(save_folder)
        for r in range(num_runs):
            print("working on ")
            print(save_folder)

            print("loading data")
                
            # Split the data into training and testing sets
            X_train, y_train, X_val, y_val, X_test, y_test = datautils.generate_training_test_data(data_dir, taskid, r) 

            data = {}
            data["X_train"] = X_train
            data["y_train"] = y_train  
            data["X_test"] = X_test
            data["y_test"] = y_test

            with open(f"{save_folder}/data_{r}.pkl", "wb") as f:
                pickle.dump(data, f)                   
                        
    print("all finished")


if __name__ == "__main__":
    task_id_lists = datautils.PSB2_DATASETS
    base_save_folder = "preprocessed_datasets"
    data_dir = "../Datasets/PSB2/datasets/"
    num_runs = 100
        
    generate_datasets(task_id_lists, base_save_folder, data_dir, num_runs)