import os
from fastapi import FastAPI
from inference.api.routes import router as ml_router

app = FastAPI(title="ML Inference Service", version="demo-1.0")

# Montar rutas ML
app.include_router(ml_router, prefix="/ml/inference")

@app.get("/ml/inference/health")
def health():
    return {
        "status": "OK",
        "environment": os.getenv("ENV", "dev"),
        "model_version": os.getenv("MODEL_VERSION", "demo-1.0")
    }

# --------------------------------------------------
# SOLO PARA DEBUG LOCAL
# --------------------------------------------------

if __name__ == "__main__":
    import uvicorn
    uvicorn.run(
        "app:app",
        host="127.0.0.1",
        port=9001,
        reload=True
    )
