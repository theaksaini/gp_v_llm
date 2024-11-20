import re
import pandas as pd

def sanitize_input(input_text):
    sanitized_text = re.sub(r'[^\w\s]', '', input_text)
    return sanitized_text.strip()

def verify_response(response):
    try:
        script = response[response.find('```python')+len('```python'):]
        re = script.split('```')
        if "def " in script and "my_func" in script:
            return True, re[0]
    except (IndexError, KeyError):
        pass
    return False, ''
