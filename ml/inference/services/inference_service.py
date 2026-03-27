import pickle
from pathlib import Path
from inference.api.schemas import MlInferenceRequest, MlInferenceResponse
from inference.services.utils import normalize_features

MODEL_PATH = Path(__file__).parent.parent / "models/ml_model.pkl"
MODEL_VERSION = "demo-1.0"

# Cargar modelo dummy (o real)
if MODEL_PATH.exists():
    with open(MODEL_PATH, "rb") as f:
        model = pickle.load(f)
else:
    model = None

# Función ficticia para demo
def simple_risk_model(age: int, cholesterol: int, pressure: int):
    score = 0.0
    if age >= 60: score += 0.3
    elif age >= 45: score += 0.2
    if cholesterol >= 240: score += 0.4
    elif cholesterol >= 200: score += 0.25
    if pressure >= 140: score += 0.3
    elif pressure >= 130: score += 0.2
    score = min(score, 1.0)
    if score >= 0.7:
        prediction = "RIESGO_ALTO"
    elif score >= 0.4:
        prediction = "RIESGO_MEDIO"
    else:
        prediction = "RIESGO_BAJO"
    return prediction, round(score, 2)


def predict_risk(request: MlInferenceRequest) -> MlInferenceResponse:
    features = normalize_features(request.features)
    
    if model:
        age, cholesterol, pressure = features
        age, cholesterol, pressure = request.features
        prediction, score = simple_risk_model(age, cholesterol, pressure)
        # Dummy predict: simple regla
        #score = min(max((features[0] + features[1] + features[2]) / 500, 0), 1)
        #prediction = "HIGH_RISK" if score > 0.7 else "LOW_RISK"
    else:
        # fallback
        prediction = "INDETERMINADO"
        score = 0.0
    
    return MlInferenceResponse(
        prediction=prediction,
        score=score,
        model_version=MODEL_VERSION
    )
