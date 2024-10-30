import json 
import pandas as pd
import openai

openai.api_type = "azure"
openai.azure_base = os.environ.get('AZURE_OPENAI_ENDPOINT')
openai.api_version = "2024-02-01"
openai.api_key = os.environ.get('AZURE_OPENAI_API_KEY')
df = pd.read_csv('.csv')
context = df.to_json(orient="")

# Define the query
query = "What are the KPIs for Outbound?"

general_role_prompt = "You are an assistant that searches for relevant KPIs based on the user's query intent. Context has been provided to you. Check the intent from the query and provide the relevant KPIs from the context."

sample_interaction = """
User: What KPI should I look at to track Outbound?
Assistant: Based on your intent, the relevant KPI could be "Order Status" etc.
"""

conversation = [
    {"role": "system", "content": general_role_prompt},
    {"role": "assistant", "content": sample_interaction},
    {"role": "user", "content": "Context: " + context + "\n\n Query: " + query}
]

# Send the request to the OpenAI API
response = openai.chat.completions.create(
    model="gketron-4o",
    messages=conversation,
    temperature=0.7,
    top_p=0.95
)


# Print the response
print(response.choices[0].message.content)