from pathlib import Path

BASE_DIR = Path(__file__).parent
DATA_DIR = BASE_DIR / "data"
RAW_DATA = DATA_DIR / "raw"
PROCESSED_DATA = DATA_DIR / "processed"
MODEL_DIR = BASE_DIR / "models"
MODEL_VERSION = "demo-1.0"
