import re
import pandas as pd

def sanitize_input(input_text):
    sanitized_text = re.sub(r'[^\w\s]', '', input_text)
    return sanitized_text.strip()

def verify_response(response_json):
    try:
        script = response_json.get("choices", [{}])[0].get("text", "").strip()
        if "def " in script and "my_func" in script:
            return script
    except (IndexError, KeyError):
        pass
    return None
