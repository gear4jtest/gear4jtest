package com.myorg.services;

public class ModelsService {
    public String getModel(String modelId) {
        return "Modèle avec ID: " + modelId;
    }

    public String createModel(String modelData) {
        return "Nouveau modèle créé avec les données: " + modelData;
    }
}
