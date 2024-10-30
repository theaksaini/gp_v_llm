import logging
import os
import requests
import pandas as pd
import json
from llmutils import verify_response, sanitize_input

# Configure logging
logging.basicConfig(
    level=logging.INFO,
    format='%(asctime)s - %(levelname)s - %(message)s'
)

class AzureAPIHandler:
    def __init__(self, api_key=None, base_url=None):
        self.api_key = api_key or os.environ.get('AZURE_OPENAI_API_KEY')
        self.base_url = base_url or os.environ.get('AZURE_OPENAI_ENDPOINT')
        self.api_version
        self.headers = {
            "Authorization": f"Bearer {self.api_key}",
            "Content-Type": "application/json"
        }

    def submit_question(self, question, model="text-davinci-003"):
        # Sanitize the input and log the action
        question = sanitize_input(question)
        logging.info("Submitting question to OpenAI API: %s", question)

        endpoint = f"{self.base_url}/completions"
        data = {
            "model": model,
            "prompt": question,
            "max_tokens": 100  # Adjust as necessary
        }
        response = requests.post(endpoint, headers=self.headers, json=data)
        response_json = response.json()

        if verify_response(response_json):
            self.save_response(question, response_json)
            logging.info("Response saved successfully.")
        else:
            logging.warning(
                "Response from '%s' did not contain valid executable code.", 
                question
            )

        return response_json

    def submit_training_data(self, csv_path):
        # Read CSV data
        df = pd.read_csv(csv_path)
        for _, row in df.iterrows():
            prompt, completion = row['prompt'], row['completion']
            logging.info(
                "Processing training data: Prompt: %s -> Completion: %s",
                prompt, completion
            )
            # Here, you might log, process, or visualize for fine-tuning contexts
        return "Processing of training data completed."

    def save_response(self, question, response_json):
        try:
            with open("responses.json", "a") as file:
                entry = {
                    "question": question,
                    "response": response_json
                }
                file.write(json.dumps(entry) + "\n")
            logging.info("Response logged to file.")
        except Exception as err:
            logging.error("Failed to save response: %s", err)