from fastapi import APIRouter
from inference.api.schemas import MlInferenceRequest, MlInferenceResponse
from inference.services.inference_service import predict_risk

router = APIRouter()

@router.post("/predict", response_model=MlInferenceResponse)
def predict(request: MlInferenceRequest):
    return predict_risk(request)
