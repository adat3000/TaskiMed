from pydantic import BaseModel
from typing import List

class MlInferenceRequest(BaseModel):
    features: List[int]  # [age, cholesterol, pressure]

class MlInferenceResponse(BaseModel):
    prediction: str
    score: float
    model_version: str
