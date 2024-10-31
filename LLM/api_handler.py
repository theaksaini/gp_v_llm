import logging
import os
import pandas as pd
import json
from llmutils import verify_response, sanitize_input
import openai

# Configure logging
logging.basicConfig(
    level=logging.INFO,
    format='%(asctime)s - %(levelname)s - %(message)s'
)

class AzureAPIHandler:
    def __init__(self, api_key=None, base_url=None, 
                api_version=None, deployment_name=None):
        self.api_key = os.environ.get('AZURE_OPENAI_API_KEY')
        self.base_url = os.environ.get('AZURE_OPENAI_ENDPOINT')
        self.api_version = "2024-02-01"
        self.model = "gketron-4o"
        self.client = openai.AzureOpenAI(
            azure_endpoint=os.environ.get('AZURE_OPENAI_ENDPOINT'),
            api_key=os.environ.get('AZURE_OPENAI_API_KEY'),
            api_version="2024-02-01"
        )

    def submit_question(self, question, csv_path, iteration = 42):
        # Sanitize the input and log the action
        question = sanitize_input(question)
        logging.info("Submitting question to OpenAI API: %s", question)

        # Read CSV data
        df = pd.read_csv(csv_path)
        context = df.to_json(orient="index")
        logging.info("Submitting csv to OpenAI API: %s", df)
        # Here, you might log, process, or visualize for fine-tuning contexts

        query = question
        general_role_prompt = "You are an assistant that generates Python functions to create correct outputs based on a series of examples inputs proved to you in the Context and instructions given by the Query. Each row contains a unique example containing string inputs, and a string output for you to learn and write a function from. Write the function in the following format: def my_func(input1: str, input2:str): \n    #To be completed by you.\n    return output"

        sample_interaction = """
        User: Context: {"row 1": {"input1": "a", "input2": "b", "output": "c"}, "row 2": {"input1": "c", "input2": "d", "output": "e"}, ...}"\n\n Query: Given two strings, input1 and input2, return the next character after input2 in the alphabet as a string."
        Assistant: def my_func(input1: str, input2:str): \n    if input2 == "z":\n    output = chr(ord(input2)-25))\n\n    else:\n    output = chr(ord(input2)+1)\n    return output
        """

        conversation = [
            {"role": "system", "content": general_role_prompt},
            #{"role": "assistant", "content": sample_interaction},
            {"role": "user", "content": "Context: " + context + "\n\n Query: " + query}
        ]

        # Send the request to the OpenAI API
        response = self.client.chat.completions.create(
            model=self.model,
            messages=conversation,
            seed = iteration, #allows for reporducable variations with 100 replicates.
            temperature=0.7,
            top_p=0.95
        )

        # Print the response
        print(response.model_dump_json(indent=2))
        print(response.choices[0].message.content)
        response_json = response.choices[0].message.content

        if verify_response(response):
            self.save_response(question, context, response)
            logging.info("Response saved successfully.")
        else:
            logging.warning(
                "Response from '%s' did not contain valid executable code.", 
                question
            )
        return response_json

    def save_response(self, question, context, response_json):
        try:
            with open("responses.json", "a") as file:
                entry = {
                    "question": question,
                    "context": context,
                    "response": response_json
                }
                file.write(json.dumps(entry) + "\n")
            logging.info("Response logged to file.")
        except Exception as err:
            logging.error("Failed to save response: %s", err)