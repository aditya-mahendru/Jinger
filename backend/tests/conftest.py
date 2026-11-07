import os
import tempfile

os.environ["JINGER_DB"] = os.path.join(tempfile.mkdtemp(), "test.db")
os.environ["LLM_API_KEY"] = ""
