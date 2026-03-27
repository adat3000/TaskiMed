import pickle
from pathlib import Path

MODEL_PATH = Path(__file__).parent.parent / "models/ml_model.pkl"

def train_dummy_model():
    """Entrena un modelo dummy simple y lo guarda en models/ml_model.pkl"""
    # Dummy: puede ser reemplazado por sklearn o PyTorch
    dummy_model = {"type": "dummy", "description": "modelo de ejemplo"}
    
    MODEL_PATH.parent.mkdir(parents=True, exist_ok=True)
    with open(MODEL_PATH, "wb") as f:
        pickle.dump(dummy_model, f)
    print(f"✅ Modelo guardado en {MODEL_PATH}")
