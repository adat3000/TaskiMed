def normalize_features(features):
    """Normaliza features (edad, colesterol, presión) a escala 0-1"""
    age, cholesterol, pressure = features
    age_norm = age / 100
    cholesterol_norm = cholesterol / 300
    pressure_norm = pressure / 200
    return [age_norm, cholesterol_norm, pressure_norm]
