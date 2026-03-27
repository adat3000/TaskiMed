package com.taskimed.service;

import com.taskimed.dto.PatientDTO;
import com.taskimed.entity.Patient;

import java.util.List;
import java.util.Map;

import org.springframework.data.domain.Page;

public interface PatientService {
    PatientDTO savePatient(PatientDTO dto);
    List<Patient> getPatients();
    PatientDTO getPatientById(Long id);
    void deletePatient(Long id);
    Page<PatientDTO> getPage(int pageNumber, int pageSize, String filtro, String sortField, String sortDir, Map<String, String> customFilters);
    
    PatientDTO convertToDTO(Patient patient);
}